package fr.patrimoine.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A price for an instrument at a point in time.
 *
 * <p>{@code stale} is the important field. When the upstream quote provider is failing and the
 * circuit breaker is open, this application serves the last price it knows rather than refusing to
 * answer &mdash; a wealth dashboard is more useful slightly out of date than unavailable. But the
 * staleness has to be visible: it travels from the provider adapter, through valuation, into the
 * API response, so the UI can label the number instead of quietly presenting yesterday's price as
 * today's.
 *
 * @param asOf when the price was observed upstream, not when it was read from cache
 */
public record Quote(InstrumentId instrument, Price price, Instant asOf, boolean stale) {

    public Quote {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(asOf, "asOf");
    }

    public static Quote fresh(InstrumentId instrument, Price price, Instant asOf) {
        return new Quote(instrument, price, asOf, false);
    }

    /** Marks an existing quote as served from cache while the provider is unavailable. */
    public Quote asStale() {
        return stale ? this : new Quote(instrument, price, asOf, true);
    }
}
