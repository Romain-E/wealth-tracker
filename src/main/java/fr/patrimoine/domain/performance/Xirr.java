package fr.patrimoine.domain.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Money-weighted annualised return: the internal rate of return of a series of dated cash flows.
 *
 * <p><b>Why this and not simply {@code (value - invested) / invested}.</b> The naive figure treats
 * 10 000 EUR invested ten years ago and 10 000 EUR invested last month as the same thing. XIRR is
 * the rate {@code r} that makes the discounted value of everything you put in equal what the
 * portfolio is worth today, so a euro invested for a decade counts for a decade. It is the metric
 * that answers "what did <em>my</em> money actually earn", which is the question a saver with
 * irregular deposits is asking. (It is not the metric for judging a fund manager &mdash; that is
 * time-weighted return, which strips out deposit timing precisely because the manager does not
 * control it.)
 *
 * <p><b>Why {@code double} and not {@code BigDecimal}.</b> This is an iterative approximation of an
 * equation with no closed-form solution; the answer is only ever accurate to the tolerance we
 * iterate to. BigDecimal would buy no accuracy, would cost an order of magnitude in speed inside
 * the inner loop, and would still be rounded to four decimals before anyone read it. Monetary
 * <em>amounts</em> stay in BigDecimal; a dimensionless convergence variable does not need to.
 *
 * <p><b>Why there is a fallback.</b> Newton-Raphson converges fast when it converges, but it is not
 * guaranteed to: a near-flat derivative sends the next iterate off to infinity, and a step below
 * {@code -100%} leaves the domain of {@code (1+r)^t} entirely. Both are reachable with real deposit
 * patterns, so on either condition this bails out to bisection, which is slower but cannot diverge
 * once a sign change has been bracketed. Shipping only the fast method is how you get a NaN in
 * production six months later.
 */
public final class Xirr {

    private static final int NEWTON_ITERATIONS = 50;
    private static final int BISECTION_ITERATIONS = 200;
    private static final double TOLERANCE = 1e-9;
    private static final double MIN_DERIVATIVE = 1e-12;
    private static final double DAYS_PER_YEAR = 365.0;

    /** A total loss is -100%; rates at or below it make the discount factor undefined. */
    private static final double LOWER_BOUND = -0.9999999;

    /** 10 000% a year. Beyond this we are fitting noise, not measuring a return. */
    private static final double UPPER_BOUND = 100.0;

    private static final double INITIAL_GUESS = 0.1;

    private Xirr() {}

    /**
     * @return the annualised rate as a ratio (0.0712 meaning 7.12%), or empty when the series
     *     admits no solution: fewer than two flows, or flows that never change sign &mdash; you
     *     cannot have a rate of return on money that only ever went one way.
     */
    public static OptionalDouble solve(List<CashFlow> flows) {
        if (flows.size() < 2 || !hasSignChange(flows)) {
            return OptionalDouble.empty();
        }

        List<CashFlow> ordered = flows.stream().sorted(Comparator.comparing(CashFlow::on)).toList();
        LocalDate origin = ordered.get(0).on();

        OptionalDouble newton = newtonRaphson(ordered, origin);
        return newton.isPresent() ? newton : bisect(ordered, origin);
    }

    private static OptionalDouble newtonRaphson(List<CashFlow> flows, LocalDate origin) {
        double rate = INITIAL_GUESS;
        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double value = npv(flows, origin, rate);
            if (Math.abs(value) < TOLERANCE) {
                return OptionalDouble.of(rate);
            }
            double slope = npvDerivative(flows, origin, rate);
            if (Math.abs(slope) < MIN_DERIVATIVE) {
                return OptionalDouble.empty(); // flat: the next step would explode
            }
            double next = rate - value / slope;
            if (!Double.isFinite(next) || next <= LOWER_BOUND || next > UPPER_BOUND) {
                return OptionalDouble.empty(); // left the domain
            }
            if (Math.abs(next - rate) < TOLERANCE) {
                return OptionalDouble.of(next);
            }
            rate = next;
        }
        return OptionalDouble.empty(); // did not settle in time
    }

    /**
     * Scans for a sign change, then halves the interval. Slow (about 200 evaluations) but
     * unconditionally convergent once bracketed, which is the entire point of having it.
     */
    private static OptionalDouble bisect(List<CashFlow> flows, LocalDate origin) {
        double[] probes = {
            LOWER_BOUND,
            -0.99,
            -0.9,
            -0.75,
            -0.5,
            -0.25,
            -0.1,
            0.0,
            0.1,
            0.25,
            0.5,
            1.0,
            2.0,
            5.0,
            10.0,
            25.0,
            50.0,
            UPPER_BOUND
        };

        for (int i = 0; i < probes.length - 1; i++) {
            double low = probes[i];
            double high = probes[i + 1];
            double atLow = npv(flows, origin, low);
            double atHigh = npv(flows, origin, high);
            if (!Double.isFinite(atLow) || !Double.isFinite(atHigh) || atLow * atHigh > 0) {
                continue;
            }
            for (int j = 0; j < BISECTION_ITERATIONS; j++) {
                double mid = (low + high) / 2.0;
                double atMid = npv(flows, origin, mid);
                if (Math.abs(atMid) < TOLERANCE || (high - low) / 2.0 < TOLERANCE) {
                    return OptionalDouble.of(mid);
                }
                if (atLow * atMid < 0) {
                    high = mid;
                } else {
                    low = mid;
                    atLow = atMid;
                }
            }
            return OptionalDouble.of((low + high) / 2.0);
        }
        return OptionalDouble.empty();
    }

    /** Net present value of the series at {@code rate}, discounting on an ACT/365 basis. */
    static double npv(List<CashFlow> flows, LocalDate origin, double rate) {
        double total = 0.0;
        for (CashFlow flow : flows) {
            total +=
                    flow.amount().amount().doubleValue()
                            / Math.pow(1.0 + rate, years(origin, flow.on()));
        }
        return total;
    }

    /** d(NPV)/d(rate). Analytic rather than a finite difference: exact, and cheaper. */
    static double npvDerivative(List<CashFlow> flows, LocalDate origin, double rate) {
        double total = 0.0;
        for (CashFlow flow : flows) {
            double t = years(origin, flow.on());
            total -= t * flow.amount().amount().doubleValue() / Math.pow(1.0 + rate, t + 1.0);
        }
        return total;
    }

    private static double years(LocalDate origin, LocalDate date) {
        return ChronoUnit.DAYS.between(origin, date) / DAYS_PER_YEAR;
    }

    private static boolean hasSignChange(List<CashFlow> flows) {
        boolean positive = false;
        boolean negative = false;
        for (CashFlow flow : flows) {
            positive |= flow.amount().isPositive();
            negative |= flow.amount().isNegative();
        }
        return positive && negative;
    }
}
