package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.error.CurrencyMismatchException;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Domain service: values a whole portfolio and builds its breakdowns.
 *
 * <p>A service rather than a method on {@link Account} because the calculation spans accounts,
 * which is precisely the case where an aggregate is the wrong home for behaviour. It has no state
 * and no dependencies, so it needs no framework and no mocks to test.
 *
 * <p>Valuation deliberately happens in two passes. Shares of the total cannot be computed until the
 * total is known, and computing a running total then patching percentages afterwards is how you end
 * up with allocations that sum to 99.97%.
 */
public final class PortfolioValuator {

    /**
     * @throws CurrencyMismatchException if the accounts are not all denominated in one currency;
     *     multi-currency consolidation needs an FX rate source, which this domain does not have and
     *     should not silently invent
     */
    public PortfolioValuation value(Collection<Account> accounts, ValuationContext context) {
        if (accounts.isEmpty()) {
            return new PortfolioValuation(
                    context.asOf(), Money.ZERO_EUR, List.of(), Map.of(), false, List.of());
        }

        Currency currency = accounts.iterator().next().currency();

        // Pass 1: value each account independently.
        List<ValuationResult> results = new ArrayList<>(accounts.size());
        List<Account> ordered = List.copyOf(accounts);
        Money total = Money.zero(currency);
        boolean stale = false;
        List<String> warnings = new ArrayList<>();

        for (Account account : ordered) {
            ValuationResult result = ValuationStrategy.forAccount(account).value(account, context);
            results.add(result);
            total = total.plus(result.value());
            stale |= result.stale();
            warnings.addAll(result.warnings());
        }

        // Pass 2: now that the total is known, express each account as a share of it.
        List<AccountValuation> byAccount = new ArrayList<>(ordered.size());
        Map<AccountType, Money> byType = new EnumMap<>(AccountType.class);

        for (int i = 0; i < ordered.size(); i++) {
            Account account = ordered.get(i);
            ValuationResult result = results.get(i);
            byAccount.add(
                    new AccountValuation(
                            account.id(),
                            account.label(),
                            account.type(),
                            result.value(),
                            Percentage.ofRatio(result.value().ratioTo(total)),
                            result.stale(),
                            result.warnings()));
            byType.merge(account.type(), result.value(), Money::plus);
        }

        return new PortfolioValuation(context.asOf(), total, byAccount, byType, stale, warnings);
    }
}
