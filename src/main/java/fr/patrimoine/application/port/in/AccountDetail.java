package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the account screen needs, assembled from the aggregate plus current prices.
 *
 * @param remainingAllowance room left before the regulatory ceiling; empty for uncapped envelopes,
 *     which is a different thing from zero room left and must not be flattened into one
 * @param warnings caveats from valuation, e.g. an instrument that could not be priced
 */
public record AccountDetail(
        AccountId id,
        String label,
        AccountType type,
        Currency currency,
        LocalDate openedOn,
        Money cashBalance,
        Money totalValue,
        Optional<Money> remainingAllowance,
        List<PositionDetail> positions,
        boolean stale,
        List<String> warnings) {

    public AccountDetail {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(openedOn, "openedOn");
        Objects.requireNonNull(cashBalance, "cashBalance");
        Objects.requireNonNull(totalValue, "totalValue");
        Objects.requireNonNull(remainingAllowance, "remainingAllowance");
        positions = List.copyOf(Objects.requireNonNullElse(positions, List.of()));
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }
}
