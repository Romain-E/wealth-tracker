package fr.patrimoine.domain.model;

/**
 * The broad families a saver sorts envelopes into, above the envelope itself: money set aside,
 * money invested, and property.
 *
 * <p>The line is drawn by what the money does, not by tax wrapper. A Livret A and an LDDS are cash
 * that earns a regulated rate and can be withdrawn at any time. A PEA, a compte-titres, a crypto
 * account and an assurance vie all hold assets whose value moves; the euro fund inside an assurance
 * vie is capital-guaranteed, but the contract is still an investment decision, taken for years.
 *
 * <p>Declaration order is display order.
 */
public enum AccountCategory {
    SAVINGS("Livrets d'épargne"),
    INVESTMENTS("Placements"),
    REAL_ESTATE("Immobilier");

    private final String label;

    AccountCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
