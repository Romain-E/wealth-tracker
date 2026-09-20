package fr.patrimoine.infrastructure.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.infrastructure.persistence.JdbcQuoteStore;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiveQuoteRepository: cache, then live source, then memory")
class LiveQuoteRepositoryTest {

    private static final InstrumentId WORLD = InstrumentId.of("LU1681043599");
    private static final InstrumentId BITCOIN = InstrumentId.of("BITCOIN");
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final Instant YESTERDAY = NOW.minus(Duration.ofDays(1));

    @Mock private QuoteCache cache;
    @Mock private JdbcQuoteStore store;
    @Mock private PlatformTransactionManager transactions;

    private final FakeProvider exchange =
            new FakeProvider(
                    "yahoo",
                    Set.of(InstrumentKind.EQUITY, InstrumentKind.ETF, InstrumentKind.FUND));
    private final FakeProvider crypto =
            new FakeProvider("coingecko", Set.of(InstrumentKind.CRYPTO));

    /** Small windows, so a test can open a breaker in a handful of calls. */
    private LiveQuoteRepository repository(boolean livePrices) {
        CircuitBreakerRegistry breakers =
                CircuitBreakerRegistry.of(
                        CircuitBreakerConfig.custom()
                                .slidingWindowSize(4)
                                .minimumNumberOfCalls(4)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofMinutes(1))
                                .recordExceptions(QuoteProviderException.class)
                                .build());
        RetryRegistry retries =
                RetryRegistry.of(
                        RetryConfig.custom()
                                .maxAttempts(2)
                                .waitDuration(Duration.ofMillis(1))
                                .retryExceptions(QuoteProviderException.class)
                                .build());
        return new LiveQuoteRepository(
                List.of(exchange, crypto),
                cache,
                store,
                breakers,
                retries,
                transactions,
                QuoteTestProperties.withLivePrices(livePrices));
    }

    private static Quote euros(InstrumentId instrument, String price, Instant asOf) {
        return Quote.fresh(instrument, Price.euros(price), asOf);
    }

    @Test
    @DisplayName("a cached price is served without calling anyone")
    void servesFromTheCache() {
        when(cache.getAll(any())).thenReturn(Map.of(WORLD, euros(WORLD, "686.05", NOW)));

        Map<InstrumentId, Quote> quotes =
                repository(true).findLatest(Map.of(WORLD, InstrumentKind.ETF));

        assertThat(quotes).containsEntry(WORLD, euros(WORLD, "686.05", NOW));
        assertThat(exchange.calls).isEmpty();
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("a miss goes to the provider for that kind, and the answer is cached and stored")
    void routesMissesByKindAndRemembersTheAnswer() {
        when(cache.getAll(any())).thenReturn(Map.of());
        exchange.answer = Map.of(WORLD, euros(WORLD, "686.05", NOW));
        crypto.answer = Map.of(BITCOIN, euros(BITCOIN, "65628", NOW));

        Map<InstrumentId, Quote> quotes =
                repository(true)
                        .findLatest(
                                Map.of(WORLD, InstrumentKind.ETF, BITCOIN, InstrumentKind.CRYPTO));

        assertThat(quotes)
                .containsOnly(
                        Map.entry(WORLD, euros(WORLD, "686.05", NOW)),
                        Map.entry(BITCOIN, euros(BITCOIN, "65628", NOW)));
        assertThat(exchange.calls).containsExactly(Set.of(WORLD));
        assertThat(crypto.calls).containsExactly(Set.of(BITCOIN));
        verify(cache, times(2)).putAll(anyCollection(), any(Duration.class));
        verify(store, times(2)).saveAll(anyCollection());
    }

    @Test
    @DisplayName("while a provider is down, the last stored price is served, flagged stale")
    void fallsBackToTheLastStoredPrice() {
        when(cache.getAll(any())).thenReturn(Map.of());
        exchange.failing = true;
        when(store.findLatest(Set.of(WORLD)))
                .thenReturn(Map.of(WORLD, euros(WORLD, "560.00", YESTERDAY)));

        Quote quote = repository(true).findLatest(Map.of(WORLD, InstrumentKind.ETF)).get(WORLD);

        assertThat(quote.stale()).isTrue();
        assertThat(quote.price()).isEqualTo(Price.euros("560.00"));
        assertThat(quote.asOf()).isEqualTo(YESTERDAY);
        // One retry, then the fallback.
        assertThat(exchange.calls).hasSize(2);
    }

    @Test
    @DisplayName("once the breaker opens, the failing provider is no longer called at all")
    void stopsCallingAProviderOnceTheBreakerOpens() {
        when(cache.getAll(any())).thenReturn(Map.of());
        when(store.findLatest(any())).thenReturn(Map.of());
        exchange.failing = true;
        LiveQuoteRepository repository = repository(true);

        // Two lookups, two attempts each: four failures in a window of four opens the breaker.
        repository.findLatest(Map.of(WORLD, InstrumentKind.ETF));
        repository.findLatest(Map.of(WORLD, InstrumentKind.ETF));
        assertThat(exchange.calls).hasSize(4);

        repository.findLatest(Map.of(WORLD, InstrumentKind.ETF));
        repository.findLatest(Map.of(WORLD, InstrumentKind.ETF));

        // Refused by the breaker without a call, and not retried either.
        assertThat(exchange.calls).hasSize(4);
    }

    @Test
    @DisplayName("an outage at one provider does not stop the other")
    void isolatesProvidersFromEachOther() {
        when(cache.getAll(any())).thenReturn(Map.of());
        exchange.failing = true;
        crypto.answer = Map.of(BITCOIN, euros(BITCOIN, "65628", NOW));
        when(store.findLatest(Set.of(WORLD))).thenReturn(Map.of());

        Map<InstrumentId, Quote> quotes =
                repository(true)
                        .findLatest(
                                Map.of(WORLD, InstrumentKind.ETF, BITCOIN, InstrumentKind.CRYPTO));

        assertThat(quotes).containsOnly(Map.entry(BITCOIN, euros(BITCOIN, "65628", NOW)));
    }

    @Test
    @DisplayName("a price in a currency that cannot be converted is ignored, not added to euros")
    void ignoresPricesInAnotherCurrency() {
        when(cache.getAll(any())).thenReturn(Map.of());
        exchange.answer =
                Map.of(
                        WORLD,
                        Quote.fresh(
                                WORLD,
                                Price.of(new BigDecimal("145.59"), Currency.getInstance("USD")),
                                NOW));
        when(store.findLatest(Set.of(WORLD)))
                .thenReturn(Map.of(WORLD, euros(WORLD, "560.00", YESTERDAY)));

        Quote quote = repository(true).findLatest(Map.of(WORLD, InstrumentKind.ETF)).get(WORLD);

        assertThat(quote.price()).isEqualTo(Price.euros("560.00"));
        assertThat(quote.stale()).isTrue();
        verify(store, never()).saveAll(anyCollection());
    }

    @Test
    @DisplayName("with live prices switched off, only stored prices are served, all flagged stale")
    void servesStoredPricesWhenLivePricesAreOff() {
        when(store.findLatest(Set.of(WORLD)))
                .thenReturn(Map.of(WORLD, euros(WORLD, "560.00", YESTERDAY)));

        Quote quote = repository(false).findLatest(Map.of(WORLD, InstrumentKind.ETF)).get(WORLD);

        assertThat(quote.stale()).isTrue();
        assertThat(exchange.calls).isEmpty();
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("failing to store a fetched price does not withhold it")
    void servesAFetchedPriceEvenIfItCannotBeStored() {
        when(cache.getAll(any())).thenReturn(Map.of());
        exchange.answer = Map.of(WORLD, euros(WORLD, "686.05", NOW));
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(store)
                .saveAll(anyCollection());

        Map<InstrumentId, Quote> quotes =
                repository(true).findLatest(Map.of(WORLD, InstrumentKind.ETF));

        assertThat(quotes).containsEntry(WORLD, euros(WORLD, "686.05", NOW));
    }

    @Test
    @DisplayName("asks nobody anything when nothing is held")
    void doesNothingForAnEmptyRequest() {
        assertThat(repository(true).findLatest(Map.of())).isEmpty();
        verifyNoInteractions(cache, store);
    }

    /** A provider whose answers and health the test controls, and which records every call. */
    private static final class FakeProvider implements QuoteProvider {

        private final String name;
        private final Set<InstrumentKind> kinds;
        private final List<Set<InstrumentId>> calls = new ArrayList<>();
        private Map<InstrumentId, Quote> answer = Map.of();
        private boolean failing;

        private FakeProvider(String name, Set<InstrumentKind> kinds) {
            this.name = name;
            this.kinds = kinds;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Set<InstrumentKind> kinds() {
            return kinds;
        }

        @Override
        public Map<InstrumentId, Quote> fetch(Set<InstrumentId> instruments) {
            calls.add(Set.copyOf(instruments));
            if (failing) {
                throw new QuoteProviderException(name + " is down", null);
            }
            return answer;
        }
    }
}
