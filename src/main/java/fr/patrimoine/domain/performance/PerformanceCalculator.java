package fr.patrimoine.domain.performance;

import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.Valuation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Domain service turning a transaction history plus a current value into a performance report.
 *
 * <p>The one subtle rule is which movements count. Only transactions that cross the boundary of the
 * portfolio are cash flows: deposits and withdrawals. A purchase, a dividend, a management fee or a
 * tax all move money <em>inside</em> the envelope and must not be counted, or the return is
 * nonsense. Buying 5 000 EUR of shares with cash already in the PEA did not cost you 5 000 EUR of
 * new savings. That rule lives on {@link
 * fr.patrimoine.domain.model.TransactionType#isExternalFlow()} so it is declared once, next to the
 * types themselves.
 */
public final class PerformanceCalculator {

    public PerformanceReport report(
            List<Transaction> transactions, Money currentValue, LocalDate asOf) {

        List<Transaction> external =
                transactions.stream()
                        .filter(t -> t.type().isExternalFlow())
                        .filter(t -> !t.date().isAfter(asOf))
                        .sorted(Comparator.comparing(Transaction::date))
                        .toList();

        Money netInvested = Money.zero(currentValue.currency());
        for (Transaction transaction : external) {
            netInvested = netInvested.plus(transaction.amount());
        }

        Money netGain = currentValue.minus(netInvested);
        Percentage netGainRatio =
                netInvested.isPositive()
                        ? Percentage.ofRatio(netGain.ratioTo(netInvested))
                        : Percentage.ZERO;

        return new PerformanceReport(
                netInvested,
                currentValue,
                netGain,
                netGainRatio,
                annualisedReturn(external, currentValue, asOf));
    }

    /**
     * Builds the XIRR series: every external movement, sign-flipped into the investor's frame, plus
     * the portfolio's current value as a terminal inflow — the money you would receive if you
     * liquidated today.
     */
    private Optional<Percentage> annualisedReturn(
            List<Transaction> external, Money currentValue, LocalDate asOf) {

        if (external.isEmpty()) {
            return Optional.empty();
        }

        List<CashFlow> flows = new ArrayList<>(external.size() + 1);
        for (Transaction transaction : external) {
            // Account frame: a deposit is +100. Investor frame: it is -100, money leaving the
            // pocket.
            flows.add(new CashFlow(transaction.date(), transaction.amount().negated()));
        }
        flows.add(CashFlow.returned(asOf, currentValue));

        OptionalDouble rate = Xirr.solve(flows);
        return rate.isPresent()
                ? Optional.of(Percentage.ofRatio(BigDecimal.valueOf(rate.getAsDouble())))
                : Optional.empty();
    }

    /**
     * Turns stored valuations into a chart series, sorted and de-duplicated by day &mdash; a
     * recomputed valuation supersedes an earlier one for the same date rather than plotting twice.
     */
    public List<PerformancePoint> series(List<Valuation> valuations) {
        return valuations.stream()
                .collect(
                        Collectors.toMap(
                                Valuation::on, v -> v, (first, second) -> second, TreeMap::new))
                .values()
                .stream()
                .map(v -> new PerformancePoint(v.on(), v.value()))
                .toList();
    }
}
