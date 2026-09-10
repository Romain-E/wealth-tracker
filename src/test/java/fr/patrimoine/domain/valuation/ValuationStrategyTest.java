package fr.patrimoine.domain.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.TransactionType;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationMethod;
import fr.patrimoine.domain.model.ValuationSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Valuation strategies")
class ValuationStrategyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final Instant NOW = Instant.parse("2026-09-10T07:00:00Z");
    private static final InstrumentId TOTAL = InstrumentId.of("FR0000120271");
    private static final InstrumentId WORLD = InstrumentId.of("IE00B4L5Y983");

    @Test
    @DisplayName("each account type resolves to the strategy its valuation method dictates")
    void resolvesTheRightStrategyPerType() {
        assertThat(ValuationStrategy.forMethod(ValuationMethod.MARKET))
                .isInstanceOf(MarketValuation.class);
        assertThat(ValuationStrategy.forMethod(ValuationMethod.REGULATED_SAVINGS))
                .isInstanceOf(RegulatedSavingsValuation.class);
        assertThat(ValuationStrategy.forMethod(ValuationMethod.SNAPSHOT))
                .isInstanceOf(SnapshotValuation.class);

        for (AccountType type : AccountType.values()) {
            Account account = Account.open(AccountId.newId(), type.label(), type, Money.EUR, TODAY);
            assertThat(ValuationStrategy.forAccount(account)).isNotNull();
        }
    }

    // -------------------------------------------------------------------- market

    @Nested
    @DisplayName("MarketValuation")
    class Market {

        private Account fundedCto() {
            Account cto = Account.open(AccountId.newId(), "CTO", AccountType.CTO, Money.EUR, TODAY);
            cto.deposit(Money.euros("20000.00"));
            cto.buy(
                    TOTAL,
                    InstrumentKind.EQUITY,
                    Quantity.of(100),
                    Price.euros("50.00"),
                    Money.ZERO_EUR);
            cto.buy(
                    WORLD,
                    InstrumentKind.ETF,
                    Quantity.of(10),
                    Price.euros("100.00"),
                    Money.ZERO_EUR);
            return cto;
        }

        @Test
        @DisplayName("marks positions to market and adds the uninvested cash")
        void marksToMarketAndAddsCash() {
            Account cto = fundedCto(); // 14 000 cash left, 100 TTE, 10 IWDA
            ValuationContext context =
                    ValuationContext.of(
                            TODAY,
                            Map.of(
                                    TOTAL, Quote.fresh(TOTAL, Price.euros("58.00"), NOW),
                                    WORLD, Quote.fresh(WORLD, Price.euros("110.00"), NOW)));

            ValuationResult result = MarketValuation.INSTANCE.value(cto, context);

            // 14 000 cash + 5 800 + 1 100
            assertThat(result.value()).isEqualTo(Money.euros("20900.00"));
            assertThat(result.stale()).isFalse();
            assertThat(result.isComplete()).isTrue();
        }

        @Test
        @DisplayName("falls back to cost basis for an unpriced instrument, and says so")
        void fallsBackToCostBasisWhenAQuoteIsMissing() {
            Account cto = fundedCto();
            ValuationContext context =
                    ValuationContext.of(
                            TODAY, Map.of(TOTAL, Quote.fresh(TOTAL, Price.euros("58.00"), NOW)));

            ValuationResult result = MarketValuation.INSTANCE.value(cto, context);

            // The ETF is valued at what it cost (1 000), not at zero, which would fake a total
            // loss.
            assertThat(result.value()).isEqualTo(Money.euros("20800.00"));
            assertThat(result.warnings()).singleElement().asString().contains("IE00B4L5Y983");
            assertThat(result.isComplete()).isFalse();
        }

        @Test
        @DisplayName("a single stale quote makes the whole valuation stale")
        void propagatesStaleness() {
            Account cto = fundedCto();
            ValuationContext context =
                    ValuationContext.of(
                            TODAY,
                            Map.of(
                                    TOTAL, Quote.fresh(TOTAL, Price.euros("58.00"), NOW),
                                    WORLD,
                                            Quote.fresh(WORLD, Price.euros("110.00"), NOW)
                                                    .asStale()));

            ValuationResult result = MarketValuation.INSTANCE.value(cto, context);

            assertThat(result.stale()).isTrue();
            assertThat(result.value()).isEqualTo(Money.euros("20900.00"));
        }

        @Test
        void valuesAnEmptyAccountAtItsCashBalance() {
            Account cto = Account.open(AccountId.newId(), "CTO", AccountType.CTO, Money.EUR, TODAY);
            cto.deposit(Money.euros("500.00"));

            assertThat(
                            MarketValuation.INSTANCE
                                    .value(cto, ValuationContext.of(TODAY, Map.of()))
                                    .value())
                    .isEqualTo(Money.euros("500.00"));
        }
    }

    // ------------------------------------------------------------------ snapshot

    @Nested
    @DisplayName("SnapshotValuation")
    class Snapshot {

        @Test
        void usesTheLatestRecordedValuation() {
            Account flat =
                    Account.open(
                            AccountId.newId(),
                            "Appartement",
                            AccountType.REAL_ESTATE,
                            Money.EUR,
                            TODAY);
            ValuationContext context =
                    new ValuationContext(
                            TODAY,
                            Map.of(),
                            Map.of(
                                    flat.id(),
                                    new Valuation(
                                            flat.id(),
                                            TODAY,
                                            Money.euros("315000.00"),
                                            ValuationSource.MANUAL)),
                            Map.of(),
                            Map.of());

            assertThat(SnapshotValuation.INSTANCE.value(flat, context).value())
                    .isEqualTo(Money.euros("315000.00"));
        }

        @Test
        @DisplayName(
                "an unvalued account is worth zero and warns, rather than vanishing from the total")
        void warnsWhenNoValuationExists() {
            Account contract =
                    Account.open(
                            AccountId.newId(),
                            "Assurance vie",
                            AccountType.ASSURANCE_VIE,
                            Money.EUR,
                            TODAY);

            ValuationResult result =
                    SnapshotValuation.INSTANCE.value(
                            contract, ValuationContext.of(TODAY, Map.of()));

            assertThat(result.value()).isEqualTo(Money.ZERO_EUR);
            assertThat(result.warnings()).singleElement().asString().contains("Assurance vie");
        }
    }

    // --------------------------------------------------------- regulated savings

    @Nested
    @DisplayName("RegulatedSavingsValuation")
    class RegulatedSavings {

        @Test
        @DisplayName("adds interest earned but not yet capitalised to the balance")
        void addsAccruedInterestToTheBalance() {
            Account livretA =
                    Account.rehydrate(
                            AccountId.newId(),
                            "Livret A",
                            AccountType.LIVRET_A,
                            Money.EUR,
                            LocalDate.of(2015, 1, 1),
                            Money.euros("10000.00"),
                            Money.euros("10000.00"),
                            Money.euros("10000.00"),
                            List.of());

            // No movement this year: a clean 10 000 EUR earning 3% from 1 January to 1 July.
            ValuationContext context =
                    new ValuationContext(
                            LocalDate.of(2026, 7, 1),
                            Map.of(),
                            Map.of(),
                            Map.of(),
                            Map.of(AccountType.LIVRET_A, Percentage.ofPoints("3.0000")));

            ValuationResult result = RegulatedSavingsValuation.INSTANCE.value(livretA, context);

            // Twelve completed fortnights: 10 000 * 3% * 12/24.
            assertThat(result.value()).isEqualTo(Money.euros("10150.00"));
            assertThat(result.isComplete()).isTrue();
        }

        @Test
        @DisplayName("rewinds this year's movements to find the balance interest started from")
        void reconstructsTheOpeningBalanceFromMovements() {
            AccountId id = AccountId.newId();
            Account livretA =
                    Account.rehydrate(
                            id,
                            "Livret A",
                            AccountType.LIVRET_A,
                            Money.EUR,
                            LocalDate.of(2015, 1, 1),
                            Money.euros(
                                    "15000.00"), // 10 000 at 1 Jan plus a 5 000 payment in February
                            Money.euros("15000.00"),
                            Money.euros("15000.00"),
                            List.of());

            ValuationContext context =
                    new ValuationContext(
                            LocalDate.of(2026, 7, 1),
                            Map.of(),
                            Map.of(),
                            Map.of(
                                    id,
                                    List.of(
                                            Transaction.cashMovement(
                                                    id,
                                                    TransactionType.DEPOSIT,
                                                    LocalDate.of(2026, 2, 10),
                                                    Money.euros("5000.00")))),
                            Map.of(AccountType.LIVRET_A, Percentage.ofPoints("3.0000")));

            ValuationResult result = RegulatedSavingsValuation.INSTANCE.value(livretA, context);

            // 10 000 for 12 fortnights + 5 000 from 16 February, i.e. 9 of them.
            // 10 000*0.03/24*12 = 150.00 ; 5 000*0.03/24*9 = 56.25
            assertThat(result.value()).isEqualTo(Money.euros("15206.25"));
        }

        @Test
        @DisplayName("without a published rate it reports the bare balance and warns")
        void warnsWhenNoRateIsAvailable() {
            Account livretA =
                    Account.open(
                            AccountId.newId(), "Livret A", AccountType.LIVRET_A, Money.EUR, TODAY);
            livretA.deposit(Money.euros("1000.00"));

            ValuationResult result =
                    RegulatedSavingsValuation.INSTANCE.value(
                            livretA, ValuationContext.of(TODAY, Map.of()));

            assertThat(result.value()).isEqualTo(Money.euros("1000.00"));
            assertThat(result.warnings()).singleElement().asString().contains("No published rate");
        }
    }
}
