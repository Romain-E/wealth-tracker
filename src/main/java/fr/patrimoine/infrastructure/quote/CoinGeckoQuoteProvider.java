package fr.patrimoine.infrastructure.quote;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Crypto prices from CoinGecko's public API.
 *
 * <p>One request prices every coin at once, which is why this provider has no fan-out: the {@code
 * simple/price} endpoint takes a comma-separated list. Coins it does not know are simply missing
 * from its answer.
 */
@Component
class CoinGeckoQuoteProvider implements QuoteProvider {

    static final String NAME = "coingecko";

    private static final String API_KEY_HEADER = "x-cg-demo-api-key";

    private final RestClient http;
    private final Currency currency;
    private final String vsCurrency;
    private final Clock clock;

    CoinGeckoQuoteProvider(RestClient.Builder builder, QuoteProperties properties, Clock clock) {
        RestClient.Builder configured =
                builder.baseUrl(properties.coingecko().baseUrl().toString())
                        .requestFactory(QuoteConfiguration.requestFactory(properties));
        String apiKey = properties.coingecko().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            configured.defaultHeader(API_KEY_HEADER, apiKey);
        }
        this.http = configured.build();
        this.currency = properties.currency();
        this.vsCurrency = currency.getCurrencyCode().toLowerCase(Locale.ROOT);
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<InstrumentKind> kinds() {
        return Set.of(InstrumentKind.CRYPTO);
    }

    @Override
    public Map<InstrumentId, Quote> fetch(Set<InstrumentId> instruments) {
        // CoinGecko ids are lower case ("bitcoin") while InstrumentId upper-cases everything, so
        // the conversion happens here, at the one edge that has the quirk. Sorted, so the same set
        // of coins always produces the same request.
        Map<String, InstrumentId> byCoin = new TreeMap<>();
        instruments.forEach(id -> byCoin.put(id.value().toLowerCase(Locale.ROOT), id));

        JsonNode prices = request(String.join(",", byCoin.keySet()));

        Map<InstrumentId, Quote> quotes = new HashMap<>();
        byCoin.forEach(
                (coin, instrument) -> {
                    JsonNode entry = prices.path(coin);
                    JsonNode price = entry.path(vsCurrency);
                    if (price.isNumber()) {
                        quotes.put(
                                instrument,
                                Quote.fresh(
                                        instrument,
                                        Price.of(price.decimalValue(), currency),
                                        observedAt(entry)));
                    }
                });
        return quotes;
    }

    private JsonNode request(String coins) {
        try {
            JsonNode body =
                    http.get()
                            .uri(
                                    uri ->
                                            uri.path("/simple/price")
                                                    .queryParam("ids", coins)
                                                    .queryParam("vs_currencies", vsCurrency)
                                                    .queryParam("include_last_updated_at", true)
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
            if (body == null) {
                throw new QuoteProviderException("CoinGecko returned an empty body", null);
            }
            return body;
        } catch (RestClientException failure) {
            throw new QuoteProviderException("CoinGecko did not answer", failure);
        }
    }

    private Instant observedAt(JsonNode entry) {
        JsonNode updated = entry.path("last_updated_at");
        return updated.isNumber() ? Instant.ofEpochSecond(updated.asLong()) : clock.instant();
    }
}
