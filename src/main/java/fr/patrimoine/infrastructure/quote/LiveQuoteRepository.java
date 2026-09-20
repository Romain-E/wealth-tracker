package fr.patrimoine.infrastructure.quote;

import fr.patrimoine.application.port.out.QuoteRepository;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.infrastructure.persistence.JdbcQuoteStore;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The price lookup the application actually uses: cache, then live source, then memory.
 *
 * <p>For each request:
 *
 * <ol>
 *   <li>prices still fresh in the cache are used as they are;
 *   <li>the others go to the provider for their kind, through a retry and a circuit breaker, and
 *       whatever comes back is cached and remembered;
 *   <li>anything still missing &mdash; the provider failed, its breaker is open, or it did not know
 *       the instrument &mdash; is served from the last price ever stored, flagged stale.
 * </ol>
 *
 * <p>What it never does is fail. A provider outage turns into older numbers with a flag on them,
 * which the domain carries all the way to the API response.
 *
 * <p><b>The retry wraps the breaker, not the other way round.</b> Each attempt is then recorded by
 * the breaker, so a struggling provider is noticed sooner; and once the breaker is open, the retry
 * receives {@link CallNotPermittedException}, which it is not configured to retry, and gives up at
 * once instead of waiting to be refused again.
 *
 * <p><b>Remembering a price is a write inside a read.</b> The lookup usually runs within the
 * caller's read-only transaction, where Postgres refuses an insert. The write therefore gets a
 * transaction of its own, and a failure there is logged rather than thrown: being unable to
 * remember a price is no reason to withhold it.
 */
@Component
class LiveQuoteRepository implements QuoteRepository {

    private static final Logger log = LoggerFactory.getLogger(LiveQuoteRepository.class);

    private final List<QuoteProvider> providers;
    private final QuoteCache cache;
    private final JdbcQuoteStore store;
    private final CircuitBreakerRegistry breakers;
    private final RetryRegistry retries;
    private final TransactionTemplate ownTransaction;
    private final QuoteProperties properties;

    LiveQuoteRepository(
            List<QuoteProvider> providers,
            QuoteCache cache,
            JdbcQuoteStore store,
            CircuitBreakerRegistry breakers,
            RetryRegistry retries,
            PlatformTransactionManager transactions,
            QuoteProperties properties) {
        this.providers = List.copyOf(providers);
        this.cache = cache;
        this.store = store;
        this.breakers = breakers;
        this.retries = retries;
        this.properties = properties;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Map<InstrumentId, Quote> findLatest(Map<InstrumentId, InstrumentKind> instruments) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        if (!properties.livePrices()) {
            return remembered(instruments.keySet());
        }

        Map<InstrumentId, Quote> found = new HashMap<>(cache.getAll(instruments.keySet()));
        for (QuoteProvider provider : providers) {
            Set<InstrumentId> missing = pricedBy(provider, instruments, found.keySet());
            if (!missing.isEmpty()) {
                found.putAll(fetchLive(provider, missing));
            }
        }

        Set<InstrumentId> unpriced = new HashSet<>(instruments.keySet());
        unpriced.removeAll(found.keySet());
        found.putAll(remembered(unpriced));
        return found;
    }

    /** Fetches every given instrument from its provider, cached or not. Used by the refresher. */
    void refresh(Map<InstrumentId, InstrumentKind> instruments) {
        for (QuoteProvider provider : providers) {
            Set<InstrumentId> wanted = pricedBy(provider, instruments, Set.of());
            if (!wanted.isEmpty()) {
                fetchLive(provider, wanted);
            }
        }
    }

    private Map<InstrumentId, Quote> fetchLive(QuoteProvider provider, Set<InstrumentId> wanted) {
        Supplier<Map<InstrumentId, Quote>> guarded =
                Retry.decorateSupplier(
                        retries.retry(provider.name()),
                        CircuitBreaker.decorateSupplier(
                                breakers.circuitBreaker(provider.name()),
                                () -> provider.fetch(wanted)));
        Map<InstrumentId, Quote> fetched;
        try {
            fetched = guarded.get();
        } catch (QuoteProviderException | CallNotPermittedException unavailable) {
            log.warn(
                    "{} unavailable, serving remembered prices: {}",
                    provider.name(),
                    unavailable.getMessage());
            return Map.of();
        }
        Map<InstrumentId, Quote> usable = inAcceptedCurrency(provider, fetched);
        remember(usable.values());
        return usable;
    }

    private Map<InstrumentId, Quote> inAcceptedCurrency(
            QuoteProvider provider, Map<InstrumentId, Quote> fetched) {
        Map<InstrumentId, Quote> usable = new HashMap<>();
        fetched.forEach(
                (instrument, quote) -> {
                    if (quote.price().currency().equals(properties.currency())) {
                        usable.put(instrument, quote);
                    } else {
                        log.warn(
                                "{} priced {} in {}, which cannot be converted yet; ignoring it",
                                provider.name(),
                                instrument,
                                quote.price().currency());
                    }
                });
        return usable;
    }

    private void remember(Collection<Quote> quotes) {
        if (quotes.isEmpty()) {
            return;
        }
        cache.putAll(quotes, properties.cacheTtl());
        try {
            ownTransaction.executeWithoutResult(status -> store.saveAll(quotes));
        } catch (DataAccessException | TransactionException failure) {
            log.warn(
                    "Could not remember {} fetched prices: {}",
                    quotes.size(),
                    failure.getMessage());
        }
    }

    private Map<InstrumentId, Quote> remembered(Collection<InstrumentId> instruments) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        Map<InstrumentId, Quote> stale = new HashMap<>();
        store.findLatest(instruments).forEach((id, quote) -> stale.put(id, quote.asStale()));
        return stale;
    }

    private static Set<InstrumentId> pricedBy(
            QuoteProvider provider,
            Map<InstrumentId, InstrumentKind> instruments,
            Set<InstrumentId> alreadyPriced) {
        return instruments.entrySet().stream()
                .filter(entry -> provider.kinds().contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(instrument -> !alreadyPriced.contains(instrument))
                .collect(Collectors.toSet());
    }
}
