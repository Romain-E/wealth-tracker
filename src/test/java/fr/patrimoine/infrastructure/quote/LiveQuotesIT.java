package fr.patrimoine.infrastructure.quote;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fr.patrimoine.application.port.out.QuoteRepository;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.infrastructure.persistence.JdbcQuoteStore;
import fr.patrimoine.infrastructure.persistence.PostgresContainerConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Live prices with every real moving part: Postgres, Redis, the resilience configuration from
 * {@code application.yml}, and both providers pointed at a local stub instead of the internet.
 *
 * <p>The scheduled refresh is pushed out of the way so that the only calls the stub sees are the
 * ones each test makes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("demo")
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
@TestPropertySource(properties = "patrimoine.quotes.refresh-initial-delay=PT1H")
@DisplayName("Live quotes end to end")
class LiveQuotesIT {

    @RegisterExtension
    static final WireMockExtension upstream =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void pointProvidersAtTheStub(DynamicPropertyRegistry properties) {
        properties.add("patrimoine.quotes.coingecko.base-url", upstream::baseUrl);
        properties.add("patrimoine.quotes.yahoo.base-url", upstream::baseUrl);
    }

    /** Seeded in the demo portfolio, with an illustrative price. */
    private static final InstrumentId AIR_LIQUIDE = InstrumentId.of("FR0000120073");

    private static final InstrumentId BITCOIN = InstrumentId.of("BITCOIN");

    @Autowired private QuoteRepository quotes;
    @Autowired private JdbcQuoteStore store;
    @Autowired private StringRedisTemplate redis;

    @Test
    @DisplayName(
            "fetches a live price once, then serves it from Redis and keeps it in the database")
    void fetchesCachesAndRemembers() {
        // Observed now, so it is newer than the seeded price and replaces it in the store.
        long observed = Instant.now().getEpochSecond();
        upstream.stubFor(
                get(urlPathEqualTo("/v1/finance/search"))
                        .withQueryParam("q", equalTo("FR0000120073"))
                        .willReturn(okJson("{\"quotes\": [{\"symbol\": \"AI.PA\"}]}")));
        upstream.stubFor(
                get(urlPathEqualTo("/v8/finance/chart/AI.PA"))
                        .willReturn(
                                okJson(
                                        """
                                        {"chart": {"result": [{"meta": {
                                          "currency": "EUR", "regularMarketPrice": 183.40,
                                          "regularMarketTime": %d
                                        }}]}}
                                        """
                                                .formatted(observed))));
        Map<InstrumentId, InstrumentKind> wanted = Map.of(AIR_LIQUIDE, InstrumentKind.EQUITY);

        Quote first = quotes.findLatest(wanted).get(AIR_LIQUIDE);
        Quote second = quotes.findLatest(wanted).get(AIR_LIQUIDE);

        assertThat(first.stale()).isFalse();
        assertThat(first.price()).isEqualTo(Price.euros("183.40"));
        assertThat(second).isEqualTo(first);
        upstream.verify(1, getRequestedFor(urlPathEqualTo("/v8/finance/chart/AI.PA")));
        assertThat(redis.hasKey("quote:FR0000120073")).isTrue();
        assertThat(store.findLatest(List.of(AIR_LIQUIDE)).get(AIR_LIQUIDE).price())
                .isEqualTo(Price.euros("183.40"));
    }

    @Test
    @DisplayName("serves the remembered price, flagged stale, while the crypto source is down")
    void fallsBackWhileTheProviderIsDown() {
        upstream.stubFor(
                get(urlPathEqualTo("/simple/price")).willReturn(aResponse().withStatus(503)));

        Quote bitcoin = quotes.findLatest(Map.of(BITCOIN, InstrumentKind.CRYPTO)).get(BITCOIN);

        assertThat(bitcoin.stale()).isTrue();
        assertThat(bitcoin.price()).isEqualTo(Price.euros("80000.00"));
        // Retried once before giving up, as configured.
        upstream.verify(2, getRequestedFor(urlPathEqualTo("/simple/price")));
    }
}
