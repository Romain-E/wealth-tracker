package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceSlice
@DisplayName("JdbcQuoteStore")
class JdbcQuoteStoreIT {

    private static final InstrumentId WORLD = InstrumentId.of("LU1681043599");
    private static final Instant MORNING = Instant.parse("2026-09-10T08:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-09-10T15:30:00Z");

    @Autowired private JdbcQuoteStore quotes;

    private static Quote quote(String price, Instant asOf) {
        return Quote.fresh(WORLD, Price.euros(price), asOf);
    }

    @Test
    @DisplayName("returns the stored prices it has and simply omits the instruments it does not")
    void returnsKnownPricesOnly() {
        quotes.saveAll(List.of(quote("560.12345678", EVENING)));

        Map<InstrumentId, Quote> found =
                quotes.findLatest(List.of(WORLD, InstrumentId.of("UNKNOWN")));

        assertThat(found)
                .containsOnlyKeys(WORLD)
                .containsEntry(WORLD, quote("560.12345678", EVENING));
    }

    @Test
    @DisplayName("keeps the more recent observation when two refreshes overlap")
    void neverReplacesANewerPriceWithAnOlderOne() {
        quotes.saveAll(List.of(quote("686.05", EVENING)));
        // A slower refresh finishes last with a price it observed earlier in the day.
        quotes.saveAll(List.of(quote("680.00", MORNING)));

        assertThat(quotes.findLatest(List.of(WORLD)))
                .containsEntry(WORLD, quote("686.05", EVENING));

        Instant later = EVENING.plusSeconds(3600);
        quotes.saveAll(List.of(quote("690.00", later)));
        assertThat(quotes.findLatest(List.of(WORLD))).containsEntry(WORLD, quote("690.00", later));
    }
}
