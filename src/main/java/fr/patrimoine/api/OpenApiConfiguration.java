package fr.patrimoine.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The published description of this API.
 *
 * <p>Generated from the code rather than written alongside it, so it cannot drift: the schemas come
 * from the response records, the statuses from the handlers. The frontend generates its TypeScript
 * client from this document, which means a field renamed here breaks the frontend build instead of
 * a screen at runtime.
 */
@Configuration
class OpenApiConfiguration {

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
                                .license(new License().name("MIT")));
    }
}
