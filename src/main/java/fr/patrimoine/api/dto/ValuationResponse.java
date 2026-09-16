package fr.patrimoine.api.dto;

import fr.patrimoine.domain.model.Valuation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** What an unpriceable envelope was declared to be worth on a given day. */
@Schema(description = "A recorded valuation snapshot")
public record ValuationResponse(
        UUID accountId, LocalDate date, MoneyResponse value, String source) {

    public static ValuationResponse from(Valuation valuation) {
        return new ValuationResponse(
                valuation.accountId().value(),
                valuation.on(),
                MoneyResponse.of(valuation.value()),
                valuation.source().name());
    }
}
