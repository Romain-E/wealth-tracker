package fr.patrimoine.domain.error;

/** Raised when a withdrawal or a purchase exceeds the cash available on the account. */
public class InsufficientCashException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InsufficientCashException(String available, String required) {
        super(
                "insufficient-cash",
                "Insufficient cash: %s available, %s required".formatted(available, required));
    }
}
