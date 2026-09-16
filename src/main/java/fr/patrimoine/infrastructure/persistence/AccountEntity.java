package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.domain.model.AccountType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The storage shape of an {@link fr.patrimoine.domain.model.Account}: a row, with no behaviour.
 *
 * <p>Package-private, like every entity here. Nothing outside the persistence adapter can even name
 * this class, so an entity cannot leak into a use case or a JSON response by accident: the domain
 * aggregate is the only account the rest of the application sees. The fields are package-private
 * for the same reason &mdash; the mapper next door is their only reader, and getters and setters
 * would add surface without adding any encapsulation.
 *
 * <p>Positions are an {@code @ElementCollection} keyed by instrument rather than a child entity.
 * They are values inside the aggregate with no identity of their own, which is exactly what an
 * element collection models, and the map key matches the aggregate's own {@code Map<InstrumentId,
 * Position>}.
 */
@Entity
@Table(name = "account")
class AccountEntity {

    @Id UUID id;

    /**
     * Never read by this code. Hibernate increments it on every update and adds it to the {@code
     * WHERE} clause, which is what turns a lost update into an exception.
     */
    @Version Long version;

    String label;

    @Enumerated(EnumType.STRING)
    AccountType type;

    Currency currency;
    LocalDate openedOn;
    BigDecimal cashBalance;
    BigDecimal netPayments;
    BigDecimal grossPayments;

    @ElementCollection
    @CollectionTable(name = "account_position", joinColumns = @JoinColumn(name = "account_id"))
    @MapKeyColumn(name = "instrument_id")
    Map<String, PositionEmbeddable> positions = new HashMap<>();
}
