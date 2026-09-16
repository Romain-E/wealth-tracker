package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.application.port.out.QuoteRepository;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Currency;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC adapter for {@link QuoteRepository}: the last price stored for each instrument.
 *
 * <p>Every quote comes back unflagged. Whether a stored price should be labelled stale depends on
 * whether the upstream provider is currently failing, and the database is the one component that
 * cannot know that.
 */
@Repository
public class JdbcQuoteRepository implements QuoteRepository {

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

    public JdbcQuoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
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
}
