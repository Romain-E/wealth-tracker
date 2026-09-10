package fr.patrimoine.domain.model;

/**
 * What a regulatory ceiling is measured against.
 *
 * <p>The distinction is real and is the kind of thing that gets a naive implementation wrong. A
 * Livret A caps <em>net</em> payments at 22 950 EUR, so withdrawing frees the room back up. A PEA
 * caps <em>gross</em> payments at 150 000 EUR, so a withdrawal does not: the room is consumed for
 * good (and before five years a withdrawal closes the plan outright).
 */
public enum CeilingBasis {

    /** No regulatory cap. */
    NONE,

    /** Payments minus withdrawals. Livret A, LDDS. */
    NET_PAYMENTS,

    /** Cumulative payments, withdrawals ignored. PEA. */
    GROSS_PAYMENTS
}
