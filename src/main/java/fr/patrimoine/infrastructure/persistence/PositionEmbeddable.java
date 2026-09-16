package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.domain.model.InstrumentKind;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.util.Currency;

/** One row of {@code account_position}; the instrument is the map key on {@link AccountEntity}. */
@Embeddable
record PositionEmbeddable(
        @Enumerated(EnumType.STRING) InstrumentKind kind,
        BigDecimal quantity,
        BigDecimal averageCost,
        Currency costCurrency) {}
