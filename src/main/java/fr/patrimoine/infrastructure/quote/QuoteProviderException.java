package fr.patrimoine.infrastructure.quote;

/**
 * A price source could not answer: unreachable, too slow, overloaded, or answering nonsense.
 *
 * <p>Every transport-level failure is translated into this one type, and it is the only exception
 * the circuit breaker and the retry are configured to count. That keeps their configuration to a
 * single line, and it means a bug in this application's own code, which surfaces as some other
 * exception, is never mistaken for an outage upstream.
 */
public class QuoteProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QuoteProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
