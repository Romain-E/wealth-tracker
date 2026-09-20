package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PersistenceSlice
@DisplayName("JpaTransactionRepository")
class JpaTransactionRepositoryIT {

    private static final InstrumentId WORLD = InstrumentId.of("LU1681043599");

    @Autowired private JpaAccountRepository accounts;
    @Autowired private JpaTransactionRepository transactions;
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcClient jdbc;

    private AccountId persistedAccount() {
        Account account =
                Account.open(
                        AccountId.newId(),
                        "Livret A",
                        AccountType.LIVRET_A,
                        Money.EUR,
                        LocalDate.of(2020, 1, 1));
        accounts.save(account);
        return account.id();
    }

    private static Transaction deposit(AccountId account, String date, String amount) {
        return Transaction.cashMovement(
                account, TransactionType.DEPOSIT, LocalDate.parse(date), Money.euros(amount));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("round-trips cash movements and trades, same-day movements in insertion order")
    void roundTripsMovements() {
        AccountId account = persistedAccount();
        Transaction deposit = deposit(account, "2026-03-02", "1000.00");
        Transaction purchase =
                Transaction.trade(
                        account,
                        TransactionType.BUY,
                        LocalDate.parse("2026-03-02"),
                        Money.euros("-280.50"),
                        WORLD,
                        Quantity.of("0.5"),
                        Price.euros("559.12345678"));
        transactions.save(deposit);
        transactions.save(purchase);
        flushAndClear();

        assertThat(transactions.findByAccount(account)).containsExactly(deposit, purchase);
    }

    @Test
    @DisplayName("loads several accounts' movements in one call, grouped by account, from a date")
    void loadsMovementsSinceADate() {
        AccountId livretA = persistedAccount();
        AccountId ldds = persistedAccount();
        AccountId notAsked = persistedAccount();
        transactions.save(deposit(livretA, "2025-12-15", "100.00"));
        Transaction onTheDay = transactions.save(deposit(livretA, "2026-01-01", "200.00"));
        Transaction later = transactions.save(deposit(ldds, "2026-03-01", "300.00"));
        transactions.save(deposit(notAsked, "2026-02-01", "400.00"));
        flushAndClear();

        Map<AccountId, List<Transaction>> movements =
                transactions.findByAccountsSince(List.of(livretA, ldds), LocalDate.of(2026, 1, 1));

        assertThat(movements).containsOnlyKeys(livretA, ldds);
        assertThat(movements.get(livretA)).containsExactly(onTheDay);
        assertThat(movements.get(ldds)).containsExactly(later);
    }

    @Test
    @DisplayName("asks the database nothing when given no accounts")
    void returnsNothingForNoAccounts() {
        assertThat(transactions.findByAccounts(List.of())).isEmpty();
        assertThat(transactions.findByAccountsSince(List.of(), LocalDate.of(2026, 1, 1))).isEmpty();
    }

    @Test
    @DisplayName("the schema itself refuses a withdrawal stored with the wrong sign")
    void schemaRejectsAWronglySignedAmount() {
        AccountId account = persistedAccount();
        entityManager.flush();

        // Straight SQL, the way a seed or a manual fix would write it: no aggregate to object.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO account_transaction
                                                    (id, account_id, type, trade_date, amount, currency)
                                                VALUES (gen_random_uuid(), :accountId, 'WITHDRAWAL',
                                                        CURRENT_DATE, 500.00, 'EUR')
                                                """)
                                        .param("accountId", account.value())
                                        .update());
    }
}
