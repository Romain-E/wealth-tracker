package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import java.util.List;
import java.util.Objects;

/**
 * What one account is worth, plus how much to trust that number.
 *
 * <p>A valuation that cannot be computed cleanly still returns a value. Refusing to answer because
 * one quote out of forty is missing would make the whole dashboard unusable over a single failing
 * ticker. Instead the number is best-effort and the caveats travel with it: {@code stale} when a
 * price came from cache while the provider was down, and {@code warnings} naming exactly which
 * instruments could not be priced.
 */
public record ValuationResult(
        AccountId accountId, Money value, boolean stale, List<String> warnings) {

    public ValuationResult {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(value, "value");
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    public static ValuationResult clean(AccountId accountId, Money value) {
        return new ValuationResult(accountId, value, false, List.of());
    }

    public boolean isComplete() {
        return warnings.isEmpty();
    }
}
