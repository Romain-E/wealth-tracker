package fr.patrimoine.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import fr.patrimoine.domain.error.DepositCeilingExceededException;
import fr.patrimoine.domain.error.IneligibleInstrumentException;
import fr.patrimoine.domain.error.InsufficientCashException;
import fr.patrimoine.domain.error.InsufficientPositionException;
import fr.patrimoine.domain.error.PositionsNotSupportedException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Account: the regulatory invariants of French wealth envelopes")
class AccountTest {

    private static final LocalDate OPENED = LocalDate.of(2018, 3, 14);
    private static final InstrumentId TOTAL = InstrumentId.of("FR0000120271");
    private static final InstrumentId BITCOIN = InstrumentId.of("BITCOIN");

    private static Account accountOf(AccountType type) {
        return Account.open(AccountId.newId(), type.label(), type, Money.EUR, OPENED);
    }

    // ------------------------------------------------------------------ ceilings

    @Nested
    @DisplayName("regulatory ceilings")
    class Ceilings {

        @Test
        @DisplayName("a Livret A refuses a payment that would take it past 22 950 EUR")
        void refusesPaymentAboveLivretACeiling() {
            Account livretA = accountOf(AccountType.LIVRET_A);
            livretA.deposit(Money.euros("22000.00"));

            assertThatExceptionOfType(DepositCeilingExceededException.class)
                    .isThrownBy(() -> livretA.deposit(Money.euros("1000.00")))
                    .withMessageContaining("22950.00")
                    .satisfies(e -> assertThat(e.code()).isEqualTo("deposit-ceiling-exceeded"));

            assertThat(livretA.cashBalance()).isEqualTo(Money.euros("22000.00"));
        }

        @Test
        @DisplayName("a payment landing exactly on the ceiling is allowed")
        void acceptsPaymentExactlyAtTheCeiling() {
            Account livretA = accountOf(AccountType.LIVRET_A);

            livretA.deposit(Money.euros("22950.00"));

            assertThat(livretA.cashBalance()).isEqualTo(Money.euros("22950.00"));
            assertThat(livretA.remainingAllowance()).contains(Money.ZERO_EUR);
        }

        @Test
        @DisplayName("capitalised interest may legally carry a Livret A above its ceiling")
        void interestMayExceedTheCeiling() {
            Account livretA = accountOf(AccountType.LIVRET_A);
            livretA.deposit(Money.euros("22950.00"));

            // This is the rule people get wrong: the cap is on payments, not on the balance.
            livretA.credit(Money.euros("401.63"));

            assertThat(livretA.cashBalance()).isEqualTo(Money.euros("23351.63"));
            assertThat(livretA.netPayments()).isEqualTo(Money.euros("22950.00"));
        }

        @Test
        @DisplayName("withdrawing from a Livret A frees the allowance back up (net basis)")
        void withdrawalRestoresLivretAAllowance() {
            Account livretA = accountOf(AccountType.LIVRET_A);
            livretA.deposit(Money.euros("22950.00"));

            livretA.withdraw(Money.euros("5000.00"));

            assertThat(livretA.remainingAllowance()).contains(Money.euros("5000.00"));
            livretA.deposit(Money.euros("5000.00")); // allowed again
            assertThat(livretA.netPayments()).isEqualTo(Money.euros("22950.00"));
        }

        @Test
        @DisplayName("withdrawing from a PEA does NOT free the allowance back up (gross basis)")
        void withdrawalDoesNotRestorePeaAllowance() {
            Account pea = accountOf(AccountType.PEA);
            pea.deposit(Money.euros("150000.00"));

            pea.withdraw(Money.euros("50000.00"));

            // The 150 000 EUR of payments are spent for good; only the cash left.
            assertThat(pea.grossPayments()).isEqualTo(Money.euros("150000.00"));
            assertThat(pea.remainingAllowance()).contains(Money.ZERO_EUR);
            assertThatExceptionOfType(DepositCeilingExceededException.class)
                    .isThrownBy(() -> pea.deposit(Money.euros("1.00")));
        }

        @Test
        @DisplayName("net payments never go negative and hand back allowance that was never earned")
        void netPaymentsAreFlooredAtZero() {
            Account livretA = accountOf(AccountType.LIVRET_A);
            livretA.deposit(Money.euros("1000.00"));
            livretA.credit(Money.euros("500.00"));

            livretA.withdraw(Money.euros("1500.00"));

            assertThat(livretA.netPayments()).isEqualTo(Money.ZERO_EUR);
            assertThat(livretA.remainingAllowance()).contains(Money.euros("22950.00"));
        }

