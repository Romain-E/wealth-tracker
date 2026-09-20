package fr.patrimoine.infrastructure.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.infrastructure.persistence.JdbcInstrumentSymbolStore;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Equity, ETF and fund prices from Yahoo Finance.
 *
 * <p><b>This API is unofficial.</b> It is free, needs no key and covers Euronext, which is why it
 * was chosen; it can also change shape or start refusing requests without notice. Everything around
 * this class &mdash; timeouts, retry, circuit breaker, the cache and the stored fallback &mdash;
 * exists so that when that happens the application keeps answering with the last known prices,
 * flagged as such, instead of failing.
 *
 * <p><b>ISINs are translated to listings.</b> Yahoo prices a listing such as {@code CW8.PA}, not an
 * ISIN. The translation is looked up once, on a euro-area exchange, and then remembered.
 *
 * <p><b>One request per instrument, run concurrently on virtual threads.</b> The endpoint that
 * prices several symbols at once requires a session cookie; the chart endpoint does not, but takes
 * a single symbol. Virtual threads make that fan-out nearly free, since each lookup spends its
 * whole life waiting on the network.
 */
@Component
class YahooFinanceQuoteProvider implements QuoteProvider, DisposableBean {

    static final String NAME = "yahoo";
    static final String SYMBOL_DIRECTORY = "YAHOO";

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceQuoteProvider.class);
    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final RestClient http;
    private final JdbcInstrumentSymbolStore symbols;
    private final List<String> preferredListings;
    private final Clock clock;
    private final ExecutorService lookups = Executors.newVirtualThreadPerTaskExecutor();

    YahooFinanceQuoteProvider(
            RestClient.Builder builder,
            QuoteProperties properties,
            JdbcInstrumentSymbolStore symbols,
            Clock clock) {
        this.http =
                builder.baseUrl(properties.yahoo().baseUrl().toString())
                        .requestFactory(QuoteConfiguration.requestFactory(properties))
                        .defaultHeader(HttpHeaders.USER_AGENT, properties.yahoo().userAgent())
                        .build();
        this.symbols = symbols;
        this.preferredListings = properties.yahoo().preferredListingSuffixes();
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<InstrumentKind> kinds() {
        return Set.of(InstrumentKind.EQUITY, InstrumentKind.ETF, InstrumentKind.FUND);
    }

    /**
     * Prices every instrument concurrently. If any lookup finds Yahoo itself failing, the whole
     * call fails: a source that is down for one symbol is down, and the breaker should hear so once
     * rather than be told the call half succeeded.
     */
    @Override
    public Map<InstrumentId, Quote> fetch(Set<InstrumentId> instruments) {
        List<Map.Entry<InstrumentId, Future<Optional<Quote>>>> pending =
                instruments.stream()
                        .map(id -> Map.entry(id, lookups.submit(() -> lookUp(id))))
                        .toList();
        try {
            Map<InstrumentId, Quote> quotes = new HashMap<>();
            for (Map.Entry<InstrumentId, Future<Optional<Quote>>> lookup : pending) {
                lookup.getValue().get().ifPresent(quote -> quotes.put(lookup.getKey(), quote));
            }
            return quotes;
        } catch (ExecutionException failed) {
            throw failed.getCause() instanceof QuoteProviderException providerFailure
                    ? providerFailure
                    : new QuoteProviderException("Yahoo Finance lookup failed", failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new QuoteProviderException("Interrupted waiting for Yahoo Finance", interrupted);
        } finally {
            // After a failure the remaining lookups are pointless; finished ones ignore this.
            pending.forEach(lookup -> lookup.getValue().cancel(true));
        }
    }

    private Optional<Quote> lookUp(InstrumentId instrument) {
        return symbolFor(instrument).flatMap(symbol -> chart(instrument, symbol));
    }

    /** An ISIN is translated; anything else is taken to be a Yahoo symbol already. */
    private Optional<String> symbolFor(InstrumentId instrument) {
        if (!ISIN.matcher(instrument.value()).matches()) {
            return Optional.of(instrument.value());
        }
        Optional<String> known = symbols.find(instrument, SYMBOL_DIRECTORY);
        if (known.isPresent()) {
            return known;
        }
        Optional<String> found = searchListing(instrument);
        found.ifPresentOrElse(
                symbol -> symbols.remember(instrument, SYMBOL_DIRECTORY, symbol),
                () ->
                        log.warn(
                                "No euro-area listing found for {}; it stays unpriced until a"
                                        + " curated row is added to instrument_symbol",
                                instrument));
        return found;
    }

    /**
     * Picks a listing on a preferred exchange, in preference order. A listing elsewhere is not a
     * fallback: it would be priced in another currency, and a wrong price is worse than none.
     */
    private Optional<String> searchListing(InstrumentId isin) {
        JsonNode result =
                get(
                        uri ->
                                uri.path("/v1/finance/search")
                                        .queryParam("q", isin.value())
                                        .queryParam("quotesCount", 10)
                                        .queryParam("newsCount", 0)
                                        .build());
        List<String> listings = new ArrayList<>();
        result.path("quotes")
                .forEach(
                        candidate -> {
                            String symbol = candidate.path("symbol").asText("");
                            if (!symbol.isBlank()) {
                                listings.add(symbol);
                            }
                        });
        for (String exchange : preferredListings) {
            for (String symbol : listings) {
                if (symbol.endsWith(exchange)) {
                    return Optional.of(symbol);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Quote> chart(InstrumentId instrument, String symbol) {
        JsonNode meta =
                get(uri ->
                                uri.path("/v8/finance/chart/{symbol}")
                                        .queryParam("range", "1d")
                                        .queryParam("interval", "1d")
                                        .build(symbol))
                        .path("chart")
                        .path("result")
                        .path(0)
                        .path("meta");
        JsonNode price = meta.path("regularMarketPrice");
        if (!price.isNumber()) {
            return Optional.empty();
        }
        String quotedIn = meta.path("currency").asText("");
        Currency currency;
        try {
            currency = Currency.getInstance(quotedIn);
        } catch (IllegalArgumentException notIso) {
            // London quotes in pence, as "GBp", which is not a currency code.
            log.warn("{} is quoted in '{}', which is not an ISO currency", symbol, quotedIn);
            return Optional.empty();
        }
        JsonNode time = meta.path("regularMarketTime");
        Instant observed = time.isNumber() ? Instant.ofEpochSecond(time.asLong()) : clock.instant();
        return Optional.of(
                Quote.fresh(instrument, Price.of(price.decimalValue(), currency), observed));
    }

    /**
     * A missing node when Yahoo says it does not know what was asked for; an exception when Yahoo
     * itself cannot answer. Only the second counts against its health.
     */
    private JsonNode get(Function<UriBuilder, URI> uri) {
        try {
            JsonNode body = http.get().uri(uri).retrieve().body(JsonNode.class);
            return body == null ? MissingNode.getInstance() : body;
        } catch (HttpClientErrorException.NotFound unknown) {
            return MissingNode.getInstance();
        } catch (RestClientException failure) {
            throw new QuoteProviderException("Yahoo Finance did not answer", failure);
        }
    }

    @Override
    public void destroy() {
        lookups.shutdownNow();
    }
}
