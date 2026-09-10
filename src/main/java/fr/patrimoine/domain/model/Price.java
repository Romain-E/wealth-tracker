package fr.patrimoine.domain.model;

import fr.patrimoine.domain.error.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * A unit price, or a weighted average cost per unit. Held at eight decimals.
 *
 * <p>Why not reuse {@link Money}: a share of TotalEnergies quotes at 58.42 EUR, but one SHIB quotes
 * at about 0.00001 EUR. Rounding prices to the cent would collapse the second to zero and silently
 * value a whole crypto account at nothing. Eight decimals is the same precision Bitcoin itself uses
 * (one satoshi) and comfortably covers equities.
 *
 * <p>{@link #times(Quantity)} is the single, explicit place where that precision collapses back
 * down to a real-world amount.
 */
public record Price(BigDecimal value, Currency currency) implements Comparable<Price> {

    public static final int SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Price {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(currency, "currency");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("A price cannot be negative: " + value);
        }
        value = value.setScale(SCALE, ROUNDING);
    }

    public static Price of(BigDecimal value, Currency currency) {
        return new Price(value, currency);
    }

    public static Price euros(String value) {
        return new Price(new BigDecimal(value), Money.EUR);
    }

    public static Price zero(Currency currency) {
        return new Price(BigDecimal.ZERO, currency);
    }

    /** Total amount for {@code quantity} units, rounded to the minor unit exactly once. */
    public Money times(Quantity quantity) {
        return Money.of(value.multiply(quantity.value()), currency);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    @Override
    public int compareTo(Price other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.stripTrailingZeros().toPlainString() + " " + currency.getCurrencyCode();
    }
}
