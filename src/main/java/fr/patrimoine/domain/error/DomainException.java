package fr.patrimoine.domain.error;

/**
 * Base type for every business-rule violation.
 *
 * <p>Each subclass carries a stable, machine-readable {@code code}. The API layer maps that code
 * onto an RFC 7807 {@code type} URI and an HTTP status without ever switching on the exception
 * class, so adding a rule here does not require touching the web layer.
 *
 * <p>Unchecked on purpose: these are programming-time-unpredictable, request-scoped failures, and
 * forcing every caller to declare them would leak business rules into method signatures all the way
 * up the stack.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
