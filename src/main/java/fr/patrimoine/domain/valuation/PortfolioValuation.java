package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.AccountCategory;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Money;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to "what am I worth": one total, and the two breakdowns a user actually asks for.
 *
 * <p>{@code byType} is kept alongside {@code byAccount} rather than left to the caller to derive,
 * because grouping money is exactly the operation you do not want duplicated in a controller, in a
 * React component and in a test with three slightly different rounding behaviours.
 *
 * @param stale true when any component of the total came from a cached price
 */
public record PortfolioValuation(
        LocalDate asOf,
        Money total,
        List<AccountValuation> byAccount,
        Map<AccountType, Money> byType,
        boolean stale,
        List<String> warnings) {

    public PortfolioValuation {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(total, "total");
        byAccount = List.copyOf(Objects.requireNonNullElse(byAccount, List.of()));
        // An EnumMap rather than Map.copyOf: it keeps AccountType declaration order, so the
        // breakdown always renders in the same, deliberate order instead of a hash order that
        // changes between runs. Map.copyOf would silently throw that ordering away.
        EnumMap<AccountType, Money> ordered = new EnumMap<>(AccountType.class);
        ordered.putAll(Objects.requireNonNullElse(byType, Map.of()));
        byType = Collections.unmodifiableMap(ordered);
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    /**
     * The same money, one level up: each family of envelopes and its total, in family order. Built
     * from {@code byType}, so the two breakdowns cannot disagree.
     */
    public Map<AccountCategory, Money> byCategory() {
        EnumMap<AccountCategory, Money> totals = new EnumMap<>(AccountCategory.class);
        byType.forEach((type, value) -> totals.merge(type.category(), value, Money::plus));
        return Collections.unmodifiableMap(totals);
    }

    public boolean isComplete() {
        return warnings.isEmpty();
    }
}
