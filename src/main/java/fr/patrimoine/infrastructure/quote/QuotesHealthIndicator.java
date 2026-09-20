package fr.patrimoine.infrastructure.quote;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * How live the prices are: the state of each price source's circuit breaker, and whether the cache
 * answers.
 *
 * <p>It never reports {@code DOWN}, only {@code UP} or {@code DEGRADED}. None of what it watches
 * can stop the application from answering: an open breaker means stored prices are served, a dead
 * cache means prices are fetched directly. Reporting either as {@code DOWN} would describe a
 * working application as broken, and anything acting on that status &mdash; an alert, a load
 * balancer &mdash; would act wrongly. {@code DEGRADED} is mapped to HTTP 200 and ranked between
 * {@code UP} and {@code DOWN} in {@code application.yml}.
 *
 * <p>For the same reason it is not part of the readiness group: an outage at Yahoo must not take
 * every pod out of rotation.
 */
@Component
class QuotesHealthIndicator implements HealthIndicator {

    static final Status DEGRADED =
            new Status("DEGRADED", "Serving, with stale prices or without the price cache");

    private final QuoteProperties properties;
    private final QuoteCache cache;
    private final CircuitBreakerRegistry breakers;
    private final List<QuoteProvider> providers;

    QuotesHealthIndicator(
            QuoteProperties properties,
            QuoteCache cache,
            CircuitBreakerRegistry breakers,
            List<QuoteProvider> providers) {
        this.properties = properties;
        this.cache = cache;
        this.breakers = breakers;
        this.providers = List.copyOf(providers);
    }

    @Override
    public Health health() {
        if (!properties.livePrices()) {
            return Health.status(DEGRADED)
                    .withDetail("livePrices", "switched off: stored prices only")
                    .build();
        }

        Map<String, String> sources = new TreeMap<>();
        boolean allClosed = true;
        for (QuoteProvider provider : providers) {
            CircuitBreaker.State state = breakers.circuitBreaker(provider.name()).getState();
            sources.put(provider.name(), state.name());
            allClosed &= state == CircuitBreaker.State.CLOSED;
        }
        boolean cacheUp = cache.isReachable();

        Health.Builder health = allClosed && cacheUp ? Health.up() : Health.status(DEGRADED);
        return health.withDetail("sources", sources)
                .withDetail("cache", cacheUp ? "UP" : "DOWN")
                .build();
    }
}
