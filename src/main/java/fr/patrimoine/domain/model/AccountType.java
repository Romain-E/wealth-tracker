package fr.patrimoine.domain.model;

import java.util.Optional;
import java.util.Set;

/**
 * The French wealth envelopes this application understands.
 *
 * <p>This enum is the single table that encodes the regulatory shape of each envelope. Everything
 * downstream &mdash; which family it belongs to, which valuation strategy applies, whether a trade
 * is legal, whether a payment is capped &mdash; is derived from it, so adding "PEA-PME" later is
 * one row here rather than a search for {@code switch} statements across the codebase.
 *
 * <p>Ceilings are as of 2026 and are deliberately hard-coded rather than configurable: they are
 * facts of French law, they change roughly once a decade, and a wrong value in a config map is much
 * harder to notice than a wrong value next to a unit test.
 */
public enum AccountType {

    /**
     * Regulated passbook. Payments capped at 22 950 EUR; interest may carry the balance above it.
     */
    LIVRET_A(
            "Livret A",
            AccountCategory.SAVINGS,
            ValuationMethod.REGULATED_SAVINGS,
            CeilingBasis.NET_PAYMENTS,
            "22950.00",
            Set.of()),

    /** Sustainable-development passbook. Same mechanics as the Livret A, lower cap. */
    LDDS(
            "Livret de developpement durable et solidaire",
            AccountCategory.SAVINGS,
            ValuationMethod.REGULATED_SAVINGS,
            CeilingBasis.NET_PAYMENTS,
            "12000.00",
            Set.of()),

    /**
     * Equity savings plan. Gross payments capped at 150 000 EUR, and the eligible universe is
     * restricted to EU-domiciled equities and qualifying funds &mdash; notably, no crypto-assets.
     */
    PEA(
            "Plan d'epargne en actions",
            AccountCategory.INVESTMENTS,
            ValuationMethod.MARKET,
            CeilingBasis.GROSS_PAYMENTS,
            "150000.00",
            Set.of(InstrumentKind.EQUITY, InstrumentKind.ETF, InstrumentKind.FUND)),

    /** Ordinary securities account. No cap, no eligibility restriction, no tax shelter either. */
    CTO(
            "Compte-titres ordinaire",
            AccountCategory.INVESTMENTS,
            ValuationMethod.MARKET,
            CeilingBasis.NONE,
            null,
            Set.of(InstrumentKind.EQUITY, InstrumentKind.ETF, InstrumentKind.FUND)),

    /** Crypto-asset account held at an exchange or in self-custody. */
    CRYPTO(
            "Crypto-actifs",
            AccountCategory.INVESTMENTS,
            ValuationMethod.MARKET,
            CeilingBasis.NONE,
            null,
            Set.of(InstrumentKind.CRYPTO)),

    /**
     * Life-insurance contract. Modelled as a snapshot: the euro fund has no market price and the
     * unit-linked side is priced by the insurer on its own calendar, so the honest answer is
     * whatever the last statement said.
     */
    ASSURANCE_VIE(
            "Assurance vie",
            AccountCategory.INVESTMENTS,
            ValuationMethod.SNAPSHOT,
            CeilingBasis.NONE,
            null,
            Set.of()),

    /** Property. Valued by appraisal, which is to say by opinion, which is to say by snapshot. */
    REAL_ESTATE(
            "Immobilier",
            AccountCategory.REAL_ESTATE,
            ValuationMethod.SNAPSHOT,
            CeilingBasis.NONE,
            null,
            Set.of());

    private final String label;
    private final AccountCategory category;
    private final ValuationMethod valuationMethod;
    private final CeilingBasis ceilingBasis;
    private final Money ceiling;
    private final Set<InstrumentKind> eligibleInstruments;

    AccountType(
            String label,
            AccountCategory category,
            ValuationMethod valuationMethod,
            CeilingBasis ceilingBasis,
            String ceiling,
            Set<InstrumentKind> eligibleInstruments) {
        this.label = label;
        this.category = category;
        this.valuationMethod = valuationMethod;
        this.ceilingBasis = ceilingBasis;
        this.ceiling = ceiling == null ? null : Money.euros(ceiling);
        this.eligibleInstruments = Set.copyOf(eligibleInstruments);
    }

    public String label() {
        return label;
    }

    public AccountCategory category() {
        return category;
    }

    public ValuationMethod valuationMethod() {
        return valuationMethod;
    }

    public CeilingBasis ceilingBasis() {
        return ceilingBasis;
    }

    public Optional<Money> ceiling() {
        return Optional.ofNullable(ceiling);
    }

    public Set<InstrumentKind> eligibleInstruments() {
        return eligibleInstruments;
    }

    /** Whether this envelope holds market positions at all. */
    public boolean holdsPositions() {
        return valuationMethod == ValuationMethod.MARKET;
    }

    public boolean accepts(InstrumentKind kind) {
        return eligibleInstruments.contains(kind);
    }
}
