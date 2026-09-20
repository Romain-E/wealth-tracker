package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Position;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PersistenceSlice
@DisplayName("JpaAccountRepository")
class JpaAccountRepositoryIT {

    private static final InstrumentId WORLD = InstrumentId.of("LU1681043599");
    private static final InstrumentId AIR_LIQUIDE = InstrumentId.of("FR0000120073");

    @Autowired private JpaAccountRepository accounts;
    @Autowired private TestEntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcClient jdbc;

    private static Account pea(LocalDate openedOn) {
        return Account.open(AccountId.newId(), "PEA", AccountType.PEA, Money.EUR, openedOn);
    }

    /** Forces real SQL both ways, instead of reading back the instance still held in memory. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("round-trips the aggregate and its positions without losing a decimal")
    void roundTripsTheAggregate() {
        Account pea = pea(LocalDate.of(2019, 5, 2));
        pea.deposit(Money.euros("10000.00"));
        // 1 501.00 spread over 3 units: an average cost that only survives at eight decimals.
        pea.buy(
                WORLD,
                InstrumentKind.ETF,
                Quantity.of(3),
                Price.euros("500.00"),
                Money.euros("1.00"));
        pea.buy(
                AIR_LIQUIDE,
                InstrumentKind.EQUITY,
                Quantity.of(10),
                Price.euros("171.37"),
                Money.euros("2.50"));
        accounts.save(pea);
        flushAndClear();

        Account loaded = accounts.findById(pea.id()).orElseThrow();

        assertThat(loaded.position(WORLD))
                .map(Position::averageCost)
                .hasValue(Price.euros("500.33333333"));
        assertThat(loaded.positions()).containsExactlyInAnyOrderElementsOf(pea.positions());
        assertThat(loaded.cashBalance()).isEqualTo(pea.cashBalance());
        assertThat(loaded.netPayments()).isEqualTo(pea.netPayments());
        assertThat(loaded.grossPayments()).isEqualTo(pea.grossPayments());
        assertThat(loaded.openedOn()).isEqualTo(pea.openedOn());
    }

    @Test
    @DisplayName("a trade updates the position it touches, and a full sale removes its row")
    void synchronisesPositions() {
        Account pea = pea(LocalDate.of(2019, 5, 2));
        pea.deposit(Money.euros("10000.00"));
        pea.buy(WORLD, InstrumentKind.ETF, Quantity.of(3), Price.euros("500.00"), Money.ZERO_EUR);
        pea.buy(
                AIR_LIQUIDE,
                InstrumentKind.EQUITY,
                Quantity.of(10),
                Price.euros("170.00"),
                Money.ZERO_EUR);
        accounts.save(pea);
        flushAndClear();

        Account loaded = accounts.findById(pea.id()).orElseThrow();
        loaded.sell(AIR_LIQUIDE, Quantity.of(10), Price.euros("180.00"), Money.ZERO_EUR);
        loaded.buy(
                WORLD, InstrumentKind.ETF, Quantity.of(1), Price.euros("540.00"), Money.ZERO_EUR);
        accounts.save(loaded);
        flushAndClear();

        Account reloaded = accounts.findById(pea.id()).orElseThrow();
        assertThat(reloaded.positions()).containsExactly(loaded.position(WORLD).orElseThrow());
        assertThat(reloaded.cashBalance()).isEqualTo(Money.euros("8060.00"));
    }

    @Test
    @DisplayName("lists every account oldest first, positions included")
    void listsAccountsInOpeningOrder() {
        Account recent = pea(LocalDate.of(2024, 1, 10));
        recent.deposit(Money.euros("1000.00"));
        recent.buy(
                WORLD, InstrumentKind.ETF, Quantity.of(1), Price.euros("500.00"), Money.ZERO_EUR);
        Account older =
                Account.open(
                        AccountId.newId(),
                        "Livret A",
                        AccountType.LIVRET_A,
                        Money.EUR,
                        LocalDate.of(2015, 3, 1));
        accounts.save(recent);
        accounts.save(older);
        flushAndClear();

        List<Account> all = accounts.findAll();

        assertThat(all).extracting(Account::id).containsExactly(older.id(), recent.id());
        assertThat(all.get(1).positions()).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName(
            "refuses to save outside a transaction, where the version check would prove nothing")
    void refusesToSaveOutsideATransaction() {
        Account pea = pea(LocalDate.of(2019, 5, 2));

        assertThatExceptionOfType(IllegalTransactionStateException.class)
                .isThrownBy(() -> accounts.save(pea));
    }

    /**
     * The race the version column exists for, played out with two real transactions: each deposit
     * fits under the Livret A ceiling on its own, together they do not.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("two concurrent deposits cannot both pass the Livret A ceiling check")
    void rejectsALostUpdateOnACappedEnvelope() {
        TransactionTemplate request = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrentRequest = new TransactionTemplate(transactionManager);
        concurrentRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Account livretA =
                Account.open(
                        AccountId.newId(),
                        "Livret A",
                        AccountType.LIVRET_A,
                        Money.EUR,
                        LocalDate.of(2020, 1, 1));
        request.executeWithoutResult(status -> accounts.save(livretA));

        try {
            assertThatExceptionOfType(OptimisticLockingFailureException.class)
                    .isThrownBy(
                            () ->
                                    request.executeWithoutResult(
                                            status -> {
                                                // Reads the account: 22 950 EUR of room left.
                                                Account mine =
                                                        accounts.findById(livretA.id())
                                                                .orElseThrow();

                                                // Meanwhile, another request reads the same
                                                // state, deposits and commits first.
                                                concurrentRequest.executeWithoutResult(
                                                        inner -> {
                                                            Account theirs =
                                                                    accounts.findById(livretA.id())
                                                                            .orElseThrow();
                                                            theirs.deposit(Money.euros("15000.00"));
                                                            accounts.save(theirs);
                                                        });

                                                // Legal against what this request read, illegal
                                                // against what is now true.
                                                mine.deposit(Money.euros("15000.00"));
                                                accounts.save(mine);
                                            }));

            Account stored =
                    request.execute(status -> accounts.findById(livretA.id()).orElseThrow());
            assertThat(stored.netPayments()).isEqualTo(Money.euros("15000.00"));
        } finally {
            jdbc.sql("DELETE FROM account WHERE id = :id")
                    .param("id", livretA.id().value())
                    .update();
        }
    }
}