        @Test
        void uncappedEnvelopesReportNoAllowance() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("1000000.00"));

            assertThat(cto.remainingAllowance()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ eligibility

    @Nested
    @DisplayName("instrument eligibility")
    class Eligibility {

        @Test
        @DisplayName("a PEA cannot hold crypto-assets")
        void peaRejectsCrypto() {
            Account pea = accountOf(AccountType.PEA);
            pea.deposit(Money.euros("10000.00"));

            assertThatExceptionOfType(IneligibleInstrumentException.class)
                    .isThrownBy(
                            () ->
                                    pea.buy(
                                            BITCOIN,
                                            InstrumentKind.CRYPTO,
                                            Quantity.of("0.1"),
                                            Price.euros("50000.00"),
                                            Money.ZERO_EUR))
                    .withMessageContaining("CRYPTO");
        }

        @Test
        @DisplayName("a crypto account cannot hold equities")
        void cryptoAccountRejectsEquities() {
            Account crypto = accountOf(AccountType.CRYPTO);
            crypto.deposit(Money.euros("10000.00"));

            assertThatExceptionOfType(IneligibleInstrumentException.class)
                    .isThrownBy(
                            () ->
                                    crypto.buy(
                                            TOTAL,
                                            InstrumentKind.EQUITY,
                                            Quantity.of(10),
                                            Price.euros("58.00"),
                                            Money.ZERO_EUR));
        }

        @Test
        @DisplayName("a Livret A holds no securities at all")
        void livretARejectsAnyTrade() {
            Account livretA = accountOf(AccountType.LIVRET_A);
            livretA.deposit(Money.euros("10000.00"));

            assertThatExceptionOfType(PositionsNotSupportedException.class)
                    .isThrownBy(
                            () ->
                                    livretA.buy(
                                            TOTAL,
                                            InstrumentKind.EQUITY,
                                            Quantity.of(10),
                                            Price.euros("58.00"),
                                            Money.ZERO_EUR));
        }
    }

    // ------------------------------------------------------------------ trading

    @Nested
    @DisplayName("trading")
    class Trading {

        @Test
        @DisplayName("settles a purchase in cash and folds the fee into the cost basis")
        void buySettlesInCash() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("10000.00"));

            cto.buy(
                    TOTAL,
                    InstrumentKind.EQUITY,
                    Quantity.of(100),
                    Price.euros("58.00"),
                    Money.euros("4.90"));

            assertThat(cto.cashBalance()).isEqualTo(Money.euros("4195.10"));
            assertThat(cto.position(TOTAL))
                    .hasValueSatisfying(
                            position -> {
                                assertThat(position.quantity()).isEqualTo(Quantity.of(100));
                                // (100 * 58.00 + 4.90) / 100
                                assertThat(position.averageCost().value())
                                        .isEqualByComparingTo("58.049");
                                assertThat(position.totalCost()).isEqualTo(Money.euros("5804.90"));
                            });
        }

