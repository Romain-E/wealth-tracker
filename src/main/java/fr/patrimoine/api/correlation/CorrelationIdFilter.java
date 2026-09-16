package fr.patrimoine.api.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id that appears in its logs, its error responses and its response header.
 *
 * <p>This is what makes a production report actionable. "It failed around 2pm" sends someone
 * grepping; "it failed, here is the id from the error message" finds the exact request. The same
 * value travels three ways: into the MDC so every log line of this request carries it, into the
 * {@code X-Correlation-Id} response header, and into the {@code correlationId} field of any problem
 * document the request produces.
 *
 * <p><b>An id supplied by the caller is honoured, but never trusted verbatim.</b> It is echoed into
 * a response header and written into logs, so accepting arbitrary text would allow header injection
 * and forged log lines &mdash; a newline in the value is enough to invent a convincing log entry.
 * Anything that is not a short, boring token is replaced with a fresh one rather than rejected: the
 * request itself is not at fault, only its label.
 *
 * <p>Runs first, so that anything failing later still has an id, and clears the MDC in a {@code
 * finally}: the thread goes back to a pool, and a leftover id would quietly mislabel the next
 * request's logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId =
                accepted(request.getHeader(HEADER)).orElseGet(() -> UUID.randomUUID().toString());
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static Optional<String> accepted(String supplied) {
        return Optional.ofNullable(supplied).filter(value -> ACCEPTABLE.matcher(value).matches());
    }

    /** The current request's id, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
