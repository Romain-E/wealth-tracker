package fr.patrimoine.api;

import fr.patrimoine.api.dto.AccountDetailResponse;
import fr.patrimoine.api.dto.AmountRequest;
import fr.patrimoine.api.dto.TransactionRequest;
import fr.patrimoine.api.dto.TransactionResponse;
import fr.patrimoine.api.dto.ValuationResponse;
import fr.patrimoine.application.port.in.GetAccountDetailUseCase;
import fr.patrimoine.application.port.in.RecordTransactionUseCase;
import fr.patrimoine.application.port.in.RecordValuationUseCase;
import fr.patrimoine.domain.model.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** One envelope: what is in it, what goes into it, and what it is declared to be worth. */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "A single envelope and the movements recorded on it")
class AccountController {

    private final GetAccountDetailUseCase accountDetail;
    private final RecordTransactionUseCase recordTransaction;
    private final RecordValuationUseCase recordValuation;

    AccountController(
            GetAccountDetailUseCase accountDetail,
            RecordTransactionUseCase recordTransaction,
            RecordValuationUseCase recordValuation) {
        this.accountDetail = accountDetail;
        this.recordTransaction = recordTransaction;
        this.recordValuation = recordValuation;
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "One account, its positions marked to market, its remaining allowance")
    // Declared in full: once any response is listed, springdoc no longer adds the 200 by itself.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The account"),
        @ApiResponse(responseCode = "404", description = "No such account")
    })
    AccountDetailResponse detail(@PathVariable UUID accountId) {
        return AccountDetailResponse.from(accountDetail.detail(new AccountId(accountId)));
    }

    /**
     * Records a movement, letting the aggregate decide whether it is legal.
     *
     * <p>201 with no {@code Location} header, deliberately. There is no endpoint that serves a
     * single movement, and inventing one purely to have somewhere to point would mean adding a use
     * case the application does not have. A header that 404s would be worse than none.
     */
    @PostMapping("/{accountId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Record a deposit, withdrawal, purchase or sale",
            description =
                    "The envelope's own rules decide: a payment over a regulatory ceiling, an"
                            + " instrument the envelope may not hold, or a purchase with"
                            + " insufficient cash is refused with 422.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Malformed body"),
        @ApiResponse(responseCode = "404", description = "No such account"),
        @ApiResponse(responseCode = "422", description = "A business rule refused the movement")
    })
    TransactionResponse record(
            @PathVariable UUID accountId, @RequestBody @Valid TransactionRequest request) {
        return TransactionResponse.from(
                recordTransaction.record(request.toCommand(new AccountId(accountId))));
    }

    /**
     * Declares what an unpriceable envelope was worth on one day.
     *
     * <p>PUT, not POST, and the date is in the path: an account has exactly one value per day, and
     * sending the same appraisal twice must not create two of them. The URL names the thing being
     * replaced, which is what makes the call safe to retry after a timeout.
     */
    @PutMapping("/{accountId}/valuations/{date}")
    @Operation(
            summary = "Set this account's value for a given day",
            description =
                    "For envelopes with no market price: property, or a life-insurance contract"
                            + " valued by the insurer's statement. Idempotent per day.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The valuation, as now recorded"),
        @ApiResponse(responseCode = "400", description = "Malformed body"),
        @ApiResponse(responseCode = "404", description = "No such account")
    })
    ValuationResponse valuation(
            @PathVariable UUID accountId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid AmountRequest value) {
        return ValuationResponse.from(
                recordValuation.record(
                        new RecordValuationUseCase.RecordValuationCommand(
                                new AccountId(accountId), date, value.toMoney())));
    }
}
