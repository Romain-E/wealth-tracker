package fr.patrimoine.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.application.port.in.RecordValuationUseCase.RecordValuationCommand;
import fr.patrimoine.application.port.in.TransactionCommand;
import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.application.port.out.ValuationRepository;
import fr.patrimoine.domain.error.DepositCeilingExceededException;
import fr.patrimoine.domain.error.IneligibleInstrumentException;
import fr.patrimoine.domain.error.InsufficientCashException;
import fr.patrimoine.domain.model.Account;
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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionCommandService: the aggregate decides, the service persists")
class TransactionCommandServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final InstrumentId WORLD = InstrumentId.of("IE00B4L5Y983");
    private static final InstrumentId BITCOIN = InstrumentId.of("BITCOIN");

    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private ValuationRepository valuations;

    @InjectMocks private TransactionCommandService service;

    private Account given(AccountType type) {
        Account account =
                Account.open(
                        AccountId.newId(), type.label(), type, Money.EUR, LocalDate.of(2020, 1, 1));
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        return account;
    }

    private Transaction captureSaved() {
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.captor();
        verify(transactions).save(saved.capture());
        return saved.getValue();
    }

    @Nested
    @DisplayName("cash movements")
    class CashMovements {

        @Test
        @DisplayName("a deposit moves the balance and records a positive movement")
        void recordsADeposit() {
            Account cto = given(AccountType.CTO);
            when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

            service.record(new TransactionCommand.Deposit(cto.id(), TODAY, Money.euros("1500.00")));

            assertThat(cto.cashBalance()).isEqualTo(Money.euros("1500.00"));
            verify(accounts).save(cto);
            Transaction movement = captureSaved();
            assertThat(movement.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(movement.amount()).isEqualTo(Money.euros("1500.00"));
            assertThat(movement.instrumentId()).isEmpty();
        }

        @Test
        @DisplayName("a withdrawal is recorded negative, signed from the account's point of view")
        void recordsAWithdrawalAsANegativeAmount() {
            Account cto = given(AccountType.CTO);
            cto.deposit(Money.euros("2000.00"));
            when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

            service.record(
                    new TransactionCommand.Withdrawal(cto.id(), TODAY, Money.euros("500.00")));

            assertThat(cto.cashBalance()).isEqualTo(Money.euros("1500.00"));
            Transaction movement = captureSaved();
            assertThat(movement.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(movement.amount()).isEqualTo(Money.euros("-500.00"));
        }
    }

    @Nested
    @DisplayName("trades")
    class Trades {

        @Test
        @DisplayName("a purchase records its all-in cost, fees included, as a negative amount")
        void recordsAPurchase() {
            Account pea = given(AccountType.PEA);
            pea.deposit(Money.euros("10000.00"));
            when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

            service.record(
                    new TransactionCommand.Purchase(
                            pea.id(),
                            TODAY,
                            WORLD,
                            InstrumentKind.ETF,
                            Quantity.of(50),
                            Price.euros("102.40"),
                            Money.euros("4.90")));

            assertThat(pea.position(WORLD)).isPresent();
            Transaction movement = captureSaved();
            assertThat(movement.type()).isEqualTo(TransactionType.BUY);
            // 50 * 102.40 + 4.90 = 5124.90, leaving the account.
            assertThat(movement.amount()).isEqualTo(Money.euros("-5124.90"));
            assertThat(movement.instrumentId()).contains(WORLD);
            assertThat(movement.tradedQuantity()).contains(Quantity.of(50));
        }

        @Test
        @DisplayName("a sale records net proceeds, fees deducted, as a positive amount")
        void recordsASale() {
            Account pea = given(AccountType.PEA);
            pea.deposit(Money.euros("10000.00"));
            pea.buy(
                    WORLD,
                    InstrumentKind.ETF,
                    Quantity.of(50),
                    Price.euros("100.00"),
                    Money.ZERO_EUR);
            when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

            service.record(
                    new TransactionCommand.Sale(
                            pea.id(),
                            TODAY,
                            WORLD,
                            Quantity.of(20),
                            Price.euros("110.00"),
                            Money.euros("2.90")));

            Transaction movement = captureSaved();
            assertThat(movement.type()).isEqualTo(TransactionType.SELL);
            // 20 * 110.00 - 2.90 = 2197.10, arriving in the account.
            assertThat(movement.amount()).isEqualTo(Money.euros("2197.10"));
        }
    }

    @Nested
    @DisplayName("a refused movement writes nothing")
    class RefusedMovements {

        @Test
        @DisplayName("breaching the Livret A ceiling persists neither account nor movement")
        void doesNotPersistWhenTheCeilingRefuses() {
            Account livretA = given(AccountType.LIVRET_A);
            livretA.deposit(Money.euros("22000.00"));

            assertThatExceptionOfType(DepositCeilingExceededException.class)
                    .isThrownBy(
                            () ->
                                    service.record(
                                            new TransactionCommand.Deposit(
                                                    livretA.id(), TODAY, Money.euros("2000.00"))));

            verify(accounts, never()).save(any());
            verify(transactions, never()).save(any());
        }

        @Test
        @DisplayName("an ineligible instrument is refused before anything is written")
        void doesNotPersistWhenTheInstrumentIsIneligible() {
            Account pea = given(AccountType.PEA);
            pea.deposit(Money.euros("10000.00"));

            assertThatExceptionOfType(IneligibleInstrumentException.class)
                    .isThrownBy(
                            () ->
                                    service.record(
                                            new TransactionCommand.Purchase(
                                                    pea.id(),
                                                    TODAY,
                                                    BITCOIN,
                                                    InstrumentKind.CRYPTO,
                                                    Quantity.of("0.5"),
                                                    Price.euros("50000.00"),
                                                    Money.ZERO_EUR)));

            verify(accounts, never()).save(any());
            verify(transactions, never()).save(any());
        }

        @Test
        @DisplayName("an unsettleable purchase is refused before anything is written")
        void doesNotPersistWhenThereIsNotEnoughCash() {
            Account cto = given(AccountType.CTO);
            cto.deposit(Money.euros("100.00"));

            assertThatExceptionOfType(InsufficientCashException.class)
                    .isThrownBy(
                            () ->
                                    service.record(
                                            new TransactionCommand.Purchase(
                                                    cto.id(),
                                                    TODAY,
                                                    WORLD,
                                                    InstrumentKind.ETF,
                                                    Quantity.of(50),
                                                    Price.euros("100.00"),
                                                    Money.ZERO_EUR)));

            verify(transactions, never()).save(any());
        }
    }

    @Test
    @DisplayName("an unknown account is a lookup failure, not a business-rule failure")
    void rejectsAnUnknownAccount() {
        AccountId missing = AccountId.newId();
        when(accounts.findById(missing)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(
                        () ->
                                service.record(
                                        new TransactionCommand.Deposit(
                                                missing, TODAY, Money.euros("100.00"))))
                .satisfies(e -> assertThat(e.code()).isEqualTo("resource-not-found"))
                .withMessageContaining("Account");
    }

    @Test
    @DisplayName("a manual valuation is recorded as MANUAL, never as COMPUTED")
    void recordsAManualValuation() {
        Account flat = given(AccountType.REAL_ESTATE);
        when(valuations.save(any())).thenAnswer(call -> call.getArgument(0));

        service.record(new RecordValuationCommand(flat.id(), TODAY, Money.euros("315000.00")));

        ArgumentCaptor<Valuation> saved = ArgumentCaptor.captor();
        verify(valuations).save(saved.capture());
        assertThat(saved.getValue().source()).isEqualTo(ValuationSource.MANUAL);
        assertThat(saved.getValue().value()).isEqualTo(Money.euros("315000.00"));
    }
}
