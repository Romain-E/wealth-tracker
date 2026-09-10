package fr.patrimoine.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link Transaction}. */
public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "value");
    }

    public static TransactionId newId() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(String value) {
        return new TransactionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
