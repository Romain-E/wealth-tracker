package fr.patrimoine.domain.error;

/**
 * Raised when a trade is attempted on an envelope that holds no market positions at all, such as a
 * Livret A or a real-estate holding.
 */
public class PositionsNotSupportedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PositionsNotSupportedException(String accountType) {
        super(
                "positions-not-supported",
                "A %s account does not hold market positions".formatted(accountType));
    }
}
