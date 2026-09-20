package fr.patrimoine.application.port.out;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Quote;
import java.util.Map;

/**
 * Driven port for the latest known price of each instrument.
 *
 * <p>Read-only and deliberately vague about where the price comes from. The adapter behind it will
 * serve from Redis, fall back to the database, and mark quotes stale when the upstream provider's
 * circuit breaker is open &mdash; but the use cases only ever see "here are the prices I could
 * find, some of them flagged as old". Pushing that decision behind the port is what lets the API
 * keep answering during a provider outage without a single conditional in the application layer.
 *
 * <p>The returned map may be missing entries. That is not an error: {@code MarketValuation} handles
 * an unpriced position by falling back to its cost basis and warning about it.
 *
 * <p>The caller passes each instrument's kind along with its id, because the kind is what decides
 * which upstream source can price it: an ISIN goes to an exchange feed, a coin to a crypto feed.
 * The caller already holds that fact on the position; making the adapter guess it from the shape of
 * an identifier would be fragile, since {@code AAPL} and {@code BTC} look alike.
 */
public interface QuoteRepository {

    Map<InstrumentId, Quote> findLatest(Map<InstrumentId, InstrumentKind> instruments);
}
