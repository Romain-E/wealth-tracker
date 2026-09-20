package fr.patrimoine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.application.port.in.AccountDetail;
import fr.patrimoine.application.port.in.GetAccountDetailUseCase;
import fr.patrimoine.application.port.in.RecordTransactionUseCase;
import fr.patrimoine.application.port.in.RecordValuationUseCase;
import fr.patrimoine.application.port.in.TransactionCommand;
import fr.patrimoine.domain.error.DepositCeilingExceededException;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.TransactionType;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@DisplayName("/api/v1/accounts")
class AccountControllerTest {

    private static final AccountId ACCOUNT = AccountId.newId();
    private static final InstrumentId WORLD = InstrumentId.of("LU1681043599");

    @Autowired private MockMvc mvc;
    @MockitoBean private GetAccountDetailUseCase accountDetail;
    @MockitoBean private RecordTransactionUseCase recordTransaction;
    @MockitoBean private RecordValuationUseCase recordValuation;

    @Test
    @DisplayName("an uncapped envelope reports a null allowance, not a zero one")
    void rendersAnAccountWithoutACeiling() throws Exception {
        when(accountDetail.detail(ACCOUNT))
                .thenReturn(
                        new AccountDetail(
                                ACCOUNT,
                                "Compte-titres",
                                AccountType.CTO,
                                Money.EUR,
                                LocalDate.of(2023, 2, 1),
                                Money.euros("1986.01"),
                                Money.euros("10386.01"),
                                Optional.empty(),
                                List.of(),
                                false,
                                List.of()));

        mvc.perform(get("/api/v1/accounts/{id}", ACCOUNT.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeLabel").value("Compte-titres ordinaire"))
                .andExpect(jsonPath("$.totalValue.amount").value(10386.01))
                .andExpect(jsonPath("$.remainingAllowance").doesNotExist());
    }

    @Test
    @DisplayName("an unknown account is a 404 problem document carrying the correlation id")
    void reportsAnUnknownAccount() throws Exception {
        when(accountDetail.detail(any()))
                .thenThrow(new ResourceNotFoundException("Account", ACCOUNT.toString()));

        mvc.perform(get("/api/v1/accounts/{id}", ACCOUNT.value()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/resource-not-found"))
                .andExpect(jsonPath("$.code").value("resource-not-found"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("a deposit becomes a Deposit command, amount and date intact")
    void recordsADeposit() throws Exception {
        when(recordTransaction.record(any()))
                .thenReturn(
                        Transaction.cashMovement(
                                ACCOUNT,
                                TransactionType.DEPOSIT,
                                LocalDate.of(2026, 9, 1),
                                Money.euros("1000.00")));

        mvc.perform(
                        post("/api/v1/accounts/{id}/transactions", ACCOUNT.value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "type": "DEPOSIT",
                                          "date": "2026-09-01",
                                          "amount": {"amount": 1000.00, "currency": "EUR"}
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount.amount").value(1000.00))
                .andExpect(jsonPath("$.instrument").doesNotExist());

        ArgumentCaptor<TransactionCommand> command =
                ArgumentCaptor.forClass(TransactionCommand.class);
        verify(recordTransaction).record(command.capture());
        assertThat(command.getValue())
                .isEqualTo(
                        new TransactionCommand.Deposit(
                                ACCOUNT, LocalDate.of(2026, 9, 1), Money.euros("1000.00")));
    }

    @Test
    @DisplayName("a purchase keeps its eight-decimal price, and omitted fees mean none")
    void recordsAPurchaseWithoutFees() throws Exception {
        when(recordTransaction.record(any()))
                .thenReturn(
                        Transaction.trade(
                                ACCOUNT,
                                TransactionType.BUY,
                                LocalDate.of(2026, 9, 1),
                                Money.euros("-1080.00"),
                                WORLD,
                                Quantity.of(2),
                                Price.euros("540.00")));

        mvc.perform(
                        post("/api/v1/accounts/{id}/transactions", ACCOUNT.value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "type": "BUY",
                                          "date": "2026-09-01",
                                          "instrument": "LU1681043599",
                                          "kind": "ETF",
                                          "quantity": 2,
                                          "unitPrice": {"amount": 540.00, "currency": "EUR"}
                                        }
                                        """))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransactionCommand> command =
                ArgumentCaptor.forClass(TransactionCommand.class);
        verify(recordTransaction).record(command.capture());
        assertThat(command.getValue())
                .isEqualTo(
                        new TransactionCommand.Purchase(
                                ACCOUNT,
                                LocalDate.of(2026, 9, 1),
                                WORLD,
                                InstrumentKind.ETF,
                                Quantity.of(2),
                                Price.euros("540.00"),
                                Money.ZERO_EUR));
    }

    @Test
    @DisplayName("fractions of a cent are refused rather than rounded on the caller's behalf")
    void rejectsSubCentAmounts() throws Exception {
        mvc.perform(
                        post("/api/v1/accounts/{id}/transactions", ACCOUNT.value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "type": "DEPOSIT",
                                          "date": "2026-09-01",
                                          "amount": {"amount": 10.005, "currency": "EUR"}
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"))
                .andExpect(jsonPath("$.errors[0].field").value("amount.amount"));
    }

    @Test
    @DisplayName("a refused business rule is a 422 naming the rule, not a 500")
    void reportsARefusedDeposit() throws Exception {
        when(recordTransaction.record(any()))
                .thenThrow(new DepositCeilingExceededException("Livret A", "22950.00", "27950.00"));

        mvc.perform(
                        post("/api/v1/accounts/{id}/transactions", ACCOUNT.value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "type": "DEPOSIT",
                                          "date": "2026-09-01",
                                          "amount": {"amount": 5000.00, "currency": "EUR"}
                                        }
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/deposit-ceiling-exceeded"))
                .andExpect(jsonPath("$.detail").value(containsString("22950.00")));
    }

    @Test
    @DisplayName("a valuation names its day in the URL, so sending it twice replaces it")
    void recordsAValuation() throws Exception {
        when(recordValuation.record(any()))
                .thenReturn(
                        new Valuation(
                                ACCOUNT,
                                LocalDate.of(2026, 6, 30),
                                Money.euros("305000.00"),
                                ValuationSource.MANUAL));

        mvc.perform(
                        put(
                                        "/api/v1/accounts/{id}/valuations/{date}",
                                        ACCOUNT.value(),
                                        "2026-06-30")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\": 305000.00, \"currency\": \"EUR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-06-30"))
                .andExpect(jsonPath("$.source").value("MANUAL"));

        verify(recordValuation)
                .record(
                        new RecordValuationUseCase.RecordValuationCommand(
                                ACCOUNT, LocalDate.of(2026, 6, 30), Money.euros("305000.00")));
    }
}
