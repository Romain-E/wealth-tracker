package fr.patrimoine.infrastructure.quote;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Recently fetched prices, in Redis.
 *
 * <p>Written by hand rather than with {@code @Cacheable}, for two reasons. The lookup is a batch
 * &mdash; one round trip for every instrument on the page, where the annotation caches one key per
 * call; and a cache miss here is not simply "call the method", it is "call the provider for exactly
 * the instruments that missed".
 *
 * <p><b>A cache failure is a miss, never an error.</b> Redis is an optimisation. If it is down, the
 * application fetches prices directly and keeps working, just more slowly; letting a cache outage
 * take the portfolio page down with it would make the optimisation a single point of failure.
 */
@Component
class QuoteCache {

    private static final Logger log = LoggerFactory.getLogger(QuoteCache.class);
    private static final String PREFIX = "quote:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    QuoteCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    Map<InstrumentId, Quote> getAll(Collection<InstrumentId> instruments) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        List<InstrumentId> ordered = List.copyOf(instruments);
        List<String> values;
        try {
            values = redis.opsForValue().multiGet(ordered.stream().map(QuoteCache::key).toList());
        } catch (DataAccessException unavailable) {
            log.warn("Quote cache unavailable, fetching directly: {}", unavailable.getMessage());
            return Map.of();
        }
        Map<InstrumentId, Quote> found = new HashMap<>();
        for (int i = 0; values != null && i < ordered.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                decode(ordered.get(i), value).ifPresent(q -> found.put(q.instrument(), q));
            }
        }
        return found;
    }

    /** One pipelined round trip; each entry expires on its own. */
    void putAll(Collection<Quote> quotes, Duration ttl) {
        if (quotes.isEmpty()) {
            return;
        }
        try {
            redis.executePipelined(
                    (RedisCallback<Object>)
                            connection -> {
                                for (Quote quote : quotes) {
                                    connection
                                            .stringCommands()
                                            .set(
                                                    bytes(key(quote.instrument())),
                                                    bytes(encode(quote)),
                                                    Expiration.from(ttl),
                                                    SetOption.upsert());
                                }
                                return null;
                            });
        } catch (DataAccessException unavailable) {
            log.warn("Quote cache unavailable, not caching: {}", unavailable.getMessage());
        }
    }

    /** Whether Redis answers at all. For the health endpoint, which reports it as optional. */
    boolean isReachable() {
        try {
            String reply = redis.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(reply);
        } catch (DataAccessException unavailable) {
            return false;
        }
    }

    private static String key(InstrumentId instrument) {
        return PREFIX + instrument.value();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String encode(Quote quote) {
        try {
            return json.writeValueAsString(
                    new Entry(
                            quote.price().value(),
                            quote.price().currency().getCurrencyCode(),
                            quote.asOf()));
        } catch (JacksonException impossible) {
            throw new IllegalStateException("Cannot serialise a quote", impossible);
        }
    }

    /** An unreadable entry, say from an older format, is treated as a miss and refetched. */
    private Optional<Quote> decode(InstrumentId instrument, String value) {
        Entry entry;
        try {
            entry = json.readValue(value, Entry.class);
        } catch (JacksonException unreadable) {
            entry = null;
        }
        if (entry == null || !entry.isComplete()) {
            log.warn("Ignoring unreadable cache entry for {}", instrument);
            return Optional.empty();
        }
        try {
            return Optional.of(
                    Quote.fresh(
                            instrument,
                            Price.of(entry.price(), Currency.getInstance(entry.currency())),
                            entry.asOf()));
        } catch (IllegalArgumentException unknownCurrency) {
            log.warn("Ignoring cache entry for {} in unknown currency", instrument);
            return Optional.empty();
        }
    }

    record Entry(BigDecimal price, String currency, Instant asOf) {

        boolean isComplete() {
            return price != null && currency != null && asOf != null;
        }
    }
}
