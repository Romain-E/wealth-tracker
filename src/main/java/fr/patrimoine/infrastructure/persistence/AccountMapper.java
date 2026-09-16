package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Position;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the account aggregate and its storage shape.
 *
 * <p>The write direction is {@link #copyInto}, not a {@code toEntity}: it updates the entity
 * Hibernate already manages instead of building a new one. A fresh object carries no version, so
 * Spring Data would take an existing account for a new one and try to insert it; and the version
 * check only means something on the instance that was loaded before the account was mutated.
 */
final class AccountMapper {

    private AccountMapper() {}

    static Account toDomain(AccountEntity entity) {
        Currency currency = entity.currency;
        List<Position> positions =
                entity.positions.entrySet().stream()
                        // A stable order: the aggregate keeps insertion order, a loaded map has
                        // none.
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> toDomain(entry.getKey(), entry.getValue()))
                        .toList();
        return Account.rehydrate(
                new AccountId(entity.id),
                entity.label,
                entity.type,
                currency,
                entity.openedOn,
                Money.of(entity.cashBalance, currency),
                Money.of(entity.netPayments, currency),
                Money.of(entity.grossPayments, currency),
                positions);
    }

    static void copyInto(Account account, AccountEntity entity) {
        entity.id = account.id().value();
        entity.label = account.label();
        entity.type = account.type();
        entity.currency = account.currency();
        entity.openedOn = account.openedOn();
        entity.cashBalance = account.cashBalance().amount();
        entity.netPayments = account.netPayments().amount();
        entity.grossPayments = account.grossPayments().amount();

        Map<String, PositionEmbeddable> wanted = new HashMap<>();
        account.positions()
                .forEach(position -> wanted.put(position.instrument().value(), toRow(position)));

        // Touch only the entries that changed. Hibernate's map marks itself dirty on any put of a
        // different object, even an equal one, and writes nothing for an entry left alone.
        entity.positions.keySet().removeIf(instrument -> !wanted.containsKey(instrument));
        wanted.forEach(
                (instrument, row) -> {
                    if (!row.equals(entity.positions.get(instrument))) {
                        entity.positions.put(instrument, row);
                    }
                });
    }

    private static Position toDomain(String instrument, PositionEmbeddable row) {
        return new Position(
                InstrumentId.of(instrument),
                row.kind(),
                Quantity.of(row.quantity()),
                Price.of(row.averageCost(), row.costCurrency()));
    }

    private static PositionEmbeddable toRow(Position position) {
        return new PositionEmbeddable(
                position.kind(),
                position.quantity().value(),
                position.averageCost().value(),
                position.averageCost().currency());
    }
}
