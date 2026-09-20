package fr.patrimoine.infrastructure.quote;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

@DisplayName("CoinGeckoQuoteProvider")
class CoinGeckoQuoteProviderTest {

    @RegisterExtension
    static final WireMockExtension coinGecko =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final InstrumentId BITCOIN = InstrumentId.of("BITCOIN");
    private static final InstrumentId ETHEREUM = InstrumentId.of("ETHEREUM");
    private static final Instant UPDATED = Instant.ofEpochSecond(1789550350);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);

    private static CoinGeckoQuoteProvider provider(String apiKey) {
        return new CoinGeckoQuoteProvider(
                RestClient.builder(),
                QuoteTestProperties.pointingAt(coinGecko.baseUrl(), apiKey, "http://localhost:1"),
                CLOCK);
    }

    @Test
    @DisplayName("prices every coin in a single call, using CoinGecko's lower-case ids")
    void pricesAllCoinsInOneCall() {
        coinGecko.stubFor(
                get(urlPathEqualTo("/simple/price"))
                        .withQueryParam("ids", equalTo("bitcoin,ethereum"))
                        .withQueryParam("vs_currencies", equalTo("eur"))
                        .willReturn(
                                okJson(
                                        """
                                        {
                                          "bitcoin": {"eur": 65628, "last_updated_at": 1789550350},
                                          "ethereum": {"eur": 2079.36, "last_updated_at": 1789550350}
                                        }
                                        """)));

        Map<InstrumentId, Quote> quotes = provider("").fetch(Set.of(BITCOIN, ETHEREUM));

        assertThat(quotes)
                .containsOnly(
                        entry(BITCOIN, Quote.fresh(BITCOIN, Price.euros("65628"), UPDATED)),
                        entry(ETHEREUM, Quote.fresh(ETHEREUM, Price.euros("2079.36"), UPDATED)));
        coinGecko.verify(1, getRequestedFor(urlPathEqualTo("/simple/price")));
    }

    @Test
    @DisplayName("a coin CoinGecko does not know is simply left out")
    void leavesUnknownCoinsOut() {
        coinGecko.stubFor(
                get(urlPathEqualTo("/simple/price"))
                        .willReturn(okJson("{\"bitcoin\": {\"eur\": 65628}}")));

        Map<InstrumentId, Quote> quotes =
                provider("").fetch(Set.of(BITCOIN, InstrumentId.of("NOT-A-COIN")));

        // No timestamp in the answer: the observation is dated now rather than in 1970.
        assertThat(quotes)
                .containsOnly(
                        entry(
                                BITCOIN,
                                Quote.fresh(BITCOIN, Price.euros("65628"), CLOCK.instant())));
    }

    @Test
    @DisplayName("sends the API key only when one is configured")
    void sendsTheKeyOnlyWhenConfigured() {
        coinGecko.stubFor(get(urlPathEqualTo("/simple/price")).willReturn(okJson("{}")));

        provider("demo-key").fetch(Set.of(BITCOIN));
        provider("").fetch(Set.of(BITCOIN));

        coinGecko.verify(
                1,
                getRequestedFor(urlPathEqualTo("/simple/price"))
                        .withHeader("x-cg-demo-api-key", equalTo("demo-key")));
        coinGecko.verify(
                1,
                getRequestedFor(urlPathEqualTo("/simple/price"))
                        .withHeader("x-cg-demo-api-key", absent()));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {429, 500, 503})
    @DisplayName("a CoinGecko that refuses or fails is reported as a provider failure")
    void reportsUnavailability(int status) {
        coinGecko.stubFor(
                get(urlPathEqualTo("/simple/price")).willReturn(aResponse().withStatus(status)));

        assertThatExceptionOfType(QuoteProviderException.class)
                .isThrownBy(() -> provider("").fetch(Set.of(BITCOIN)));
    }
}
