package fr.patrimoine.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Price and Quantity: why prices are not amounts")
class PriceAndQuantityTest {

    @Nested
    class Prices {

        @Test
        @DisplayName("keeps sub-cent precision that Money would destroy")
        void keepsSubCentPrecision() {
            Price shib = Price.euros("0.00001234");

            assertThat(shib.value()).isEqualByComparingTo("0.00001234");
            assertThat(shib.isZero()).isFalse();

            // The same number as a Money would round to zero and value the whole holding at
            // nothing.
            assertThat(Money.euros("0.00001234")).isEqualTo(Money.ZERO_EUR);
        }

        @Test
        @DisplayName("collapses to cents exactly once, when multiplied out to an amount")
        void roundsOnlyWhenTurnedIntoAnAmount() {
            Price price = Price.euros("0.00001234");
            Quantity quantity = Quantity.of("1500000");

            // 0.00001234 * 1_500_000 = 18.51
            assertThat(price.times(quantity)).isEqualTo(Money.euros("18.51"));
        }

        @Test
        void rejectsNegativePrices() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Price.euros("-1.00"))
                    .withMessageContaining("cannot be negative");
        }
    }

    @Nested
    class Quantities {

        @Test
        @DisplayName("is fractional, because you can own a fraction of a bitcoin")
        void supportsFractionalUnits() {
            assertThat(Quantity.of("0.01840000").value()).isEqualByComparingTo("0.0184");
        }

        @Test
        @DisplayName("makes a short position unrepresentable rather than validating against it")
        void rejectsNegativeQuantities() {
            assertThatIllegalArgumentException().isThrownBy(() -> Quantity.of("-1"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Quantity.of(5).minus(Quantity.of(6)));
        }

        @Test
        void addsAndSubtracts() {
            assertThat(Quantity.of(10).plus(Quantity.of("2.5"))).isEqualTo(Quantity.of("12.5"));
            assertThat(Quantity.of(10).minus(Quantity.of(10))).isEqualTo(Quantity.ZERO);
            assertThat(Quantity.ZERO.isZero()).isTrue();
        }

        @Test
        @DisplayName("spreads a total across units at full precision, for weighted average cost")
        void spreadsATotalAcrossUnits() {
            BigDecimal unit = Quantity.of(3).spread(new BigDecimal("100.00"));

            assertThat(unit.doubleValue())
                    .isCloseTo(33.3333333, org.assertj.core.data.Offset.offset(1e-6));
        }
    }
}
