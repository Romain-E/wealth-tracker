package fr.patrimoine.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Position: weighted average cost (prix de revient moyen pondere)")
class PositionTest {

    private static final InstrumentId TOTAL = InstrumentId.of("FR0000120271");
    private static final Instant NOW = Instant.parse("2026-09-10T09:00:00Z");

    @Test
    @DisplayName("a first lot costs its all-in unit price, fees included")
    void openingLotFoldsInFees() {
        Position position =
                Position.opening(
                        TOTAL,
                        InstrumentKind.EQUITY,
                        Quantity.of(100),
                        Price.euros("58.00"),
                        Money.euros("4.90"));

        assertThat(position.averageCost().value()).isEqualByComparingTo("58.049");
        assertThat(position.totalCost()).isEqualTo(Money.euros("5804.90"));
    }

    @Test
    @DisplayName("a second lot re-averages the cost over the whole holding")
    void addingALotReAveragesTheCost() {
        Position position =
                Position.opening(
                        TOTAL,
                        InstrumentKind.EQUITY,
                        Quantity.of(100),
                        Price.euros("50.00"),
                        Money.ZERO_EUR);

        Position after = position.addLot(Quantity.of(100), Price.euros("70.00"), Money.ZERO_EUR);

        // 100 @ 50 + 100 @ 70 = 12 000 over 200 units.
        assertThat(after.quantity()).isEqualTo(Quantity.of(200));
        assertThat(after.averageCost().value()).isEqualByComparingTo("60");
        assertThat(after.totalCost()).isEqualTo(Money.euros("12000.00"));
    }

    @Test
    @DisplayName(
            "uneven lots average by value, not by price: 300 @ 10 then 100 @ 20 is 12.50, not 15")
    void averagesByValueNotByPrice() {
        Position position =
                Position.opening(
                        TOTAL,
                        InstrumentKind.EQUITY,
                        Quantity.of(300),
                        Price.euros("10.00"),
                        Money.ZERO_EUR);

        Position after = position.addLot(Quantity.of(100), Price.euros("20.00"), Money.ZERO_EUR);

        assertThat(after.averageCost().value()).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("a sale does not move the average cost, it only reduces the quantity")
    void sellingLeavesTheAverageCostAlone() {
        Position position =
                Position.opening(
                        TOTAL,
                        InstrumentKind.EQUITY,
                        Quantity.of(200),
                        Price.euros("60.00"),
                        Money.ZERO_EUR);

        Position after = position.reduceBy(Quantity.of(150)).orElseThrow();

        assertThat(after.quantity()).isEqualTo(Quantity.of(50));
        assertThat(after.averageCost().value()).isEqualByComparingTo("60");
        assertThat(after.totalCost()).isEqualTo(Money.euros("3000.00"));
    }

    @Test
    void closingThePositionEntirelyYieldsNothing() {
        Position position =
                Position.opening(
                        TOTAL,
                        InstrumentKind.EQUITY,
                        Quantity.of(200),
                        Price.euros("60.00"),
                        Money.ZERO_EUR);

        assertThat(position.reduceBy(Quantity.of(200))).isEmpty();
    }

    @Test
    void marksToMarketAndReportsTheUnrealisedGain() {
        Position position =
                Position.opening(
                        TOTAL,
                        InstrumentKind.EQUITY,
                        Quantity.of(100),
                        Price.euros("50.00"),
                        Money.ZERO_EUR);
        Quote quote = Quote.fresh(TOTAL, Price.euros("58.42"), NOW);

        assertThat(position.marketValue(quote)).isEqualTo(Money.euros("5842.00"));
        assertThat(position.unrealisedGain(quote)).isEqualTo(Money.euros("842.00"));
    }

    @Test
    @DisplayName("refuses to be valued by a quote for a different instrument")
    void refusesAMismatchedQuote() {
        Position position =
                Position.opening(
                        TOTAL,
                        InstrumentKind.EQUITY,
                        Quantity.of(100),
                        Price.euros("50.00"),
                        Money.ZERO_EUR);
        Quote wrong = Quote.fresh(InstrumentId.of("BITCOIN"), Price.euros("50000.00"), NOW);

        assertThatIllegalArgumentException().isThrownBy(() -> position.marketValue(wrong));
    }

    @Test
    @DisplayName("a zero-quantity position is unrepresentable")
    void rejectsAZeroQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new Position(
                                        TOTAL,
                                        InstrumentKind.EQUITY,
                                        Quantity.ZERO,
                                        Price.euros("50.00")));
    }
}
