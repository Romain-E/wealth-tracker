package fr.patrimoine.domain.performance;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.Valuation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
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

    /**
     * Consolidates snapshots from several accounts into a single portfolio curve.
     *
     * <p>The trap this avoids: summing whatever valuations happen to share a date. Accounts are not
     * snapshotted on the same calendar &mdash; a flat is appraised once a year, a PEA is marked to
     * market nightly &mdash; so a naive sum drops every account that has no row on a given day and
     * draws a portfolio that appears to crash and recover repeatedly. The chart would be pure
     * artefact.
     *
     * <p>Instead each account's last known value is carried forward ({@code floorEntry}), which is
     * also the economically correct answer: an asset you have not revalued is still worth what it
     * was last worth. Accounts with no snapshot at or before a date contribute nothing, so the
     * curve grows naturally as envelopes are opened rather than starting at a fictional full total.
     *
     * <p>Carrying forward only works if the starting value is known, which is the second trap: the
     * window's edge. A flat appraised last year has no row inside this year's window, so it would
     * be missing until its next appraisal and then appear as a sudden jump. Callers therefore also
     * pass each account's last snapshot before {@code from}; those rows are starting balances, so
     * they anchor the curve on {@code from} rather than adding points of their own.
     */
    public List<PerformancePoint> aggregateSeries(List<Valuation> valuations, LocalDate from) {
        if (valuations.isEmpty()) {
            return List.of();
        }

        Map<AccountId, NavigableMap<LocalDate, Money>> byAccount = new LinkedHashMap<>();
        SortedSet<LocalDate> dates = new TreeSet<>();
        for (Valuation valuation : valuations) {
            byAccount
                    .computeIfAbsent(valuation.accountId(), key -> new TreeMap<>())
                    .put(valuation.on(), valuation.value());
            dates.add(valuation.on().isBefore(from) ? from : valuation.on());
        }

        Currency currency = valuations.get(0).value().currency();
        List<PerformancePoint> series = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            Money total = Money.zero(currency);
            for (NavigableMap<LocalDate, Money> history : byAccount.values()) {
                Map.Entry<LocalDate, Money> known = history.floorEntry(date);
                if (known != null) {
                    total = total.plus(known.getValue());
                }
            }
            series.add(new PerformancePoint(date, total));
        }
        return List.copyOf(series);
    }
}
