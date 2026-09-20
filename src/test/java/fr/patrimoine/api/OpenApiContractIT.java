package fr.patrimoine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import fr.patrimoine.infrastructure.persistence.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The frontend is compiled against {@code web/openapi.json}, a copy of this API's contract kept in
 * the repository. This test fails the backend build the moment that copy stops matching what the
 * API actually serves, so the two cannot drift apart silently: either the change was unintended, or
 * the copy is regenerated in the same commit and the frontend's type check shows everything the
 * change breaks.
 *
 * <p>To regenerate the copy after an intended change:
 *
 * <pre>
 * ./mvnw verify -Dit.test=OpenApiContractIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
 *     -Djacoco.skip=true -Dopenapi.update=true
 * </pre>
 *
 * <p>Same configuration as {@link ApiIT}, so both share one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@TestPropertySource(properties = "patrimoine.quotes.live-prices=false")
@Import(PostgresContainerConfiguration.class)
@DisplayName("OpenAPI contract")
class OpenApiContractIT {

    private static final Path CONTRACT = Path.of("web", "openapi.json");

    /** Sorted keys and LF line endings, so a regenerated file only differs where the API did. */
    private static final ObjectMapper STABLE_JSON =
            JsonMapper.builder().enable(JsonNodeFeature.WRITE_PROPERTIES_SORTED).build();

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("the API serves exactly the contract the frontend is compiled against")
    void matchesTheCommittedContract() throws Exception {
        JsonNode served = json.readTree(http.getForObject("/v3/api-docs", String.class));

        if (Boolean.getBoolean("openapi.update")) {
            write(served);
            return;
        }

        assertThat(CONTRACT)
                .as("%s is missing; generate it with -Dopenapi.update=true", CONTRACT)
                .exists();
        JsonNode committed = json.readTree(CONTRACT.toFile());
        assertThat(served)
                .as(
                        "The API no longer matches %s. If the change is intended, regenerate the"
                                + " file with -Dopenapi.update=true and fix what the frontend type"
                                + " check reports.",
                        CONTRACT)
                .isEqualTo(committed);
    }

    private static void write(JsonNode contract) throws Exception {
        DefaultPrettyPrinter printer =
                new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));
        String text = STABLE_JSON.writer(printer).writeValueAsString(contract) + "\n";
        Files.writeString(CONTRACT, text, StandardCharsets.UTF_8);
    }
}
