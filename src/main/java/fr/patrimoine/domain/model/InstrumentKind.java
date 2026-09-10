package fr.patrimoine.domain.model;

/**
 * What kind of thing a position holds.
 *
 * <p>Drives two unrelated decisions, which is why it is not merged into {@link AccountType}: which
 * envelopes may legally hold it, and which quote provider can price it.
 */
public enum InstrumentKind {
    EQUITY,
    ETF,
    FUND,
    CRYPTO
}
