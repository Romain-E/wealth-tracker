package fr.patrimoine.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A single movement on an account.
 *
 * <p>Immutable and dated. The instrument fields are populated only for {@code BUY} and {@code
 * SELL}; modelling those as a separate subtype would be more precise but would double the
 * persistence mapping for very little gain, so the invariant is enforced in the constructor instead
 * and the accessors return {@link Optional}.
 *
 * <p>{@code amount} is always signed from the account's point of view: positive when cash comes in
 * (deposit, sale, dividend), negative when it leaves (withdrawal, purchase, fee).
 */
public record Transaction(
        TransactionId id,
        AccountId accountId,
        TransactionType type,
        LocalDate date,
        Money amount,
        InstrumentId instrument,
        Quantity quantity,
        Price unitPrice) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        boolean isTrade = type == TransactionType.BUY || type == TransactionType.SELL;
        if (isTrade && (instrument == null || quantity == null || unitPrice == null)) {
            throw new IllegalArgumentException(
                    type + " requires an instrument, a quantity and a unit price");
        }
        if (!isTrade && instrument != null) {
            throw new IllegalArgumentException(type + " must not carry an instrument");
        }
    }

    public static Transaction cashMovement(
            AccountId accountId, TransactionType type, LocalDate date, Money amount) {
        return new Transaction(
                TransactionId.newId(), accountId, type, date, amount, null, null, null);
    }

    public static Transaction trade(
            AccountId accountId,
            TransactionType type,
            LocalDate date,
            Money amount,
            InstrumentId instrument,
            Quantity quantity,
            Price unitPrice) {
        return new Transaction(
                TransactionId.newId(),
                accountId,
                type,
                date,
                amount,
                instrument,
                quantity,
                unitPrice);
    }

    public Optional<InstrumentId> instrumentId() {
        return Optional.ofNullable(instrument);
    }

    public Optional<Quantity> tradedQuantity() {
        return Optional.ofNullable(quantity);
    }
}
