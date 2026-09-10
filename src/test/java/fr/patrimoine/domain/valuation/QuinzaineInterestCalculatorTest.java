package fr.patrimoine.domain.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.valuation.QuinzaineInterestCalculator.BalanceChange;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Regle des quinzaines: interest accrues per fortnight, not per day")
class QuinzaineInterestCalculatorTest {

    private static final Percentage RATE = Percentage.ofPoints("3.0000");
    private static final LocalDate JAN_1 = LocalDate.of(2025, 1, 1);
    private static final LocalDate NEXT_JAN_1 = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("a full year pays exactly the posted rate: 24 fortnights of rate/24")
    void aFullYearPaysThePostedRate() {
        Money interest =
                QuinzaineInterestCalculator.accrue(
                        Money.euros("10000.00"), List.of(), RATE, JAN_1, NEXT_JAN_1);

        assertThat(interest).isEqualTo(Money.euros("300.00"));
    }

    @Test
    @DisplayName("one completed fortnight pays a twenty-fourth of the annual rate")
    void oneFortnightPaysOneTwentyFourth() {
        Money interest =
                QuinzaineInterestCalculator.accrue(
                        Money.euros("10000.00"), List.of(), RATE, JAN_1, LocalDate.of(2025, 1, 16));

        assertThat(interest).isEqualTo(Money.euros("12.50"));
    }

    @Test
    @DisplayName("a fortnight left unfinished pays nothing at all")
    void aPartialFortnightPaysNothing() {
        Money interest =
                QuinzaineInterestCalculator.accrue(
                        Money.euros("10000.00"), List.of(), RATE, JAN_1, LocalDate.of(2025, 1, 15));

        assertThat(interest).isEqualTo(Money.ZERO_EUR);
    }

    @Nested
    @DisplayName("payments start earning at the following fortnight")
    class Payments {

        @Test
        @DisplayName("money paid in on the 2nd earns nothing until the 16th")
        void aPaymentOnTheSecondWaitsUntilTheSixteenth() {
            Money interest =
                    QuinzaineInterestCalculator.accrue(
                            Money.ZERO_EUR,
                            List.of(
                                    new BalanceChange(
                                            LocalDate.of(2025, 1, 2), Money.euros("10000.00"))),
                            RATE,
                            JAN_1,
                            LocalDate.of(2025, 2, 1));

            // Only the second fortnight earns.
            assertThat(interest).isEqualTo(Money.euros("12.50"));
        }

        @Test
        @DisplayName("depositing on the 15th beats depositing on the 16th by a full fortnight")
        void oneDayLaterCostsTwoWeeks() {
            Money onThe15th = accrueSinglePayment(LocalDate.of(2025, 1, 15));
            Money onThe16th = accrueSinglePayment(LocalDate.of(2025, 1, 16));

            assertThat(onThe15th).isEqualTo(Money.euros("25.00")); // earns from Jan 16
            assertThat(onThe16th).isEqualTo(Money.euros("12.50")); // earns only from Feb 1
        }

        private Money accrueSinglePayment(LocalDate on) {
            return QuinzaineInterestCalculator.accrue(
                    Money.ZERO_EUR,
                    List.of(new BalanceChange(on, Money.euros("10000.00"))),
                    RATE,
                    JAN_1,
                    LocalDate.of(2025, 2, 16));
        }
    }

    @Nested
    @DisplayName("withdrawals stop earning from the fortnight they fall in")
    class Withdrawals {

        @Test
        @DisplayName("withdrawing on the 14th forfeits interest back to the 1st")
        void aWithdrawalOnTheFourteenthForfeitsTheWholeFortnight() {
            Money interest =
                    QuinzaineInterestCalculator.accrue(
                            Money.euros("10000.00"),
                            List.of(
                                    new BalanceChange(
                                            LocalDate.of(2025, 1, 14), Money.euros("-10000.00"))),
                            RATE,
                            JAN_1,
                            LocalDate.of(2025, 2, 1));

            assertThat(interest).isEqualTo(Money.ZERO_EUR);
        }

        @Test
        @DisplayName("waiting until the 16th keeps the first fortnight's interest")
        void aWithdrawalOnTheSixteenthKeepsTheFirstFortnight() {
            Money interest =
                    QuinzaineInterestCalculator.accrue(
                            Money.euros("10000.00"),
                            List.of(
                                    new BalanceChange(
                                            LocalDate.of(2025, 1, 16), Money.euros("-10000.00"))),
                            RATE,
                            JAN_1,
                            LocalDate.of(2025, 2, 1));

            assertThat(interest).isEqualTo(Money.euros("12.50"));
        }
    }

    @Test
    @DisplayName("a balance driven negative earns nothing rather than negative interest")
    void neverAccruesOnANegativeBalance() {
        Money interest =
                QuinzaineInterestCalculator.accrue(
                        Money.euros("1000.00"),
                        List.of(
                                new BalanceChange(
                                        LocalDate.of(2025, 1, 1), Money.euros("-5000.00"))),
                        RATE,
                        JAN_1,
                        NEXT_JAN_1);

        assertThat(interest).isEqualTo(Money.ZERO_EUR);
    }

    @Test
    void paysNothingForANonPositiveRateOrAnEmptyPeriod() {
        assertThat(
                        QuinzaineInterestCalculator.accrue(
                                Money.euros("10000.00"),
                                List.of(),
                                Percentage.ZERO,
                                JAN_1,
                                NEXT_JAN_1))
                .isEqualTo(Money.ZERO_EUR);
        assertThat(
                        QuinzaineInterestCalculator.accrue(
                                Money.euros("10000.00"), List.of(), RATE, JAN_1, JAN_1))
                .isEqualTo(Money.ZERO_EUR);
    }

    @ParameterizedTest(name = "{0}: preceding boundary {1}, following boundary {2}")
    @CsvSource({
        "2025-01-01, 2025-01-01, 2025-01-16",
        "2025-01-15, 2025-01-01, 2025-01-16",
        "2025-01-16, 2025-01-16, 2025-02-01",
        "2025-01-31, 2025-01-16, 2025-02-01",
        "2025-12-20, 2025-12-16, 2026-01-01",
        "2024-02-29, 2024-02-16, 2024-03-01"
    })
    @DisplayName("fortnight boundaries land on the 1st and the 16th, month lengths notwithstanding")
    void computesBoundaries(LocalDate date, LocalDate preceding, LocalDate following) {
        assertThat(QuinzaineInterestCalculator.boundaryOnOrBefore(date)).isEqualTo(preceding);
        assertThat(QuinzaineInterestCalculator.nextBoundaryStrictlyAfter(date))
                .isEqualTo(following);
    }
}
