package fr.patrimoine.domain.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import fr.patrimoine.domain.model.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Xirr: money-weighted annualised return")
class XirrTest {

    private static CashFlow flow(String date, String amount) {
        return new CashFlow(LocalDate.parse(date), Money.euros(amount));
    }

    @Nested
    @DisplayName("known-answer tests")
    class KnownAnswers {

        @Test
        @DisplayName("reproduces the reference XIRR value from Microsoft's own worked example")
        void matchesTheReferenceImplementation() {
            // The canonical example shipped in Excel's XIRR documentation. Pinning against an
            // independent implementation is the only honest way to test a numerical solver:
            // asserting on the output of the code under test proves nothing.
            List<CashFlow> flows =
                    List.of(
                            flow("2008-01-01", "-10000.00"),
                            flow("2008-03-01", "2750.00"),
                            flow("2008-10-30", "4250.00"),
                            flow("2009-02-15", "3250.00"),
                            flow("2009-04-01", "2750.00"));

            OptionalDouble rate = Xirr.solve(flows);

            assertThat(rate).isPresent();
            assertThat(rate.getAsDouble()).isCloseTo(0.373362535, within(1e-7));
        }

        @Test
        @DisplayName("annualises correctly across a leap year: 1 000 to 1 100 over 366 days")
        void annualisesOverALeapYear() {
            OptionalDouble rate =
                    Xirr.solve(
                            List.of(flow("2020-01-01", "-1000.00"), flow("2021-01-01", "1100.00")));

            // Not exactly 10%: 366 days is slightly more than one ACT/365 year.
            assertThat(rate.getAsDouble()).isCloseTo(0.0997136, within(1e-6));
        }

        @Test
        @DisplayName("weights by time, so a late deposit does not dilute an early one")
        void weightsCashFlowsByHowLongTheyWereInvested() {
            OptionalDouble rate =
                    Xirr.solve(
                            List.of(
                                    flow("2020-01-01", "-10000.00"),
                                    flow("2021-01-01", "-5000.00"),
                                    flow("2025-01-01", "22000.00")));

            assertThat(rate.getAsDouble()).isCloseTo(0.0852723, within(1e-6));
        }

        @Test
        @DisplayName("a loss produces a negative rate")
        void handlesLosses() {
            OptionalDouble rate =
                    Xirr.solve(
                            List.of(
                                    flow("2024-01-01", "-10000.00"),
                                    flow("2025-01-01", "7500.00")));

            assertThat(rate.getAsDouble()).isCloseTo(-0.2494103, within(1e-6));
            assertThat(rate.getAsDouble()).isNegative();
        }

        @Test
        @DisplayName("a near-total wipeout converges to nearly -100% instead of diverging")
        void handlesANearTotalWipeout() {
            OptionalDouble rate =
                    Xirr.solve(
                            List.of(flow("2020-01-01", "-10000.00"), flow("2021-01-01", "1.00")));

            assertThat(rate).isPresent();
            assertThat(rate.getAsDouble()).isCloseTo(-0.9998974, within(1e-5));
            assertThat(Double.isFinite(rate.getAsDouble())).isTrue();
        }
    }

    @Nested
    @DisplayName("series with no solution")
    class NoSolution {

        @Test
        @DisplayName("a single cash flow has no rate of return")
        void rejectsASingleFlow() {
            assertThat(Xirr.solve(List.of(flow("2024-01-01", "-10000.00")))).isEmpty();
            assertThat(Xirr.solve(List.of())).isEmpty();
        }

        @Test
        @DisplayName("money that only ever went one way has no rate of return")
        void rejectsFlowsThatNeverChangeSign() {
            assertThat(
                            Xirr.solve(
                                    List.of(
                                            flow("2024-01-01", "-1000.00"),
                                            flow("2025-01-01", "-1000.00"))))
                    .isEmpty();
            assertThat(
                            Xirr.solve(
                                    List.of(
                                            flow("2024-01-01", "1000.00"),
                                            flow("2025-01-01", "1000.00"))))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("numerical robustness")
    class Robustness {

        @Test
        @DisplayName(
                "accepts flows in any order, since a transaction list is not sorted by default")
        void isOrderIndependent() {
            List<CashFlow> ordered =
                    List.of(
                            flow("2020-01-01", "-10000.00"),
                            flow("2022-06-15", "-2000.00"),
                            flow("2025-01-01", "15000.00"));
            List<CashFlow> shuffled =
                    List.of(
                            flow("2025-01-01", "15000.00"),
                            flow("2020-01-01", "-10000.00"),
                            flow("2022-06-15", "-2000.00"));

            assertThat(Xirr.solve(shuffled).getAsDouble())
                    .isCloseTo(Xirr.solve(ordered).getAsDouble(), within(1e-9));
        }

        @Test
        @DisplayName("survives a very large, very irregular series without diverging")
        void survivesAManyFlowSeries() {
            List<CashFlow> flows = new java.util.ArrayList<>();
            LocalDate date = LocalDate.of(2015, 1, 5);
            for (int i = 0; i < 120; i++) {
                flows.add(new CashFlow(date, Money.euros("-500.00")));
                date = date.plusMonths(1).plusDays(i % 7 == 0 ? 3 : 0);
            }
            flows.add(new CashFlow(date, Money.euros("92000.00")));

            OptionalDouble rate = Xirr.solve(flows);

            assertThat(rate).isPresent();
            assertThat(Double.isFinite(rate.getAsDouble())).isTrue();
            assertThat(rate.getAsDouble()).isBetween(0.05, 0.12);
        }

        @Test
        @DisplayName("the NPV really is zero at the returned rate, which is the definition")
        void theSolutionSatisfiesTheEquation() {
            List<CashFlow> flows =
                    List.of(
                            flow("2020-01-01", "-10000.00"),
                            flow("2022-06-15", "-2000.00"),
                            flow("2025-01-01", "15000.00"));

            double rate = Xirr.solve(flows).getAsDouble();

            assertThat(Xirr.npv(flows, LocalDate.of(2020, 1, 1), rate))
                    .isCloseTo(0.0, within(1e-6));
        }

        @Test
        @DisplayName("the analytic derivative agrees with a finite difference")
        void derivativeMatchesAFiniteDifference() {
            List<CashFlow> flows =
                    List.of(flow("2020-01-01", "-10000.00"), flow("2025-01-01", "15000.00"));
            LocalDate origin = LocalDate.of(2020, 1, 1);
            double rate = 0.07;
            double h = 1e-6;

            double numeric =
                    (Xirr.npv(flows, origin, rate + h) - Xirr.npv(flows, origin, rate - h))
                            / (2 * h);

            assertThat(Xirr.npvDerivative(flows, origin, rate)).isCloseTo(numeric, within(1e-3));
        }
    }
}
