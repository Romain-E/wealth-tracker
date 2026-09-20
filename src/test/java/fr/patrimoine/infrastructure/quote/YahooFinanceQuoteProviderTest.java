package fr.patrimoine.infrastructure.quote;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.infrastructure.persistence.JdbcInstrumentSymbolStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("YahooFinanceQuoteProvider")
class YahooFinanceQuoteProviderTest {

    @RegisterExtension
    static final WireMockExtension yahoo =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final InstrumentId AMUNDI_WORLD = InstrumentId.of("LU1681043599");
    private static final InstrumentId ISHARES_WORLD = InstrumentId.of("IE00B4L5Y983");
    private static final long MARKET_TIME = 1789549514L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);

    @Mock private JdbcInstrumentSymbolStore symbols;

    private YahooFinanceQuoteProvider provider;

    @BeforeEach
    void createProvider() {
        provider =
                new YahooFinanceQuoteProvider(
                        RestClient.builder(),
                        QuoteTestProperties.pointingAt("http://localhost:1", "", yahoo.baseUrl()),
                        symbols,
                        CLOCK);
    }

    @AfterEach
    void stopLookups() {
        provider.destroy();
    }

    private static void chart(String symbol, String currency, String price) {
        yahoo.stubFor(
                get(urlPathEqualTo("/v8/finance/chart/" + symbol))
                        .willReturn(
                                okJson(
                                        """
                                        {"chart": {"result": [{"meta": {
                                          "symbol": "%s", "currency": "%s",
                                          "regularMarketPrice": %s, "regularMarketTime": %d
                                        }}], "error": null}}
                                        """
                                                .formatted(symbol, currency, price, MARKET_TIME))));
    }

    private static void search(InstrumentId isin, String... listings) {
        String quotes =
                Arrays.stream(listings)
                        .map(symbol -> "{\"symbol\": \"" + symbol + "\"}")
                        .collect(Collectors.joining(","));
        yahoo.stubFor(
                get(urlPathEqualTo("/v1/finance/search"))
                        .withQueryParam("q", equalTo(isin.value()))
                        .willReturn(okJson("{\"quotes\": [" + quotes + "]}")));
    }

    private static Quote euros(InstrumentId instrument, String price) {
        return Quote.fresh(instrument, Price.euros(price), Instant.ofEpochSecond(MARKET_TIME));
    }

    @Test
    @DisplayName("translates an ISIN to a listing on the preferred exchange, then remembers it")
    void resolvesAnIsinToAPreferredListing() {
        when(symbols.find(AMUNDI_WORLD, "YAHOO")).thenReturn(Optional.empty());
        // The search order is Yahoo's; the exchange preference is ours: Paris beats Xetra.
        search(AMUNDI_WORLD, "CW8.L", "CW8.DE", "CW8.PA");
        chart("CW8.PA", "EUR", "686.05");

        Map<InstrumentId, Quote> quotes = provider.fetch(Set.of(AMUNDI_WORLD));

        assertThat(quotes).containsOnly(entry(AMUNDI_WORLD, euros(AMUNDI_WORLD, "686.05")));
        verify(symbols).remember(AMUNDI_WORLD, "YAHOO", "CW8.PA");
    }

    @Test
    @DisplayName("uses a symbol it already knows without searching again")
    void reusesAKnownSymbol() {
        when(symbols.find(ISHARES_WORLD, "YAHOO")).thenReturn(Optional.of("EUNL.DE"));
        chart("EUNL.DE", "EUR", "126.19");

        assertThat(provider.fetch(Set.of(ISHARES_WORLD)))
                .containsOnly(entry(ISHARES_WORLD, euros(ISHARES_WORLD, "126.19")));
        yahoo.verify(0, getRequestedFor(urlPathEqualTo("/v1/finance/search")));
        verify(symbols, never()).remember(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("takes an identifier that is not an ISIN to be a Yahoo symbol already")
    void usesTickersAsTheyAre() {
        InstrumentId totalEnergies = InstrumentId.of("TTE.PA");
        chart("TTE.PA", "EUR", "56.42");

        assertThat(provider.fetch(Set.of(totalEnergies)))
                .containsOnly(entry(totalEnergies, euros(totalEnergies, "56.42")));
        verifyNoInteractions(symbols);
    }

    @Test
    @DisplayName("leaves an ISIN unpriced rather than settle on a listing in another currency")
    void refusesListingsOutsideTheEuroArea() {
        when(symbols.find(ISHARES_WORLD, "YAHOO")).thenReturn(Optional.empty());
        search(ISHARES_WORLD, "IWDA.L");

        assertThat(provider.fetch(Set.of(ISHARES_WORLD))).isEmpty();
        verify(symbols, never()).remember(any(), anyString(), anyString());
        yahoo.verify(0, getRequestedFor(urlPathEqualTo("/v8/finance/chart/IWDA.L")));
    }

    @Test
    @DisplayName("a symbol Yahoo does not know is left out, not counted as a failure")
    void leavesUnknownSymbolsOut() {
        yahoo.stubFor(
                get(urlPathEqualTo("/v8/finance/chart/GONE.PA"))
                        .willReturn(aResponse().withStatus(404)));

        assertThat(provider.fetch(Set.of(InstrumentId.of("GONE.PA")))).isEmpty();
    }

    @Test
    @DisplayName("a listing quoted in pence is skipped rather than misread as pounds")
    void skipsNonIsoCurrencies() {
        chart("BARC.L", "GBp", "215.5");

        assertThat(provider.fetch(Set.of(InstrumentId.of("BARC.L")))).isEmpty();
    }

    @Test
    @DisplayName("prices several instruments in one call")
    void pricesSeveralInstruments() {
        InstrumentId airLiquide = InstrumentId.of("AI.PA");
        InstrumentId totalEnergies = InstrumentId.of("TTE.PA");
        chart("AI.PA", "EUR", "183.40");
        chart("TTE.PA", "EUR", "56.42");

        assertThat(provider.fetch(Set.of(airLiquide, totalEnergies)))
                .containsOnly(
                        entry(airLiquide, euros(airLiquide, "183.40")),
                        entry(totalEnergies, euros(totalEnergies, "56.42")));
    }

    @Test
    @DisplayName("a Yahoo that throttles or fails is reported as a provider failure")
    void reportsUnavailability() {
        yahoo.stubFor(
                get(urlPathEqualTo("/v8/finance/chart/AI.PA"))
                        .willReturn(aResponse().withStatus(429)));

        assertThatExceptionOfType(QuoteProviderException.class)
                .isThrownBy(() -> provider.fetch(Set.of(InstrumentId.of("AI.PA"))));
    }
}
