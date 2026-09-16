package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One holding, as a screen wants to see it: what it cost, what it is worth, and the difference.
 *
 * <p>Unlike {@link fr.patrimoine.domain.model.Position}, which is a domain concept, this is
 * use-case-shaped &mdash; it exists because a UI needs cost and market value side by side. That is
 * why it lives in {@code port.in} rather than in the domain.
 *
 * @param currentPrice empty when the instrument could not be priced, in which case {@code
 *     marketValue} falls back to {@code totalCost} and {@code unrealisedGain} is zero
 * @param pricedAt when the price used was observed upstream, so a UI can show its age
 */
public record PositionDetail(
        InstrumentId instrument,
        InstrumentKind kind,
        Quantity quantity,
        Price averageCost,
        Money totalCost,
        Optional<Price> currentPrice,
        Money marketValue,
        Money unrealisedGain,
        Optional<Instant> pricedAt,
        boolean stale) {

    public PositionDetail {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(averageCost, "averageCost");
        Objects.requireNonNull(totalCost, "totalCost");
        Objects.requireNonNull(currentPrice, "currentPrice");
        Objects.requireNonNull(marketValue, "marketValue");
        Objects.requireNonNull(unrealisedGain, "unrealisedGain");
        Objects.requireNonNull(pricedAt, "pricedAt");
    }

    public boolean isPriced() {
        return currentPrice.isPresent();
    }
}
