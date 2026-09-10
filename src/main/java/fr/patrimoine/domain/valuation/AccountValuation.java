package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import java.util.List;
import java.util.Objects;

/** One line of the per-account breakdown. */
public record AccountValuation(
        AccountId accountId,
        String label,
        AccountType type,
        Money value,
        Percentage share,
        boolean stale,
        List<String> warnings) {

    public AccountValuation {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(share, "share");
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }
}
