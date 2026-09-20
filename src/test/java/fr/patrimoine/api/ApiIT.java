package fr.patrimoine.api;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.api.correlation.CorrelationIdFilter;
import fr.patrimoine.infrastructure.persistence.PostgresContainerConfiguration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The API over real HTTP, against the seeded database: controllers, validation, error mapping,
 * serialisation, use cases, Postgres and Flyway all at once.
 *
 * <p>The slice tests above prove each layer in isolation against stubs. This one proves they were
 * wired to each other, which is the failure no amount of mocking can catch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
// Prices come from the seed: a test must not depend on a website being up.
@TestPropertySource(properties = "patrimoine.quotes.live-prices=false")
@Import(PostgresContainerConfiguration.class)
@DisplayName("The API end to end")
class ApiIT {

    /** Fixed ids from the demo seed. */
    private static final UUID LIVRET_A = UUID.fromString("00000000-0000-4000-a000-000000000001");

    private static final UUID FLAT = UUID.fromString("00000000-0000-4000-a000-000000000007");

    @Autowired private TestRestTemplate http;

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("serves the seeded portfolio, and labels the response with a correlation id")
    void servesThePortfolio() {
        ResponseEntity<String> response = http.getForEntity("/api/v1/portfolio", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER)).isNotBlank();
        assertThat(response.getBody())
                .contains("\"currency\":\"EUR\"")
                .contains("\"type\":\"LIVRET_A\"")
                .contains("\"typeLabel\":\"Immobilier\"");
    }

    @Test
    @DisplayName("echoes a correlation id supplied by the caller")
    void echoesASuppliedCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER, "smoke-test-1");

        ResponseEntity<String> response =
                http.exchange(
                        "/api/v1/portfolio",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);

        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER))
                .isEqualTo("smoke-test-1");
    }

    @Test
    @DisplayName("an unknown account is a 404 problem document")
    void reportsAnUnknownAccount() {
        ResponseEntity<String> response =
                http.getForEntity("/api/v1/accounts/{id}", String.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("/problems/resource-not-found");
    }

    @Test
    @DisplayName("the Livret A ceiling is enforced all the way from HTTP to the aggregate")
    void refusesADepositOverTheCeiling() {
        // The seeded Livret A has 22 450 EUR of payments against a 22 950 EUR cap: 500 EUR of room.
        ResponseEntity<String> response =
                http.postForEntity(
                        "/api/v1/accounts/{id}/transactions",
                        json(
                                """
                                {
                                  "type": "DEPOSIT",
                                  "date": "%s",
                                  "amount": {"amount": 5000.00, "currency": "EUR"}
                                }
                                """
                                        .formatted(LocalDate.now())),
                        String.class,
                        LIVRET_A);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody())
                .contains("/problems/deposit-ceiling-exceeded")
                .contains("22950.00");
    }

    @Test
    @DisplayName("a deposit within the ceiling is recorded and shows up in the account's balance")
    void recordsADepositAndReflectsIt() {
        ResponseEntity<String> recorded =
                http.postForEntity(
                        "/api/v1/accounts/{id}/transactions",
                        json(
                                """
                                {
                                  "type": "DEPOSIT",
                                  "date": "%s",
                                  "amount": {"amount": 100.00, "currency": "EUR"}
                                }
                                """
                                        .formatted(LocalDate.now())),
                        String.class,
                        LIVRET_A);

        assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(recorded.getBody()).contains("\"type\":\"DEPOSIT\"");

        ResponseEntity<String> account =
                http.getForEntity("/api/v1/accounts/{id}", String.class, LIVRET_A);
        assertThat(account.getBody()).contains("\"cashBalance\":{\"amount\":23490.95");
    }

    @Test
    @DisplayName("putting a valuation twice replaces it rather than stacking snapshots")
    void putsAValuation() {
        String today = LocalDate.now().toString();
        String body =
                """
                {"amount": 318000.00, "currency": "EUR"}
                """;

        http.put("/api/v1/accounts/{id}/valuations/{date}", json(body), FLAT, today);
        http.put("/api/v1/accounts/{id}/valuations/{date}", json(body), FLAT, today);

        ResponseEntity<String> account =
                http.getForEntity("/api/v1/accounts/{id}", String.class, FLAT);
        assertThat(account.getBody()).contains("\"totalValue\":{\"amount\":318000.00");
    }

    @Test
    @DisplayName("publishes the OpenAPI document its clients are generated from")
    void publishesItsOwnDescription() {
        ResponseEntity<String> response = http.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"title\":\"Patrimoine API\"")
                .contains("\"/api/v1/portfolio\"")
                .contains("\"/api/v1/accounts/{accountId}/valuations/{date}\"")
                // The four movement shapes reach the document as a discriminated union, which is
                // what makes a generated client able to tell a deposit from a purchase.
                .contains("DepositRequest")
                .contains("PurchaseRequest");
    }
}
