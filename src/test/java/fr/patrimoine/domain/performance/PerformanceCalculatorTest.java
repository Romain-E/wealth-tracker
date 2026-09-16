package fr.patrimoine.domain.performance;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.TransactionType;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationSource;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PerformanceCalculator: only money crossing the portfolio boundary counts")
class PerformanceCalculatorTest {

    private static final AccountId ACCOUNT = AccountId.newId();
    private static final AccountId FLAT = AccountId.newId();
    private static final LocalDate TODAY = LocalDate.of(2025, 1, 1);
    private static final LocalDate WINDOW_START = LocalDate.of(2024, 1, 1);
    private static final InstrumentId WORLD = InstrumentId.of("IE00B4L5Y983");

    private final PerformanceCalculator calculator = new PerformanceCalculator();

    private static Transaction cash(TransactionType type, String date, String amount) {
        return Transaction.cashMovement(ACCOUNT, type, LocalDate.parse(date), Money.euros(amount));
    }

    @Test
    @DisplayName("nets deposits against withdrawals to find what was actually invested")
    void computesNetInvestedAndGain() {
        List<Transaction> history =
                List.of(
                        cash(TransactionType.DEPOSIT, "2020-01-01", "10000.00"),
                        cash(TransactionType.DEPOSIT, "2021-01-01", "5000.00"),
                        cash(TransactionType.WITHDRAWAL, "2023-01-01", "-3000.00"));

        PerformanceReport report = calculator.report(history, Money.euros("18000.00"), TODAY);

        assertThat(report.netInvested()).isEqualTo(Money.euros("12000.00"));
        assertThat(report.netGain()).isEqualTo(Money.euros("6000.00"));
        assertThat(report.netGainRatio()).isEqualTo(Percentage.ofPoints("50.0000"));
        assertThat(report.isPositive()).isTrue();
    }

    @Test
    @DisplayName("internal movements are ignored: a purchase is not new savings")
    void ignoresMovementsInsideTheEnvelope() {
        List<Transaction> history =
                List.of(
                        cash(TransactionType.DEPOSIT, "2020-01-01", "10000.00"),
                        // All of these move money around inside the envelope and must not count.
                        Transaction.trade(
                                ACCOUNT,
                                TransactionType.BUY,
                                LocalDate.parse("2020-01-15"),
                                Money.euros("-8000.00"),
                                WORLD,
                                Quantity.of(80),
                                Price.euros("100.00")),
                        cash(TransactionType.DIVIDEND, "2021-06-01", "250.00"),
                        cash(TransactionType.FEE, "2021-12-31", "-30.00"),
                        cash(TransactionType.TAX, "2022-05-01", "-45.00"),
                        cash(TransactionType.INTEREST, "2023-12-31", "12.00"));

        PerformanceReport report = calculator.report(history, Money.euros("15000.00"), TODAY);

        assertThat(report.netInvested()).isEqualTo(Money.euros("10000.00"));
        assertThat(report.netGain()).isEqualTo(Money.euros("5000.00"));
    }

    @Test
    @DisplayName("reports a money-weighted annualised return alongside the raw gain")
    void reportsAnAnnualisedReturn() {
        List<Transaction> history =
                List.of(
                        cash(TransactionType.DEPOSIT, "2020-01-01", "10000.00"),
                        cash(TransactionType.DEPOSIT, "2021-01-01", "5000.00"));

        PerformanceReport report = calculator.report(history, Money.euros("22000.00"), TODAY);

        // Same series as the verified XIRR case: 8.53% a year.
        assertThat(report.annualisedReturn())
                .hasValueSatisfying(
                        rate ->
                                assertThat(rate.value().doubleValue())
                                        .isCloseTo(
                                                8.5272, org.assertj.core.data.Offset.offset(1e-3)));

        // The cumulative gain is 46.67% -- a much larger-looking number for the same performance.
        assertThat(report.netGainRatio().value().doubleValue())
                .isCloseTo(46.6667, org.assertj.core.data.Offset.offset(1e-3));
    }

    @Test
    @DisplayName("no annualised return is claimed when there is nothing to annualise")
    void reportsNoAnnualisedReturnWithoutExternalFlows() {
        PerformanceReport report = calculator.report(List.of(), Money.euros("1000.00"), TODAY);

        assertThat(report.annualisedReturn()).isEmpty();
        assertThat(report.netInvested()).isEqualTo(Money.ZERO_EUR);
        assertThat(report.netGainRatio()).isEqualTo(Percentage.ZERO);
    }

