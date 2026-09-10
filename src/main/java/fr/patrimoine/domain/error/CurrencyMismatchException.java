package fr.patrimoine.domain.error;

import java.util.Currency;

/** Raised when two monetary amounts in different currencies are combined. */
public class CurrencyMismatchException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(Currency left, Currency right) {
        super(
                "currency-mismatch",
                "Cannot combine amounts in %s and %s"
                        .formatted(left.getCurrencyCode(), right.getCurrencyCode()));
    }
}
