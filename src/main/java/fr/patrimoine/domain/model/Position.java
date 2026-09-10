package fr.patrimoine.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A holding of one instrument inside one account.
 *
 * <p>The cost basis is a <b>weighted average</b> (in French tax terms, the <i>prix de revient moyen
 * pondere</i>). That is not an arbitrary choice: it is the method the French tax authority requires
 * for securities held in a compte-titres, so FIFO or specific-lot accounting would give a number
 * that does not match the user's tax return. Each purchase re-averages; a sale does not move the
 * average at all, it only reduces the quantity.
 *
 * <p>Dealing fees are folded into the cost basis rather than expensed, again matching the tax
 * treatment: they increase the acquisition price and therefore reduce the eventual capital gain.
 */
public record Position(
        InstrumentId instrument, InstrumentKind kind, Quantity quantity, Price averageCost) {

    public Position {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(averageCost, "averageCost");
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException(
                    "A position must hold a positive quantity; remove it instead of holding zero");
        }
    }

    /** Opening lot: the average cost of a first purchase is simply its all-in unit price. */
    public static Position opening(
            InstrumentId instrument,
            InstrumentKind kind,
            Quantity quantity,
            Price unitPrice,
            Money fees) {
        BigDecimal gross = unitPrice.times(quantity).plus(fees).amount();
        return new Position(
                instrument, kind, quantity, Price.of(quantity.spread(gross), unitPrice.currency()));
    }

    /** What was actually paid for the units currently held. */
    public Money totalCost() {
        return averageCost.times(quantity);
    }

    public Money marketValue(Quote quote) {
        requireMatching(quote);
        return quote.price().times(quantity);
    }

    public Money unrealisedGain(Quote quote) {
        return marketValue(quote).minus(totalCost());
    }

    /** Adds a lot and re-averages the cost basis over the new total quantity. */
    public Position addLot(Quantity added, Price unitPrice, Money fees) {
        if (!added.isPositive()) {
            throw new IllegalArgumentException("A purchase must add a positive quantity");
        }
        Quantity newQuantity = quantity.plus(added);
        Money newTotalCost = totalCost().plus(unitPrice.times(added)).plus(fees);
        return new Position(
                instrument,
                kind,
                newQuantity,
                Price.of(newQuantity.spread(newTotalCost.amount()), averageCost.currency()));
    }

    /**
     * Removes units, leaving the average cost untouched. Empty when the position is fully closed,
     * which the aggregate turns into a removal from its map. Optional rather than a null return so
     * that "no position left" is a state the caller has to handle rather than trip over.
     */
    public Optional<Position> reduceBy(Quantity sold) {
        Quantity remaining = quantity.minus(sold);
        return remaining.isZero()
                ? Optional.empty()
                : Optional.of(new Position(instrument, kind, remaining, averageCost));
    }

    private void requireMatching(Quote quote) {
        if (!quote.instrument().equals(instrument)) {
            throw new IllegalArgumentException(
                    "Quote for %s cannot value a position in %s"
                            .formatted(quote.instrument(), instrument));
        }
    }
}
