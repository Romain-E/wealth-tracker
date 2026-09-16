package fr.patrimoine.api.dto;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A movement as it was recorded.
 *
 * <p>{@code amount} is signed from the account's point of view: positive when money comes in,
 * negative when it leaves. A purchase therefore reports its whole cash effect, fees included, which
 * is the figure that reconciles against a bank statement.
 */
@Schema(description = "A recorded movement on an account")
public record TransactionResponse(
        UUID id,
        UUID accountId,
        String type,
        LocalDate date,
        MoneyResponse amount,
        @Schema(nullable = true) String instrument,
        @Schema(nullable = true) BigDecimal quantity,
        @Schema(nullable = true) BigDecimal unitPrice) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id().value(),
                transaction.accountId().value(),
                transaction.type().name(),
                transaction.date(),
                MoneyResponse.of(transaction.amount()),
                transaction.instrumentId().map(InstrumentId::value).orElse(null),
                transaction.tradedQuantity().map(Quantity::value).orElse(null),
                transaction.unitPrice() == null ? null : transaction.unitPrice().value());
    }
}
