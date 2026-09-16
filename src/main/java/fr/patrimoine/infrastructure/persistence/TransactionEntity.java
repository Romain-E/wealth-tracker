package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.domain.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * The storage shape of a {@link fr.patrimoine.domain.model.Transaction}: one ledger row.
 *
 * <p>{@code @Immutable} because the ledger is append-only: Hibernate skips dirty checking for these
 * rows and never issues an {@code UPDATE} for them.
 */
@Entity
@Immutable
@Table(name = "account_transaction")
class TransactionEntity {

    @Id UUID id;

    /** Assigned by the database, read only to order movements that share a date. */
    @Column(insertable = false, updatable = false)
    Long sequenceNumber;

    UUID accountId;

    @Enumerated(EnumType.STRING)
    TransactionType type;

    LocalDate tradeDate;
    BigDecimal amount;
    Currency currency;
    String instrumentId;
    BigDecimal quantity;
    BigDecimal unitPrice;
    Currency priceCurrency;
}
