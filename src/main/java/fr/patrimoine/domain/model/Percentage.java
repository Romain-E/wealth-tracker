package fr.patrimoine.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A percentage expressed in percentage points: {@code Percentage(new BigDecimal("22.8000"))} is
 * 22.8%, not 2280%.
 *
 * <p>Four decimals because an annualised return of 7.1234% is meaningfully different from 7.12%
 * when compounded over twenty years, and because rounding on the way in would make round-tripping
 * through {@link #asRatio()} lossy.
 */
public record Percentage(BigDecimal value) implements Comparable<Percentage> {

    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final Percentage ZERO = new Percentage(BigDecimal.ZERO);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Percentage {
        Objects.requireNonNull(value, "value");
        value = value.setScale(SCALE, ROUNDING);
    }

    /** From a ratio, i.e. {@code 0.228} becomes 22.8%. */
    public static Percentage ofRatio(BigDecimal ratio) {
        return new Percentage(ratio.multiply(HUNDRED));
    }

    public static Percentage ofPoints(String points) {
        return new Percentage(new BigDecimal(points));
    }

    public BigDecimal asRatio() {
        return value.divide(HUNDRED, SCALE + 2, ROUNDING);
    }

    @Override
    public int compareTo(Percentage other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString() + "%";
    }
}
