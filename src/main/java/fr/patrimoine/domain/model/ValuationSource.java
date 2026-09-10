package fr.patrimoine.domain.model;

/** Where a {@link Valuation} came from, which is how much to trust it. */
public enum ValuationSource {
    /** Entered by the user: an appraisal, an insurer statement. */
    MANUAL,
    /** Computed by this application by marking positions to market. */
    COMPUTED,
    /** Received from an external system. */
    IMPORTED
}
