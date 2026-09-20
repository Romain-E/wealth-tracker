package fr.patrimoine.infrastructure.quote;

import java.net.URI;
import java.time.Duration;
import java.util.Currency;
import java.util.List;

/** Settings for tests: short timeouts, and upstream sources wherever the test says. */
final class QuoteTestProperties {

    static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private QuoteTestProperties() {}

    static QuoteProperties pointingAt(String coinGeckoUrl, String coinGeckoKey, String yahooUrl) {
        return build(true, coinGeckoUrl, coinGeckoKey, yahooUrl);
    }

    static QuoteProperties withLivePrices(boolean live) {
        return build(live, "http://localhost:1", "", "http://localhost:1");
    }

    private static QuoteProperties build(
            boolean live, String coinGeckoUrl, String coinGeckoKey, String yahooUrl) {
        return new QuoteProperties(
                live,
                Currency.getInstance("EUR"),
                CACHE_TTL,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                new QuoteProperties.CoinGecko(URI.create(coinGeckoUrl), coinGeckoKey),
                new QuoteProperties.Yahoo(
                        URI.create(yahooUrl), "patrimoine-test", List.of(".PA", ".AS", ".DE")));
    }
}
