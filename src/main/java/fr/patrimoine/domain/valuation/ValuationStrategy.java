package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.ValuationMethod;

/**
 * How to put a number on an envelope.
 *
 * <p>Sealed on purpose. The three implementations are not an open extension point, they are an
 * exhaustive description of a closed set: French savings products are valued in exactly these three
 * ways. Sealing buys a compile-time guarantee &mdash; the {@code switch} in {@link #forMethod} has
 * no {@code default}, so the day a fourth valuation method is added the compiler points at every
 * place that must handle it, instead of a {@code default} branch silently returning the wrong
 * strategy in production.
 *
 * <p>The strategies are stateless singletons: they take everything they need as arguments, which is
 * what lets them be shared and what makes them trivially thread-safe.
 */
public sealed interface ValuationStrategy
        permits RegulatedSavingsValuation, MarketValuation, SnapshotValuation {

    ValuationResult value(Account account, ValuationContext context);

    static ValuationStrategy forMethod(ValuationMethod method) {
        return switch (method) {
            case REGULATED_SAVINGS -> RegulatedSavingsValuation.INSTANCE;
            case MARKET -> MarketValuation.INSTANCE;
            case SNAPSHOT -> SnapshotValuation.INSTANCE;
        };
    }

    static ValuationStrategy forAccount(Account account) {
        return forMethod(account.type().valuationMethod());
    }
}
