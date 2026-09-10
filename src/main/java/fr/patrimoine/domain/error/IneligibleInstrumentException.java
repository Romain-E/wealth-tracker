package fr.patrimoine.domain.error;

/**
 * Raised when an instrument may not legally be held in an envelope, e.g. a crypto-asset inside a
 * PEA, whose eligible universe is restricted by the Code monetaire et financier.
 */
public class IneligibleInstrumentException extends DomainException {

    private static final long serialVersionUID = 1L;

    public IneligibleInstrumentException(String accountType, String instrumentKind) {
        super(
                "ineligible-instrument",
                "A %s account cannot hold an instrument of kind %s"
                        .formatted(accountType, instrumentKind));
    }
}
