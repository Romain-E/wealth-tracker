package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.Money;
import java.util.List;

/**
 * Values an envelope at whatever its most recent manual valuation said: assurance vie, real estate.
 *
 * <p>There is no market price for a flat in Lyon or for a euro fund, and inventing one would be
 * worse than admitting it. When no valuation has ever been recorded the account is worth zero and
 * says so in a warning, rather than being quietly dropped from the total &mdash; a portfolio that
 * silently omits an account is a portfolio that reports the wrong net worth.
 */
public final class SnapshotValuation implements ValuationStrategy {

    static final SnapshotValuation INSTANCE = new SnapshotValuation();

    private SnapshotValuation() {}

    @Override
    public ValuationResult value(Account account, ValuationContext context) {
        return context.latestValuationFor(account.id())
                .map(valuation -> ValuationResult.clean(account.id(), valuation.value()))
                .orElseGet(
                        () ->
                                new ValuationResult(
                                        account.id(),
                                        Money.zero(account.currency()),
                                        false,
                                        List.of(
                                                "No valuation has ever been recorded for "
                                                        + account.label())));
    }
}
