package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.application.port.in.GetPerformanceUseCase;
import fr.patrimoine.application.port.in.GetPortfolioOverviewUseCase;
import fr.patrimoine.application.port.in.PerformanceView;
import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Position;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.valuation.PortfolioValuation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The demo seed is hand-written SQL, so it bypasses every rule the aggregate enforces. This test
 * puts the rules back: it replays each seeded ledger through the domain and requires the stored
 * balances, payment counters and positions to be exactly what the replay produces. A seed edit that
 * miscomputes one weighted average cost, or deposits past a ceiling, fails here rather than showing
 * a subtly wrong figure in the demo.
 *
 * <p>It also boots the whole application, so it is the test that proves every port has an adapter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("demo")
// Prices come from the seed: a test must not depend on a website being up.
@TestPropertySource(properties = "patrimoine.quotes.live-prices=false")
@Import(PostgresContainerConfiguration.class)
@DisplayName("Demo seed")
class DemoSeedIT {

    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private GetPortfolioOverviewUseCase overview;
    @Autowired private GetPerformanceUseCase performance;

    @Test
    @DisplayName("every seeded balance and position is what replaying its ledger produces")
    void seedObeysTheDomainRules() {
        List<Account> seeded = accounts.findAll();
        assertThat(seeded).hasSize(7);

        for (Account stored : seeded) {
            Account replayed = replay(stored, transactions.findByAccount(stored.id()));

            assertThat(replayed.cashBalance())
                    .as("%s: cash", stored.label())
                    .isEqualTo(stored.cashBalance());
            assertThat(replayed.netPayments())
                    .as("%s: net payments", stored.label())
                    .isEqualTo(stored.netPayments());
            assertThat(replayed.grossPayments())
                    .as("%s: gross payments", stored.label())
                    .isEqualTo(stored.grossPayments());
            assertThat(replayed.positions())
                    .as("%s: positions", stored.label())
                    .containsExactlyInAnyOrderElementsOf(stored.positions());
        }
    }

    @Test
    @DisplayName("the application values the whole demo portfolio without a single warning")
    void valuesTheDemoPortfolioCleanly() {
        PortfolioValuation valuation = overview.overview();

        assertThat(valuation.warnings()).isEmpty();
        assertThat(valuation.byAccount()).hasSize(7);
        assertThat(valuation.total().isPositive()).isTrue();
    }

    @Test
    @DisplayName("the portfolio has a year of month-end history and an annualised return")
    void drawsAPerformanceCurve() {
        LocalDate today = LocalDate.now();

        PerformanceView view = performance.portfolioPerformance(today.minusYears(1), today);

        assertThat(view.series()).hasSizeGreaterThanOrEqualTo(12);
        assertThat(view.report().annualisedReturn()).isPresent();
    }

    /**
     * Rebuilds an account from nothing, one movement at a time, through the aggregate's own rules.
     */
    private static Account replay(Account stored, List<Transaction> ledger) {
        Account account =
                Account.open(
                        stored.id(),
                        stored.label(),
                        stored.type(),
                        stored.currency(),
                        stored.openedOn());
        for (Transaction movement : ledger) {
            switch (movement.type()) {
                case DEPOSIT -> account.deposit(movement.amount());
                case WITHDRAWAL -> account.withdraw(movement.amount().negated());
                case DIVIDEND, INTEREST -> account.credit(movement.amount());
                case FEE, TAX -> account.debit(movement.amount().negated());
                // The ledger stores a trade's all-in cash effect; the fees are whatever the units
                // at their price do not explain.
                case BUY ->
                        account.buy(
                                movement.instrument(),
                                kindOf(stored, movement.instrument()),
                                movement.quantity(),
                                movement.unitPrice(),
                                movement.amount()
                                        .negated()
                                        .minus(movement.unitPrice().times(movement.quantity())));
                case SELL ->
                        account.sell(
                                movement.instrument(),
                                movement.quantity(),
                                movement.unitPrice(),
                                movement.unitPrice()
                                        .times(movement.quantity())
                                        .minus(movement.amount()));
            }
        }
        return account;
    }

    /**
     * A movement does not record the instrument's kind; every instrument the seed buys is still
     * held.
     */
    private static InstrumentKind kindOf(Account stored, InstrumentId instrument) {
        return stored.position(instrument)
                .map(Position::kind)
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Seed buys %s but no longer holds it; cannot tell its kind"
                                                .formatted(instrument)));
    }
}
