package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Accrued interest on a regulated passbook, under the French <i>regle des quinzaines</i>.
 *
 * <p>This is the rule that a naive implementation always gets wrong. Interest on a Livret A or an
 * LDDS is <b>not</b> computed daily. The year is divided into twenty-four fortnights, starting on
 * the 1st and the 16th of each month, and each fortnight pays {@code rate / 24} on whatever balance
 * was in force at its start. The consequences are counter-intuitive and entirely deliberate on the
 * part of the legislator:
 *
 * <ul>
 *   <li>Money paid in on the 2nd earns nothing until the 16th &mdash; a fortnight of free float for
 *       the bank. Paying in on the 15th or the 31st is therefore strictly better than the 16th or
 *       the 1st.
 *   <li>Money withdrawn on the 14th stops earning all the way back to the 1st, forfeiting a
 *       fortnight already served. Withdrawing on the 1st or the 16th instead gives up only the
 *       fortnight that has just begun.
 * </ul>
 *
 * <p>Interest itself is capitalised once a year, on 31 December, and only then joins the balance
 * that earns further interest. That is why {@code openingBalance} and the movements since the last
 * capitalisation are the two inputs here.
 *
 * <p>A part-completed fortnight pays nothing, which is why this returns zero for any period shorter
 * than one full fortnight.
 */
public final class QuinzaineInterestCalculator {

    private static final BigDecimal QUINZAINES_PER_YEAR = BigDecimal.valueOf(24);
    private static final int MID_MONTH = 16;

    private QuinzaineInterestCalculator() {}

    /**
     * A dated change to the balance, signed: positive for a payment in, negative for a withdrawal.
     */
    public record BalanceChange(LocalDate on, Money delta) {}

    /**
     * @param openingBalance balance at {@code from}, i.e. just after the last capitalisation
     * @param changes movements between {@code from} and {@code to}, in any order
     * @param annualRate the product's posted annual rate
     * @param from start of the accrual period, normally 1 January
     * @param to the valuation date, exclusive
     */
    public static Money accrue(
            Money openingBalance,
            List<BalanceChange> changes,
            Percentage annualRate,
            LocalDate from,
            LocalDate to) {

        if (!to.isAfter(from) || annualRate.value().signum() <= 0) {
            return Money.zero(openingBalance.currency());
        }

        List<EffectiveChange> effective = toEffectiveDates(changes);
        BigDecimal perQuinzaineRate =
                annualRate.asRatio().divide(QUINZAINES_PER_YEAR, MathContext.DECIMAL64);

        BigDecimal accrued = BigDecimal.ZERO;
        for (LocalDate start = firstBoundaryOnOrAfter(from);
                !nextBoundary(start).isAfter(to);
                start = nextBoundary(start)) {
            BigDecimal inForce = balanceInForceAt(openingBalance, effective, start);
            if (inForce.signum() > 0) {
                accrued = accrued.add(inForce.multiply(perQuinzaineRate), MathContext.DECIMAL64);
            }
        }

        return Money.of(accrued, openingBalance.currency());
    }

    /**
     * Maps each movement to the fortnight boundary from which it actually affects the earning
     * balance: payments start earning at the <em>next</em> boundary, withdrawals stop earning from
     * the <em>preceding</em> one. This asymmetry is the rule, not an approximation of it.
     */
    private static List<EffectiveChange> toEffectiveDates(List<BalanceChange> changes) {
        List<EffectiveChange> effective = new ArrayList<>(changes.size());
        for (BalanceChange change : changes) {
            LocalDate date =
                    change.delta().isNegative()
                            ? boundaryOnOrBefore(change.on())
                            : nextBoundaryStrictlyAfter(change.on());
            effective.add(new EffectiveChange(date, change.delta().amount()));
        }
        return effective;
    }

    private static BigDecimal balanceInForceAt(
            Money openingBalance, List<EffectiveChange> changes, LocalDate boundary) {
        BigDecimal balance = openingBalance.amount();
        for (EffectiveChange change : changes) {
            if (!change.effectiveFrom().isAfter(boundary)) {
                balance = balance.add(change.delta());
            }
        }
        return balance;
    }

    static LocalDate boundaryOnOrBefore(LocalDate date) {
        return date.getDayOfMonth() >= MID_MONTH
                ? date.withDayOfMonth(MID_MONTH)
                : date.withDayOfMonth(1);
    }

    static LocalDate nextBoundaryStrictlyAfter(LocalDate date) {
        return date.getDayOfMonth() < MID_MONTH
                ? date.withDayOfMonth(MID_MONTH)
                : date.plusMonths(1).withDayOfMonth(1);
    }

    static LocalDate firstBoundaryOnOrAfter(LocalDate date) {
        int day = date.getDayOfMonth();
        if (day == 1 || day == MID_MONTH) {
            return date;
        }
        return nextBoundaryStrictlyAfter(date);
    }

    static LocalDate nextBoundary(LocalDate boundary) {
        return boundary.getDayOfMonth() == 1
                ? boundary.withDayOfMonth(MID_MONTH)
                : boundary.plusMonths(1).withDayOfMonth(1);
    }

    private record EffectiveChange(LocalDate effectiveFrom, BigDecimal delta) {}
}