    @Test
    @DisplayName("ignores movements dated after the valuation date")
    void ignoresFutureDatedMovements() {
        List<Transaction> history =
                List.of(
                        cash(TransactionType.DEPOSIT, "2020-01-01", "10000.00"),
                        cash(TransactionType.DEPOSIT, "2026-01-01", "99999.00"));

        PerformanceReport report = calculator.report(history, Money.euros("12000.00"), TODAY);

        assertThat(report.netInvested()).isEqualTo(Money.euros("10000.00"));
    }

    @Test
    @DisplayName("a loss is reported as a negative gain, not as an absolute value")
    void reportsLosses() {
        List<Transaction> history =
                List.of(cash(TransactionType.DEPOSIT, "2024-01-01", "10000.00"));

        PerformanceReport report = calculator.report(history, Money.euros("7500.00"), TODAY);

        assertThat(report.netGain()).isEqualTo(Money.euros("-2500.00"));
        assertThat(report.isPositive()).isFalse();
        assertThat(report.netGainRatio()).isEqualTo(Percentage.ofPoints("-25.0000"));
    }

    @Test
    @DisplayName("the chart series is sorted by date and keeps one point per day")
    void buildsASortedDeduplicatedSeries() {
        List<Valuation> valuations =
                List.of(
                        valuation("2025-03-01", "12000.00"),
                        valuation("2025-01-01", "10000.00"),
                        valuation("2025-02-01", "11000.00"),
                        // A recomputed valuation for a day already recorded supersedes it.
                        valuation("2025-02-01", "11500.00"));

        List<PerformancePoint> series = calculator.series(valuations);

        assertThat(series)
                .extracting(PerformancePoint::on)
                .containsExactly(
                        LocalDate.parse("2025-01-01"),
                        LocalDate.parse("2025-02-01"),
                        LocalDate.parse("2025-03-01"));
        assertThat(series.get(1).value()).isEqualTo(Money.euros("11500.00"));
    }

    @Test
    @DisplayName("consolidation carries each account's last value forward instead of dropping it")
    void carriesSparseSnapshotsForward() {
        List<Valuation> valuations =
                List.of(
                        valuation(ACCOUNT, "2024-01-01", "10000.00"),
                        valuation(FLAT, "2024-01-01", "250000.00"),
                        // The PEA is marked again in March; the flat is not re-appraised.
                        valuation(ACCOUNT, "2024-03-01", "11000.00"));

        List<PerformancePoint> series = calculator.aggregateSeries(valuations, WINDOW_START);

        // Summing only same-day rows would show 11 000 in March: a 96% crash that never happened.
        assertThat(series)
                .containsExactly(
                        point("2024-01-01", "260000.00"), point("2024-03-01", "261000.00"));
    }

    @Test
    @DisplayName("an account adds nothing to the curve before its first snapshot")
    void growsAsAccountsAreOpened() {
        List<Valuation> valuations =
                List.of(
                        valuation(ACCOUNT, "2024-01-01", "10000.00"),
                        valuation(FLAT, "2024-06-01", "250000.00"));

        assertThat(calculator.aggregateSeries(valuations, WINDOW_START))
                .containsExactly(point("2024-01-01", "10000.00"), point("2024-06-01", "260000.00"));
    }

    @Test
    @DisplayName("a snapshot older than the window anchors the curve on the window's first day")
    void anchorsTheWindowOnEarlierSnapshots() {
        List<Valuation> valuations =
                List.of(
                        // Appraised the year before and not since: a starting balance, not a point.
                        valuation(FLAT, "2023-06-01", "250000.00"),
                        valuation(ACCOUNT, "2024-03-01", "10000.00"));

        assertThat(calculator.aggregateSeries(valuations, WINDOW_START))
                .containsExactly(
                        point("2024-01-01", "250000.00"), point("2024-03-01", "260000.00"));
    }

    @Test
    void consolidatesNothingIntoAnEmptyCurve() {
        assertThat(calculator.aggregateSeries(List.of(), WINDOW_START)).isEmpty();
    }

    private static Valuation valuation(String date, String amount) {
        return valuation(ACCOUNT, date, amount);
    }

    private static Valuation valuation(AccountId account, String date, String amount) {
        return new Valuation(
                account, LocalDate.parse(date), Money.euros(amount), ValuationSource.COMPUTED);
    }

    private static PerformancePoint point(String date, String amount) {
        return new PerformancePoint(LocalDate.parse(date), Money.euros(amount));
    }
}
