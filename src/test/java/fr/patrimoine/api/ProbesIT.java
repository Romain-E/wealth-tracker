package fr.patrimoine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.patrimoine.infrastructure.persistence.PostgresContainerConfiguration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * What Kubernetes and an operator see: the probes, the health details and the build information,
 * all on the management port.
 *
 * <p>Redis is pointed at a port where nothing listens, so the cache is down whatever happens to run
 * on the machine executing the tests. That is the interesting case: the application must stay ready
 * and alive, and say that it is degraded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Import(PostgresContainerConfiguration.class)
@TestPropertySource(
        properties = {"spring.data.redis.port=1", "patrimoine.quotes.refresh-initial-delay=PT1H"})
@DisplayName("Health probes and build information")
class ProbesIT {

    @LocalManagementPort private int managementPort;
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;

    private ResponseEntity<String> management(String path) {
        return http.getForEntity(
                "http://localhost:" + managementPort + "/actuator" + path, String.class);
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody());
    }

    private static Set<String> components(JsonNode health) {
        Set<String> names = new HashSet<>();
        health.path("components").fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("liveness looks at the application only, never at a dependency")
    void livenessIgnoresDependencies() throws Exception {
        ResponseEntity<String> response = management("/health/liveness");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).path("status").asText()).isEqualTo("UP");
        assertThat(components(body(response))).containsExactly("livenessState");
    }

    @Test
    @DisplayName("readiness needs the database, and stays UP without the cache")
    void readinessNeedsTheDatabaseOnly() throws Exception {
        ResponseEntity<String> response = management("/health/readiness");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).path("status").asText()).isEqualTo("UP");
        assertThat(components(body(response))).containsExactlyInAnyOrder("readinessState", "db");
    }

    @Test
    @DisplayName("overall health reports the missing cache as DEGRADED, still with HTTP 200")
    void reportsADegradedApplication() throws Exception {
        ResponseEntity<String> response = management("/health");
        JsonNode health = body(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.path("status").asText()).isEqualTo("DEGRADED");
        JsonNode quotes = health.path("components").path("quotes");
        assertThat(quotes.path("status").asText()).isEqualTo("DEGRADED");
        assertThat(quotes.path("details").path("cache").asText()).isEqualTo("DOWN");
        // The cache is reported once, as optional, not a second time as a failure.
        assertThat(components(health)).doesNotContain("redis").contains("db", "quotes");
    }

    @Test
    @DisplayName("info says which build and which commit are running")
    void identifiesTheRunningBuild() throws Exception {
        JsonNode info = body(management("/info"));

        assertThat(info.path("build").path("artifact").asText()).isEqualTo("patrimoine-api");
        assertThat(info.path("git").path("commit").path("id").asText()).isNotBlank();
        assertThat(info.path("java").path("version").asText()).isNotBlank();
    }

    @Test
    @DisplayName("none of it is reachable on the public port")
    void keepsActuatorOffThePublicPort() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
