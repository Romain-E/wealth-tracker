package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.TransactionId;

/** Translates between a ledger movement and its row. Both directions, since rows never change. */
final class TransactionMapper {

    private TransactionMapper() {}

    static TransactionEntity toEntity(Transaction transaction) {
        TransactionEntity entity = new TransactionEntity();
        entity.id = transaction.id().value();
        entity.accountId = transaction.accountId().value();
        entity.type = transaction.type();
        entity.tradeDate = transaction.date();
        entity.amount = transaction.amount().amount();
        entity.currency = transaction.amount().currency();
        if (transaction.instrument() != null) {
            entity.instrumentId = transaction.instrument().value();
            entity.quantity = transaction.quantity().value();
            entity.unitPrice = transaction.unitPrice().value();
            entity.priceCurrency = transaction.unitPrice().currency();
        }
        return entity;
    }

    static Transaction toDomain(TransactionEntity entity) {
        boolean isTrade = entity.instrumentId != null;
        return new Transaction(
                new TransactionId(entity.id),
                new AccountId(entity.accountId),
                entity.type,
                entity.tradeDate,
                Money.of(entity.amount, entity.currency),
                isTrade ? InstrumentId.of(entity.instrumentId) : null,
                isTrade ? Quantity.of(entity.quantity) : null,
                isTrade ? Price.of(entity.unitPrice, entity.priceCurrency) : null);
    }
}
