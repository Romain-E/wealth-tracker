package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Currency;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The last price ever observed for each instrument: what the application falls back to when the
 * live source cannot answer.
 *
 * <p>Not the {@code QuoteRepository} port itself. The port answers "the best price available right
 * now", a decision that involves a cache and live providers; this class only remembers. Whether a
 * remembered price counts as stale is decided by the caller, which knows why it is reading it.
 */
@Repository
public class JdbcQuoteStore {

    private static final RowMapper<Quote> QUOTE =
            (row, rowNumber) ->
                    Quote.fresh(
                            InstrumentId.of(row.getString("instrument_id")),
                            Price.of(
                                    row.getBigDecimal("price"),
                                    Currency.getInstance(row.getString("currency"))),
                            // The driver maps timestamptz to OffsetDateTime, not to Instant.
                            row.getObject("as_of", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public JdbcQuoteStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<InstrumentId, Quote> findLatest(Collection<InstrumentId> instruments) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql(
                        """
                        SELECT instrument_id, price, currency, as_of
                        FROM quote
                        WHERE instrument_id IN (:instruments)
                        """)
                .param("instruments", instruments.stream().map(InstrumentId::value).toList())
                .query(QUOTE)
                .list()
                .stream()
                .collect(Collectors.toMap(Quote::instrument, Function.identity()));
    }

    /**
     * Records newly observed prices, keeping whichever observation is more recent.
     *
     * <p>The {@code WHERE} on the update is the point. Two refreshes can overlap &mdash; the
     * scheduled one and a request that missed the cache &mdash; and without it the slower of the
     * two would replace a newer price with an older one simply by finishing last.
     */
    public void saveAll(Collection<Quote> quotes) {
        for (Quote quote : quotes) {
            jdbc.sql(
                            """
                            INSERT INTO quote (instrument_id, price, currency, as_of)
                            VALUES (:instrument, :price, :currency, :asOf)
                            ON CONFLICT (instrument_id) DO UPDATE
                            SET price = EXCLUDED.price,
                                currency = EXCLUDED.currency,
                                as_of = EXCLUDED.as_of
                            WHERE quote.as_of < EXCLUDED.as_of
                            """)
                    .param("instrument", quote.instrument().value())
                    .param("price", quote.price().value())
                    .param("currency", quote.price().currency().getCurrencyCode())
                    .param("asOf", OffsetDateTime.ofInstant(quote.asOf(), ZoneOffset.UTC))
                    .update();
        }
    }
}
