package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Position;
import fr.patrimoine.domain.model.Quote;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Marks positions to market and adds uninvested cash: PEA, CTO, crypto.
 *
 * <p>Two failure modes are handled deliberately rather than thrown.
 *
 * <p><b>A missing quote</b> falls back to the position's cost basis. Valuing it at zero would show
 * the user a catastrophic loss that did not happen, which is far more alarming than a slightly
 * stale number; valuing it at cost is the most conservative honest estimate. The warning names the
 * instrument so the caller can surface it.
 *
 * <p><b>A stale quote</b> is used as-is but propagates its staleness to the result. This is the
 * mechanism that lets the API keep answering while the quote provider's circuit breaker is open,
 * without pretending the numbers are live.
 */
public final class MarketValuation implements ValuationStrategy {

    static final MarketValuation INSTANCE = new MarketValuation();

    private MarketValuation() {}

    @Override
    public ValuationResult value(Account account, ValuationContext context) {
        Money total = account.cashBalance();
        List<String> warnings = new ArrayList<>();
        boolean stale = false;

        for (Position position : account.positions()) {
            Optional<Quote> quote = context.quoteFor(position.instrument());
            if (quote.isEmpty()) {
                warnings.add(
                        "No quote for %s; valued at its cost basis"
                                .formatted(position.instrument()));
                total = total.plus(position.totalCost());
                continue;
            }
            stale |= quote.get().stale();
            total = total.plus(position.marketValue(quote.get()));
        }

        return new ValuationResult(account.id(), total, stale, warnings);
    }
}
