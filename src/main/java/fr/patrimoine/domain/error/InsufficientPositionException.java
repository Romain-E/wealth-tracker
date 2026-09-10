package fr.patrimoine.domain.error;

/** Raised when selling more units of an instrument than the account holds. */
public class InsufficientPositionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InsufficientPositionException(String instrument, String held, String requested) {
        super(
                "insufficient-position",
                "Insufficient position on %s: %s held, %s requested"
                        .formatted(instrument, held, requested));
    }
}
