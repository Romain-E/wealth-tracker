package fr.patrimoine.infrastructure.quote;

import java.net.URI;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for live prices, bound from {@code patrimoine.quotes}.
 *
 * @param livePrices when false, no upstream source is called and stored prices are served, flagged
 *     stale. An operational kill switch: if a provider starts rejecting this application, prices
 *     can be frozen by configuration instead of by an emergency release.
 * @param currency the only currency a price is accepted in. There is no currency conversion yet,
 *     and a dollar price added to a euro balance would fail the whole valuation; a price in another
 *     currency is therefore dropped, and the position falls back to its cost basis with a warning.
 * @param cacheTtl how long a fetched price is served from the cache before being fetched again
 */
@ConfigurationProperties("patrimoine.quotes")
public record QuoteProperties(
        boolean livePrices,
        Currency currency,
        Duration cacheTtl,
        Duration connectTimeout,
        Duration readTimeout,
        CoinGecko coingecko,
        Yahoo yahoo) {

    public QuoteProperties {
        Objects.requireNonNull(currency, "patrimoine.quotes.currency");
        Objects.requireNonNull(cacheTtl, "patrimoine.quotes.cache-ttl");
        Objects.requireNonNull(connectTimeout, "patrimoine.quotes.connect-timeout");
        Objects.requireNonNull(readTimeout, "patrimoine.quotes.read-timeout");
        Objects.requireNonNull(coingecko, "patrimoine.quotes.coingecko");
        Objects.requireNonNull(yahoo, "patrimoine.quotes.yahoo");
    }

    /**
     * @param apiKey optional; the public API answers without one, at a lower rate limit
     */
    public record CoinGecko(URI baseUrl, String apiKey) {}

    /**
     * @param userAgent sent on every call. Yahoo answers 429 to requests that carry none.
     * @param preferredListingSuffixes the exchanges to pick, in order, when an ISIN is listed in
     *     several places. Euro-area venues only, so a search never settles on a dollar listing.
     */
    public record Yahoo(URI baseUrl, String userAgent, List<String> preferredListingSuffixes) {

        public Yahoo {
            preferredListingSuffixes = List.copyOf(preferredListingSuffixes);
        }
    }
}
