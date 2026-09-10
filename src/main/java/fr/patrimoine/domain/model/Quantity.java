package fr.patrimoine.domain.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A number of units held: shares, ETF units, or fractions of a crypto-asset.
 *
 * <p>Fractional by design &mdash; you can own 0.0184 BTC &mdash; and never negative: a short
 * position is a different concept that this domain does not model, so making it unrepresentable is
 * cheaper than validating for it everywhere.
 */
public record Quantity(BigDecimal value) implements Comparable<Quantity> {

    public static final int SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final Quantity ZERO = new Quantity(BigDecimal.ZERO);

    public Quantity {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("A quantity cannot be negative: " + value);
        }
        value = value.setScale(SCALE, ROUNDING);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public static Quantity of(long value) {
        return new Quantity(BigDecimal.valueOf(value));
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value.add(other.value));
    }

    /**
     * @throws IllegalArgumentException if the result would be negative; callers that need a
     *     business error (selling more than you hold) must check first and throw their own.
     */
    public Quantity minus(Quantity other) {
        return new Quantity(value.subtract(other.value));
    }

    /**
     * Spreads a total across these units: {@code total / quantity}, at full precision. Used to
     * recompute a weighted average cost after a purchase.
     */
    public BigDecimal spread(BigDecimal total) {
        return total.divide(value, MathContext.DECIMAL64);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isGreaterThan(Quantity other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.stripTrailingZeros().toPlainString();
    }
}
