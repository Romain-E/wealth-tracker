package fr.patrimoine.api.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.patrimoine.application.port.in.TransactionCommand;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Quantity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A movement the caller wants recorded, discriminated by {@code type}.
 *
 * <p>Four shapes rather than one body with nullable fields, mirroring the sealed {@link
 * TransactionCommand} it maps onto. The payoff is that a deposit is structurally incapable of
 * carrying a quantity: there is no "instrument required for BUY" validation to write, because the
 * invalid combination cannot be expressed. Jackson picks the shape from {@code type}, so an unknown
 * type is rejected during parsing instead of reaching a service.
 *
 * <p>Dates cannot be in the future. Recording a purchase for next week would let it sit in the
 * ledger changing a balance that has not happened yet.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TransactionRequest.Deposit.class, name = "DEPOSIT"),
    @JsonSubTypes.Type(value = TransactionRequest.Withdrawal.class, name = "WITHDRAWAL"),
    @JsonSubTypes.Type(value = TransactionRequest.Purchase.class, name = "BUY"),
    @JsonSubTypes.Type(value = TransactionRequest.Sale.class, name = "SELL")
})
@Schema(
        description = "A movement to record; the type field selects the shape",
        discriminatorProperty = "type")
public sealed interface TransactionRequest {

    TransactionCommand toCommand(AccountId accountId);

    /** Money paid in from outside. This is the one that meets the regulatory ceiling. */
    @Schema(name = "DepositRequest")
    record Deposit(@NotNull @PastOrPresent LocalDate date, @NotNull @Valid AmountRequest amount)
            implements TransactionRequest {

        @Override
        public TransactionCommand toCommand(AccountId accountId) {
            return new TransactionCommand.Deposit(accountId, date, amount.toMoney());
        }
    }

    /** Money taken out. */
    @Schema(name = "WithdrawalRequest")
    record Withdrawal(@NotNull @PastOrPresent LocalDate date, @NotNull @Valid AmountRequest amount)
            implements TransactionRequest {

        @Override
        public TransactionCommand toCommand(AccountId accountId) {
            return new TransactionCommand.Withdrawal(accountId, date, amount.toMoney());
        }
    }

    /** Buying units, settled from the envelope's own cash. */
    @Schema(name = "PurchaseRequest")
    record Purchase(
            @NotNull @PastOrPresent LocalDate date,
            @NotBlank @Schema(example = "LU1681043599") String instrument,
            @NotNull InstrumentKind kind,
            @NotNull @Positive @Digits(integer = 17, fraction = 8) BigDecimal quantity,
            @NotNull @Valid PriceRequest unitPrice,
            @Valid @Schema(description = "Dealing fees; omitted means none") AmountRequest fees)
            implements TransactionRequest {

        @Override
        public TransactionCommand toCommand(AccountId accountId) {
            return new TransactionCommand.Purchase(
                    accountId,
                    date,
                    InstrumentId.of(instrument),
                    kind,
                    Quantity.of(quantity),
                    unitPrice.toPrice(),
                    feesOrNone(fees, unitPrice));
        }
    }

    /** Selling units, with the net proceeds credited back to cash. */
    @Schema(name = "SaleRequest")
    record Sale(
            @NotNull @PastOrPresent LocalDate date,
            @NotBlank @Schema(example = "LU1681043599") String instrument,
            @NotNull @Positive @Digits(integer = 17, fraction = 8) BigDecimal quantity,
            @NotNull @Valid PriceRequest unitPrice,
            @Valid @Schema(description = "Dealing fees; omitted means none") AmountRequest fees)
            implements TransactionRequest {

        @Override
        public TransactionCommand toCommand(AccountId accountId) {
            return new TransactionCommand.Sale(
                    accountId,
                    date,
                    InstrumentId.of(instrument),
                    Quantity.of(quantity),
                    unitPrice.toPrice(),
                    feesOrNone(fees, unitPrice));
        }
    }

    /** Absent fees mean none, in the currency the trade was priced in. */
    private static Money feesOrNone(AmountRequest fees, PriceRequest unitPrice) {
        return fees == null ? Money.zero(unitPrice.asCurrency()) : fees.toMoney();
    }
}
