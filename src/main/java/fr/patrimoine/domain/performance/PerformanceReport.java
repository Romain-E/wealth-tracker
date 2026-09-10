package fr.patrimoine.domain.performance;

import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import java.util.Objects;
import java.util.Optional;

/**
 * How an account or a portfolio has done.
 *
 * <p>Two returns are reported side by side because they answer different questions and disagree
 * often enough to be worth showing both:
 *
 * <ul>
 *   <li>{@code netGainRatio} &mdash; total gain over net money put in. Cumulative, not annualised,
 *       and blind to when the money arrived. It is what a user computes in their head.
 *   <li>{@code annualisedReturn} &mdash; the money-weighted rate from {@link Xirr}. Comparable
 *       against a savings rate or an index because it is per year and accounts for timing.
 * </ul>
 *
 * <p>{@code annualisedReturn} is an {@link Optional} rather than a zero: a portfolio funded
 * yesterday, or one with no deposits at all, has no meaningful annualised return, and reporting
 * 0.00% would be a claim rather than an absence.
 */
public record PerformanceReport(
        Money netInvested,
        Money currentValue,
        Money netGain,
        Percentage netGainRatio,
        Optional<Percentage> annualisedReturn) {

    public PerformanceReport {
        Objects.requireNonNull(netInvested, "netInvested");
        Objects.requireNonNull(currentValue, "currentValue");
        Objects.requireNonNull(netGain, "netGain");
        Objects.requireNonNull(netGainRatio, "netGainRatio");
        Objects.requireNonNull(annualisedReturn, "annualisedReturn");
    }

    public boolean isPositive() {
        return netGain.isPositive();
    }
}
