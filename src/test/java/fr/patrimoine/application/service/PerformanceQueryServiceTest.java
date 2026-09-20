package fr.patrimoine.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.application.port.in.PerformanceView;
import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.application.port.out.QuoteRepository;
import fr.patrimoine.application.port.out.RegulatedRateProvider;
import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.application.port.out.ValuationRepository;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.TransactionType;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationSource;
import fr.patrimoine.domain.performance.PerformancePoint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerformanceQueryService")
class PerformanceQueryServiceTest {

    private static final Instant FIXED = Instant.parse("2025-01-01T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2025, 1, 1);
    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final InstrumentId WORLD = InstrumentId.of("IE00B4L5Y983");

    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private ValuationRepository valuations;
    @Mock private QuoteRepository quotes;
    @Mock private RegulatedRateProvider rates;

    private PerformanceQueryService service;

    @BeforeEach
    void setUp() {
        service =
                new PerformanceQueryService(
                        accounts,
                        transactions,
                        valuations,
                        new ValuationContextAssembler(quotes, valuations, transactions, rates),
                        Clock.fixed(FIXED, ZoneId.of("Europe/Paris")));
    }

    private static Account fundedPea() {
        Account pea =
                Account.open(
                        AccountId.newId(),
                        "PEA",
                        AccountType.PEA,
                        Money.EUR,
                        LocalDate.of(2019, 5, 2));
        pea.deposit(Money.euros("15000.00"));
        pea.buy(WORLD, InstrumentKind.ETF, Quantity.of(100), Price.euros("100.00"), Money.ZERO_EUR);
        return pea;
    }

    private static Transaction deposit(AccountId id, String date, String amount) {
        return Transaction.cashMovement(
                id, TransactionType.DEPOSIT, LocalDate.parse(date), Money.euros(amount));
    }

    @Test
    @DisplayName("measures today's computed value against what was actually paid in")
    void reportsPortfolioPerformance() {
        Account pea = fundedPea();
        when(accounts.findAll()).thenReturn(List.of(pea));
        when(rates.currentRates()).thenReturn(Map.of());
        when(quotes.findLatest(anyMap()))
                .thenReturn(Map.of(WORLD, Quote.fresh(WORLD, Price.euros("120.00"), FIXED)));
        when(transactions.findByAccounts(anyCollection()))
                .thenReturn(
                        Map.of(
                                pea.id(),
                                List.of(
                                        deposit(pea.id(), "2020-01-01", "10000.00"),
                                        deposit(pea.id(), "2021-01-01", "5000.00"))));
        when(valuations.findAllBetween(FROM, TODAY)).thenReturn(List.of());

        PerformanceView view = service.portfolioPerformance(FROM, TODAY);

        // 5 000 cash + 100 units at 120.00 = 17 000, against 15 000 paid in.
        assertThat(view.report().currentValue()).isEqualTo(Money.euros("17000.00"));
        assertThat(view.report().netInvested()).isEqualTo(Money.euros("15000.00"));
        assertThat(view.report().netGain()).isEqualTo(Money.euros("2000.00"));
        assertThat(view.report().annualisedReturn()).isPresent();
    }

    @Test
    @DisplayName("the current value is computed from live prices, not read from the last snapshot")
    void doesNotUseStoredSnapshotsForTheCurrentValue() {
        Account pea = fundedPea();
        when(accounts.findAll()).thenReturn(List.of(pea));
        when(rates.currentRates()).thenReturn(Map.of());
        when(quotes.findLatest(anyMap()))
                .thenReturn(Map.of(WORLD, Quote.fresh(WORLD, Price.euros("120.00"), FIXED)));
        when(transactions.findByAccounts(anyCollection())).thenReturn(Map.of());
        // A deliberately wrong, stale snapshot: if it leaked into the headline figure the
        // number next to it on screen would disagree with the portfolio total.
        when(valuations.findAllBetween(FROM, TODAY))
                .thenReturn(
                        List.of(
                                new Valuation(
                                        pea.id(),
                                        LocalDate.of(2024, 6, 1),
                                        Money.euros("999999.00"),
                                        ValuationSource.COMPUTED)));

        PerformanceView view = service.portfolioPerformance(FROM, TODAY);

        assertThat(view.report().currentValue()).isEqualTo(Money.euros("17000.00"));
        // The snapshot is still used for the curve, which is the one thing it is good for.
        assertThat(view.series())
                .containsExactly(
                        new PerformancePoint(LocalDate.of(2024, 6, 1), Money.euros("999999.00")));
    }

    @Test
    @DisplayName("consolidates sparse per-account snapshots by carrying each forward")
    void consolidatesTheSeriesAcrossAccounts() {
        Account pea = fundedPea();
        AccountId flat = AccountId.newId();
        when(accounts.findAll()).thenReturn(List.of(pea));
        when(rates.currentRates()).thenReturn(Map.of());
        when(quotes.findLatest(anyMap())).thenReturn(Map.of());
        when(transactions.findByAccounts(anyCollection())).thenReturn(Map.of());
        when(valuations.findAllBetween(FROM, TODAY))
                .thenReturn(
                        List.of(
                                new Valuation(
                                        pea.id(),
                                        LocalDate.of(2024, 1, 1),
                                        Money.euros("10000.00"),
                                        ValuationSource.COMPUTED),
                                new Valuation(
                                        flat,
                                        LocalDate.of(2024, 6, 1),
                                        Money.euros("250000.00"),
                                        ValuationSource.MANUAL),
                                new Valuation(
                                        pea.id(),
                                        LocalDate.of(2024, 12, 1),
                                        Money.euros("12000.00"),
                                        ValuationSource.COMPUTED)));

        PerformanceView view = service.portfolioPerformance(FROM, TODAY);

        // The flat has no January row, so January is the PEA alone; from June the flat's value
        // is carried forward rather than vanishing on days it was not re-appraised.
        assertThat(view.series())
                .containsExactly(
                        new PerformancePoint(LocalDate.of(2024, 1, 1), Money.euros("10000.00")),
                        new PerformancePoint(LocalDate.of(2024, 6, 1), Money.euros("260000.00")),
                        new PerformancePoint(LocalDate.of(2024, 12, 1), Money.euros("262000.00")));
    }

    @Test
    @DisplayName("an account last appraised before the window is still in the curve from day one")
    void seedsTheSeriesWithSnapshotsOlderThanTheWindow() {
        Account pea = fundedPea();
        AccountId flat = AccountId.newId();
        when(accounts.findAll()).thenReturn(List.of(pea));
        when(rates.currentRates()).thenReturn(Map.of());
        when(quotes.findLatest(anyMap())).thenReturn(Map.of());
        when(transactions.findByAccounts(anyCollection())).thenReturn(Map.of());
        when(valuations.latestByAccountBefore(FROM))
                .thenReturn(
                        Map.of(
                                flat,
                                new Valuation(
                                        flat,
                                        LocalDate.of(2023, 6, 1),
                                        Money.euros("250000.00"),
                                        ValuationSource.MANUAL)));
        when(valuations.findAllBetween(FROM, TODAY))
                .thenReturn(
                        List.of(
                                new Valuation(
                                        pea.id(),
                                        LocalDate.of(2024, 3, 1),
                                        Money.euros("10000.00"),
                                        ValuationSource.COMPUTED)));

        PerformanceView view = service.portfolioPerformance(FROM, TODAY);

        // Without the seed the curve would start at 10 000 in March and show the flat as a
        // 250 000 jump on its next appraisal -- wealth appearing from nowhere.
        assertThat(view.series())
                .containsExactly(
                        new PerformancePoint(FROM, Money.euros("250000.00")),
                        new PerformancePoint(LocalDate.of(2024, 3, 1), Money.euros("260000.00")));
    }

    @Test
    @DisplayName("per-account performance uses that account's own history and curve")
    void reportsAccountPerformance() {
        Account pea = fundedPea();
        when(accounts.findById(pea.id())).thenReturn(Optional.of(pea));
        when(rates.currentRates()).thenReturn(Map.of());
        when(quotes.findLatest(anyMap()))
                .thenReturn(Map.of(WORLD, Quote.fresh(WORLD, Price.euros("130.00"), FIXED)));
        when(transactions.findByAccount(pea.id()))
                .thenReturn(List.of(deposit(pea.id(), "2020-01-01", "15000.00")));
        when(valuations.findByAccountBetween(pea.id(), FROM, TODAY)).thenReturn(List.of());

        PerformanceView view = service.accountPerformance(pea.id(), FROM, TODAY);

        // 5 000 cash + 100 at 130.00 = 18 000 against 15 000 over five years.
        assertThat(view.report().currentValue()).isEqualTo(Money.euros("18000.00"));
        assertThat(view.report().netGain()).isEqualTo(Money.euros("3000.00"));
        assertThat(view.report().annualisedReturn())
                .hasValueSatisfying(
                        rate ->
                                assertThat(rate.value().doubleValue())
                                        .isCloseTo(3.72, within(0.05)));
    }

    @Test
    void rejectsAnUnknownAccount() {
        AccountId missing = AccountId.newId();
        when(accounts.findById(missing)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.accountPerformance(missing, FROM, TODAY));
    }

    @Test
    @DisplayName("claims no annualised return for a portfolio with no recorded deposits")
    void claimsNoReturnWithoutHistory() {
        when(accounts.findAll()).thenReturn(List.of());
        when(rates.currentRates()).thenReturn(Map.of());
        when(transactions.findByAccounts(anyCollection())).thenReturn(Map.of());
        when(valuations.findAllBetween(any(), any())).thenReturn(List.of());

        PerformanceView view = service.portfolioPerformance(FROM, TODAY);

        assertThat(view.report().annualisedReturn()).isEmpty();
        assertThat(view.series()).isEmpty();
    }
}
