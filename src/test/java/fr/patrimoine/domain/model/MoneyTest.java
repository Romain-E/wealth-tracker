package fr.patrimoine.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.patrimoine.domain.error.CurrencyMismatchException;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Nested
    @DisplayName("decimal correctness")
    class DecimalCorrectness {

        @Test
        @DisplayName("adds tenths exactly, which binary floating point cannot")
        void addsTenthsExactly() {
            Money result = Money.euros("0.10").plus(Money.euros("0.20"));

            assertThat(result).isEqualTo(Money.euros("0.30"));
            // The reason this class exists: 0.1 + 0.2 == 0.30000000000000004 in double.
            assertThat(result.amount()).isEqualByComparingTo("0.30");
        }

        @ParameterizedTest(name = "{0} rounds to {1} (half-even)")
        @CsvSource({"2.345, 2.34", "2.355, 2.36", "2.365, 2.36", "2.375, 2.38", "-2.345, -2.34"})
        @DisplayName("rounds half to even, so a long series of roundings does not drift upward")
        void roundsHalfToEven(String input, String expected) {
            assertThat(Money.euros(input).amount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("normalises scale so that equality is not scale-sensitive")
        void normalisesScale() {
            // BigDecimal.equals says 10 != 10.00; Money must not inherit that trap.
            assertThat(Money.euros("10")).isEqualTo(Money.euros("10.00"));
            assertThat(Money.euros(10)).isEqualTo(Money.euros("10.000"));
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void addsSubtractsAndMultiplies() {
            assertThat(Money.euros("100.00").plus(Money.euros("25.50")))
                    .isEqualTo(Money.euros("125.50"));
            assertThat(Money.euros("100.00").minus(Money.euros("25.50")))
                    .isEqualTo(Money.euros("74.50"));
            assertThat(Money.euros("100.00").times(new BigDecimal("1.5")))
                    .isEqualTo(Money.euros("150.00"));
        }

        @Test
        void allowsNegativeAmountsBecauseALossIsARealAmount() {
            Money loss = Money.euros("100.00").minus(Money.euros("250.00"));

            assertThat(loss).isEqualTo(Money.euros("-150.00"));
            assertThat(loss.isNegative()).isTrue();
            assertThat(loss.abs()).isEqualTo(Money.euros("150.00"));
            assertThat(loss.negated()).isEqualTo(Money.euros("150.00"));
        }

        @Test
        @DisplayName("refuses to combine currencies rather than silently assuming a rate")
        void refusesCrossCurrencyArithmetic() {
            Money euros = Money.euros("100.00");
            Money dollars = Money.of(new BigDecimal("100.00"), USD);

            assertThatExceptionOfType(CurrencyMismatchException.class)
                    .isThrownBy(() -> euros.plus(dollars))
                    .withMessageContaining("EUR")
                    .withMessageContaining("USD");
            assertThatExceptionOfType(CurrencyMismatchException.class)
                    .isThrownBy(() -> euros.compareTo(dollars));
        }
    }

    @Nested
    @DisplayName("ratios")
    class Ratios {

        @Test
        void computesShareOfATotal() {
            BigDecimal ratio = Money.euros("2500.00").ratioTo(Money.euros("10000.00"));

            assertThat(Percentage.ofRatio(ratio)).isEqualTo(Percentage.ofPoints("25.0000"));
        }

        @Test
        @DisplayName("an empty portfolio has a zero share, not an undefined one")
        void returnsZeroRatioAgainstAnEmptyTotal() {
            assertThat(Money.euros("100.00").ratioTo(Money.ZERO_EUR)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("keeps enough precision that thirds do not collapse")
        void keepsPrecisionOnRepeatingDecimals() {
            BigDecimal ratio = Money.euros("100.00").ratioTo(Money.euros("300.00"));

            assertThat(ratio.doubleValue())
                    .isCloseTo(0.3333333, org.assertj.core.data.Offset.offset(1e-6));
        }
    }

    @Test
    void comparesAndOrders() {
        assertThat(Money.euros("100.00").isGreaterThan(Money.euros("99.99"))).isTrue();
        assertThat(Money.euros("100.00").isLessThan(Money.euros("100.01"))).isTrue();
        assertThat(Money.euros("100.00").isGreaterThan(Money.euros("100.00"))).isFalse();
        assertThat(Money.ZERO_EUR.isZero()).isTrue();
        assertThat(Money.ZERO_EUR.isPositive()).isFalse();
    }

    @Test
    void printsAmountAndCurrency() {
        assertThat(Money.euros("1234.50")).hasToString("1234.50 EUR");
    }
}
