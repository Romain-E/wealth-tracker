package fr.patrimoine.api.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /** Captures what the MDC held while the request was being handled, not after. */
    private String duringRequest;

    private final FilterChain chain =
            (request, response) -> duringRequest = MDC.get(CorrelationIdFilter.MDC_KEY);

    private MockHttpServletResponse handle(String suppliedHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/portfolio");
        if (suppliedHeader != null) {
            request.addHeader(CorrelationIdFilter.HEADER, suppliedHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("invents an id when the caller supplies none")
    void generatesAnId() throws Exception {
        MockHttpServletResponse response = handle(null);

        String issued = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(issued).isNotNull();
        assertThat(UUID.fromString(issued)).isNotNull();
        assertThat(duringRequest).isEqualTo(issued);
    }

    @Test
    @DisplayName("keeps a caller's own id, so a trace spans both systems")
    void honoursASuppliedId() throws Exception {
        MockHttpServletResponse response = handle("checkout-7f3a9");

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("checkout-7f3a9");
        assertThat(duringRequest).isEqualTo("checkout-7f3a9");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "has spaces",
                "forged\nWARN pretend log line",
                "injected\r\nSet-Cookie: admin=true",
                "<script>alert(1)</script>",
                "0123456789012345678901234567890123456789012345678901234567890123456789"
            })
    @DisplayName("replaces anything that could forge a log line or a header")
    void replacesUnsafeIds(String unsafe) throws Exception {
        MockHttpServletResponse response = handle(unsafe);

        String issued = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(issued).isNotEqualTo(unsafe);
        assertThat(UUID.fromString(issued)).isNotNull();
    }

    @Test
    @DisplayName("clears the id afterwards, so a pooled thread cannot mislabel the next request")
    void clearsTheMdcAfterwards() throws Exception {
        handle("checkout-7f3a9");

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        assertThat(CorrelationIdFilter.current()).isNull();
    }
}
