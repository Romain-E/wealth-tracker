package fr.patrimoine.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The published description of this API.
 *
 * <p>Generated from the code rather than written alongside it, so it cannot drift: the schemas come
 * from the response records, the statuses from the handlers. The frontend generates its TypeScript
 * client from this document, which means a field renamed here breaks the frontend build instead of
 * a screen at runtime. {@code OpenApiContractIT} keeps the committed copy in {@code web/} honest.
 *
 * <p>The server is declared as {@code /}, the same origin as the document. Left to itself,
 * springdoc writes the URL of whichever host and port served the request, which makes the document
 * differ between machines and would put a developer's localhost into the frontend's contract.
 */
@Configuration
class OpenApiConfiguration {

    private static final String SCHEMAS = "#/components/schemas/";
    private static final String PROBLEM = "Problem";
    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    @Bean
    OpenAPI patrimoineApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Patrimoine API")
                                .version("v1")
                                .description(
                                        """
                                        Wealth tracking for French savings envelopes: Livret A, \
                                        LDDS, PEA, compte-titres, crypto, assurance vie and \
                                        property.

                                        Regulatory rules are enforced by the domain, not by the \
                                        caller: payment ceilings, instrument eligibility and cash \
                                        settlement are checked on every movement, and a refusal \
                                        comes back as a 422 problem document naming the rule.

                                        Errors follow RFC 7807. Every response carries an \
                                        `X-Correlation-Id` header, and every problem document \
                                        repeats it, so a reported error can be traced to its \
                                        request in the logs.\
                                        """)
                                .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/").description("Same origin")));
    }

    /**
     * Makes the generated document say exactly what the JSON on the wire says, so that a client
     * generated from it is precise. Four corrections to what springdoc infers from the Java types:
     *
     * <ol>
     *   <li><b>Every response property is required.</b> Jackson writes every record component; a
     *       value that can be missing is written as {@code null} and declared nullable. Left
     *       unmarked, a generated client types every field as optional, which pushes pointless null
     *       checks into every screen and hides the few fields that really can be null. Requests are
     *       left alone: their required fields come from the validation annotations.
     *   <li><b>A nullable reference is "that schema or null".</b> springdoc writes it as a {@code
     *       $ref} with a {@code null} type beside it, which JSON Schema reads as both at once, and
     *       generators drop the null.
     *   <li><b>A discriminated union is a plain union.</b> The request shapes implement a common
     *       Java interface, so springdoc makes each one extend the union it belongs to, a cycle
     *       that generators untangle badly. Each shape becomes standalone, with its discriminator
     *       value as a literal, and the union is only the list of its shapes.
     *   <li><b>Every error is a problem document.</b> Each declared 4xx and 5xx response is
     *       described as {@code application/problem+json} with one shared schema, so a client gets
     *       a typed error instead of an unknown body.
     * </ol>
     */
    @Bean
    OpenApiCustomizer preciseContract() {
        return openApi -> {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Map<String, Schema<?>> schemas = (Map) openApi.getComponents().getSchemas();
            flattenDiscriminatedUnions(schemas);
            schemas.forEach(
                    (name, schema) -> {
                        if (name.endsWith("Request") || schema.getProperties() == null) {
                            return;
                        }
                        schema.setRequired(
                                new ArrayList<>(new TreeSet<>(schema.getProperties().keySet())));
                        schema.getProperties()
                                .replaceAll((property, value) -> nullableReference(value));
                    });
            // Added after the pass above: its required members are chosen, not "all of them".
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            describeErrorsAsProblems(openApi);
        };
    }

    private static void describeErrorsAsProblems(OpenAPI openApi) {
        MediaType problem = new MediaType().schema(reference(SCHEMAS + PROBLEM));
        openApi.getPaths().values().stream()
                .flatMap(path -> path.readOperations().stream())
                .flatMap(operation -> operation.getResponses().entrySet().stream())
                .filter(response -> response.getKey().matches("[45]\\d\\d"))
                .forEach(
                        response ->
                                response.getValue()
                                        .setContent(
                                                new Content()
                                                        .addMediaType(
                                                                PROBLEM_MEDIA_TYPE, problem)));
    }

    /**
     * RFC 7807, plus the two members this API adds: {@code code} for its own errors and {@code
     * correlationId} on every problem. Errors raised by Spring itself carry no code, so only the
     * RFC's members are required.
     */
    @SuppressWarnings("rawtypes")
    private static JsonSchema problemSchema() {
        JsonSchema fieldError = typed("object");
        Map<String, Schema> fieldErrorProperties = new LinkedHashMap<>();
        fieldErrorProperties.put("field", typed("string"));
        fieldErrorProperties.put("message", typed("string"));
        fieldError.setProperties(fieldErrorProperties);
        fieldError.setRequired(List.of("field", "message"));
        JsonSchema errors = typed("array");
        errors.setItems(fieldError);

        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("type", typed("string"));
        properties.put("title", typed("string"));
        properties.put("status", typed("integer"));
        properties.put("detail", typed("string"));
        properties.put("instance", typed("string"));
        properties.put("code", typed("string"));
        properties.put("correlationId", typed("string"));
        properties.put("errors", errors);

        JsonSchema problem = typed("object");
        problem.setDescription("An RFC 7807 problem document");
        problem.setProperties(properties);
        problem.setRequired(List.of("type", "title", "status"));
        return problem;
    }

    private static Schema<?> nullableReference(Schema<?> property) {
        boolean nullable = property.getTypes() != null && property.getTypes().contains("null");
        if (property.get$ref() == null || !nullable) {
            return property;
        }
        JsonSchema either = new JsonSchema();
        either.setOneOf(List.of(reference(property.get$ref()), typed("null")));
        either.setDescription(property.getDescription());
        return either;
    }

    private static JsonSchema typed(String type) {
        JsonSchema schema = new JsonSchema();
        schema.addType(type);
        return schema;
    }

    private static JsonSchema reference(String ref) {
        JsonSchema schema = new JsonSchema();
        schema.set$ref(ref);
        return schema;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void flattenDiscriminatedUnions(Map<String, Schema<?>> schemas) {
        schemas.forEach(
                (unionName, union) -> {
                    Discriminator discriminator = union.getDiscriminator();
                    if (discriminator == null || discriminator.getMapping() == null) {
                        return;
                    }
                    String tagName = discriminator.getPropertyName();
                    discriminator
                            .getMapping()
                            .forEach(
                                    (tagValue, variantRef) -> {
                                        Schema variant =
                                                schemas.get(variantRef.substring(SCHEMAS.length()));
                                        if (variant == null || variant.getAllOf() == null) {
                                            return;
                                        }
                                        JsonSchema tag = new JsonSchema();
                                        tag.addType("string");
                                        tag.setEnum(List.of(tagValue));

                                        Map<String, Schema> properties = new LinkedHashMap<>();
                                        properties.put(tagName, tag);
                                        List<String> required = new ArrayList<>(List.of(tagName));
                                        for (Object item : variant.getAllOf()) {
                                            Schema part = (Schema) item;
                                            boolean isTheUnion =
                                                    (SCHEMAS + unionName).equals(part.get$ref());
                                            if (!isTheUnion && part.getProperties() != null) {
                                                properties.putAll(part.getProperties());
                                            }
                                        }
                                        if (variant.getRequired() != null) {
                                            required.addAll(variant.getRequired());
                                        }
                                        variant.setAllOf(null);
                                        variant.setTypes(Set.of("object"));
                                        variant.setProperties(properties);
                                        variant.setRequired(required);
                                    });
                    union.setProperties(null);
                    union.setRequired(null);
                });
    }
}
