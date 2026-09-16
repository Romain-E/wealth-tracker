package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@PersistenceSlice
@DisplayName("JdbcQuoteRepository")
class JdbcQuoteRepositoryIT {

    private static final InstrumentId WORLD = InstrumentId.of("LU1681043599");

    @Autowired private JdbcQuoteRepository quotes;
    @Autowired private JdbcClient jdbc;

    @Test
    @DisplayName("returns the stored prices it has and simply omits the instruments it does not")
    void returnsKnownPricesOnly() {
        jdbc.sql(
                        """
                        INSERT INTO quote (instrument_id, price, currency, as_of)
                        VALUES ('LU1681043599', 560.12345678, 'EUR', :asOf)
                        """)
                .param("asOf", OffsetDateTime.parse("2026-09-10T15:30:00Z"))
                .update();

        Map<InstrumentId, Quote> found =
                quotes.findLatest(List.of(WORLD, InstrumentId.of("UNKNOWN")));

        assertThat(found)
                .containsOnlyKeys(WORLD)
                .containsEntry(
                        WORLD,
                        Quote.fresh(
                                WORLD,
                                Price.euros("560.12345678"),
                                Instant.parse("2026-09-10T15:30:00Z")));
    }
}
