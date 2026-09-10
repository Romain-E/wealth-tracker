package fr.patrimoine.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an {@link Account}.
 *
 * <p>A wrapper rather than a bare {@code UUID} so that the compiler rejects passing an account id
 * where a transaction id is expected. Both are UUIDs; only one of them is correct.
 */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
