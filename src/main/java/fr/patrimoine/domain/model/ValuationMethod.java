package fr.patrimoine.domain.model;

/**
 * How an envelope's value is established. This is the axis that actually matters when computing a
 * portfolio, and it does not line up one-to-one with the account type a user recognises.
 */
public enum ValuationMethod {

    /** Value is the balance, moved only by payments and by capitalised interest. Livret A, LDDS. */
    REGULATED_SAVINGS,

    /** Value is the sum of positions marked to market, plus uninvested cash. PEA, CTO, crypto. */
    MARKET,

    /** Value is whatever the latest manual valuation says. Assurance vie in euros, real estate. */
    SNAPSHOT
}
