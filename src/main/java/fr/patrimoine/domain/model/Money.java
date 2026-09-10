package fr.patrimoine.domain.model;

import fr.patrimoine.domain.error.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary <em>amount</em>: a balance, a total, a gain. Always rounded to the minor unit.
 *
 * <p>Two deliberate decisions here.
 *
 * <p><b>1. {@link BigDecimal}, never {@code double}.</b> Binary floating point cannot represent
 * 0.10 exactly, so {@code 0.1 + 0.2 != 0.3}. On a wealth report those cents accumulate and end up
 * visible, and "the rounding is a floating-point artefact" is not something you want to explain to
 * someone looking at their own net worth.
 *
 * <p><b>2. Amounts are distinct from {@link Price}s.</b> An amount is rounded to two decimals
 * because that is what a bank statement shows. A price is not: a crypto-asset can trade at
 * 0.00000412 EUR, and rounding it to a cent would erase it entirely. Keeping them as separate types
 * means the compiler stops you from storing a price where an amount belongs, and it makes the point
 * at which precision is lost &mdash; {@link Price#times(Quantity)} &mdash; explicit and single.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final Currency EUR = Currency.getInstance("EUR");

    /** Two decimals: euros and cents. */
    public static final int SCALE = 2;

    /**
     * Banker's rounding. Unlike HALF_UP it does not systematically inflate a long series of
     * roundings, which matters when totalling hundreds of positions.
     */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public static final Money ZERO_EUR = new Money(BigDecimal.ZERO, EUR);

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money euros(String amount) {
        return new Money(new BigDecimal(amount), EUR);
    }

    public static Money euros(long amount) {
        return new Money(BigDecimal.valueOf(amount), EUR);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    /**
     * This amount as a ratio of {@code base}, at high precision so that callers can turn it into a
     * percentage without compounding rounding error. Returns zero for a zero base rather than
     * throwing: an empty portfolio has a 0% allocation, not an undefined one.
     */
    public BigDecimal ratioTo(Money base) {
        requireSameCurrency(base);
        if (base.isZero()) {
            return BigDecimal.ZERO;
        }
        return amount.divide(base.amount, MathContext.DECIMAL64);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