        @Test
        void refusesAPurchaseItCannotSettle() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("100.00"));

            assertThatExceptionOfType(InsufficientCashException.class)
                    .isThrownBy(
                            () ->
                                    cto.buy(
                                            TOTAL,
                                            InstrumentKind.EQUITY,
                                            Quantity.of(100),
                                            Price.euros("58.00"),
                                            Money.ZERO_EUR))
                    .withMessageContaining("100.00 EUR available");
        }

        @Test
        @DisplayName("a sale credits net proceeds and leaves the average cost untouched")
        void sellReducesThePositionAtUnchangedAverageCost() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("10000.00"));
            cto.buy(
                    TOTAL,
                    InstrumentKind.EQUITY,
                    Quantity.of(100),
                    Price.euros("58.00"),
                    Money.ZERO_EUR);

            cto.sell(TOTAL, Quantity.of(40), Price.euros("62.00"), Money.euros("2.90"));

            assertThat(cto.cashBalance()).isEqualTo(Money.euros("6677.10")); // 4200 + (2480 - 2.90)
            assertThat(cto.position(TOTAL))
                    .hasValueSatisfying(
                            position -> {
                                assertThat(position.quantity()).isEqualTo(Quantity.of(60));
                                assertThat(position.averageCost().value())
                                        .isEqualByComparingTo("58");
                            });
        }

        @Test
        @DisplayName(
                "selling the whole holding removes the position rather than leaving a zero one")
        void sellingEverythingRemovesThePosition() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("10000.00"));
            cto.buy(
                    TOTAL,
                    InstrumentKind.EQUITY,
                    Quantity.of(100),
                    Price.euros("58.00"),
                    Money.ZERO_EUR);

            cto.sell(TOTAL, Quantity.of(100), Price.euros("62.00"), Money.ZERO_EUR);

            assertThat(cto.position(TOTAL)).isEmpty();
            assertThat(cto.positions()).isEmpty();
        }

        @Test
        void refusesToSellMoreThanIsHeld() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("10000.00"));
            cto.buy(
                    TOTAL,
                    InstrumentKind.EQUITY,
                    Quantity.of(10),
                    Price.euros("58.00"),
                    Money.ZERO_EUR);

            assertThatExceptionOfType(InsufficientPositionException.class)
                    .isThrownBy(
                            () ->
                                    cto.sell(
                                            TOTAL,
                                            Quantity.of(11),
                                            Price.euros("62.00"),
                                            Money.ZERO_EUR))
                    .withMessageContaining("10 held");
        }

        @Test
        void refusesToSellSomethingNeverHeld() {
            Account cto = accountOf(AccountType.CTO);

            assertThatExceptionOfType(InsufficientPositionException.class)
                    .isThrownBy(
                            () ->
                                    cto.sell(
                                            TOTAL,
                                            Quantity.of(1),
                                            Price.euros("62.00"),
                                            Money.ZERO_EUR))
                    .withMessageContaining("0 held");
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("rehydration accepts state that today's rules would refuse to create")
        void rehydrationSkipsCeilingValidation() {
            // Fifteen years of capitalised interest legitimately puts this above the ceiling.
            Account restored =
                    Account.rehydrate(
                            AccountId.newId(),
                            "Livret A",
                            AccountType.LIVRET_A,
                            Money.EUR,
                            LocalDate.of(2009, 1, 1),
                            Money.euros("28430.11"),
                            Money.euros("22950.00"),
                            Money.euros("22950.00"),
                            List.of());

            assertThat(restored.cashBalance()).isEqualTo(Money.euros("28430.11"));
            assertThat(restored.remainingAllowance()).contains(Money.ZERO_EUR);
        }

        @Test
        void rejectsNonPositiveMovements() {
            Account cto = accountOf(AccountType.CTO);

            assertThatIllegalArgumentException().isThrownBy(() -> cto.deposit(Money.ZERO_EUR));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> cto.deposit(Money.euros("-10.00")));
            assertThatIllegalArgumentException().isThrownBy(() -> cto.withdraw(Money.ZERO_EUR));
        }

        @Test
        void refusesAWithdrawalItCannotFund() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("100.00"));

            assertThatExceptionOfType(InsufficientCashException.class)
                    .isThrownBy(() -> cto.withdraw(Money.euros("100.01")));
            assertThatExceptionOfType(InsufficientCashException.class)
                    .isThrownBy(() -> cto.debit(Money.euros("100.01")));
        }

        @Test
        void rejectsABlankLabel() {
            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () ->
                                    Account.open(
                                            AccountId.newId(),
                                            "  ",
                                            AccountType.CTO,
                                            Money.EUR,
                                            OPENED));
        }

        @Test
        @DisplayName("identity is the id, not the contents: two accounts can look identical")
        void equalityIsByIdentity() {
            AccountId id = AccountId.newId();
            Account first = Account.open(id, "PEA", AccountType.PEA, Money.EUR, OPENED);
            Account second = Account.open(id, "PEA", AccountType.PEA, Money.EUR, OPENED);
            second.deposit(Money.euros("1000.00"));

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
            assertThat(first).isNotEqualTo(accountOf(AccountType.PEA));
        }

        @Test
        @DisplayName("the positions list is a copy, so callers cannot mutate the aggregate")
        void exposesPositionsDefensively() {
            Account cto = accountOf(AccountType.CTO);
            cto.deposit(Money.euros("10000.00"));
            cto.buy(
                    TOTAL,
                    InstrumentKind.EQUITY,
                    Quantity.of(10),
                    Price.euros("58.00"),
                    Money.ZERO_EUR);

            List<Position> positions = cto.positions();

            assertThat(positions).hasSize(1);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> positions.add(positions.get(0)));
        }
    }
}
