package fr.patrimoine.infrastructure.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Quote;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuotesHealthIndicator: degraded, never down")
class QuotesHealthIndicatorTest {

    @Mock private QuoteCache cache;

    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();

    private QuotesHealthIndicator indicator(boolean livePrices) {
        return new QuotesHealthIndicator(
                QuoteTestProperties.withLivePrices(livePrices),
                cache,
                breakers,
                List.of(new NamedProvider("yahoo"), new NamedProvider("coingecko")));
    }

    @Test
    @DisplayName("is UP when every source is closed and the cache answers")
    void upWhenEverythingIsLive() {
        when(cache.isReachable()).thenReturn(true);

        Health health = indicator(true).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("sources", Map.of("coingecko", "CLOSED", "yahoo", "CLOSED"))
                .containsEntry("cache", "UP");
    }

    @Test
    @DisplayName("is DEGRADED, naming the source, while a breaker is open")
    void degradedWhileABreakerIsOpen() {
        when(cache.isReachable()).thenReturn(true);
        breakers.circuitBreaker("yahoo").transitionToOpenState();

        Health health = indicator(true).health();

        assertThat(health.getStatus()).isEqualTo(QuotesHealthIndicator.DEGRADED);
        assertThat(health.getDetails())
                .containsEntry("sources", Map.of("coingecko", "CLOSED", "yahoo", "OPEN"));
    }

    @Test
    @DisplayName("is DEGRADED, not DOWN, when the cache does not answer")
    void degradedWithoutTheCache() {
        when(cache.isReachable()).thenReturn(false);

        Health health = indicator(true).health();

        assertThat(health.getStatus()).isEqualTo(QuotesHealthIndicator.DEGRADED);
        assertThat(health.getDetails()).containsEntry("cache", "DOWN");
    }

    @Test
    @DisplayName("is DEGRADED when live prices are switched off, without probing anything")
    void degradedWhenLivePricesAreOff() {
        Health health = indicator(false).health();

        assertThat(health.getStatus()).isEqualTo(QuotesHealthIndicator.DEGRADED);
        assertThat(health.getDetails()).containsKey("livePrices");
        verifyNoInteractions(cache);
    }

    /** Only the name matters here: it is what the breaker is looked up by. */
    private record NamedProvider(String name) implements QuoteProvider {

        @Override
        public Set<InstrumentKind> kinds() {
            return Set.of();
        }

        @Override
        public Map<InstrumentId, Quote> fetch(Set<InstrumentId> instruments) {
            return Map.of();
        }
    }
}
