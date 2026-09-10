package fr.patrimoine.domain.error;

/**
 * Raised when a payment would push an account past its regulatory ceiling.
 *
 * <p>Note that the ceiling applies to <em>payments</em>, never to the balance: a Livret A is
 * legally allowed to sit above 22 950 EUR once capitalised interest is added, and a PEA above 150
 * 000 EUR once the securities have gained value. Only new money is capped.
 */
public class DepositCeilingExceededException extends DomainException {

    private static final long serialVersionUID = 1L;

    public DepositCeilingExceededException(String accountType, String ceiling, String attempted) {
        super(
                "deposit-ceiling-exceeded",
                "%s payments are capped at %s; this payment would bring them to %s"
                        .formatted(accountType, ceiling, attempted));
    }
}
