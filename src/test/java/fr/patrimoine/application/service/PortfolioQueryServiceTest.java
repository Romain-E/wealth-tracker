package fr.patrimoine.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.application.port.in.AccountDetail;
import fr.patrimoine.application.port.in.PositionDetail;
import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.application.port.out.QuoteRepository;
import fr.patrimoine.application.port.out.RegulatedRateProvider;
import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.application.port.out.ValuationRepository;
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
import fr.patrimoine.domain.valuation.PortfolioValuation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioQueryService")
class PortfolioQueryServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Instant FIXED = Instant.parse("2026-07-01T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);
    private static final InstrumentId WORLD = InstrumentId.of("IE00B4L5Y983");
    private static final InstrumentId TOTAL = InstrumentId.of("FR0000120271");

    @Mock private AccountRepository accounts;
    @Mock private QuoteRepository quotes;
    @Mock private ValuationRepository valuations;
    @Mock private TransactionRepository transactions;
    @Mock private RegulatedRateProvider rates;

    private PortfolioQueryService service;

    @BeforeEach
    void setUp() {
        // A fixed clock, so an accrued-interest figure can be asserted exactly instead of
        // depending on whatever today happens to be when CI runs.
        service =
                new PortfolioQueryService(
                        accounts,
                        new ValuationContextAssembler(quotes, valuations, transactions, rates),
                        Clock.fixed(FIXED, PARIS));
    }

    private static Account pea() {
        Account pea =
                Account.open(
                        AccountId.newId(),
                        "PEA",
                        AccountType.PEA,
                        Money.EUR,
                        LocalDate.of(2019, 5, 2));
        pea.deposit(Money.euros("30000.00"));
        pea.buy(WORLD, InstrumentKind.ETF, Quantity.of(200), Price.euros("100.00"), Money.ZERO_EUR);
        return pea;
    }

    @Nested
    @DisplayName("overview")
    class Overview {

        @Test
        @DisplayName("totals the portfolio using prices fetched through the quote port")
        void totalsThePortfolio() {
            Account account = pea();
            when(accounts.findAll()).thenReturn(List.of(account));
            when(rates.currentRates()).thenReturn(Map.of());
            when(quotes.findLatest(anyCollection()))
                    .thenReturn(Map.of(WORLD, Quote.fresh(WORLD, Price.euros("125.00"), FIXED)));

            PortfolioValuation overview = service.overview();

            // 10 000 uninvested cash + 200 units at 125.00
            assertThat(overview.total()).isEqualTo(Money.euros("35000.00"));
            assertThat(overview.asOf()).isEqualTo(TODAY);
            assertThat(overview.byType()).containsEntry(AccountType.PEA, Money.euros("35000.00"));
        }

        @Test
        @DisplayName("reads the date from the injected clock, in Paris time")
        void datesTheValuationFromTheClock() {
            when(accounts.findAll()).thenReturn(List.of());
            when(rates.currentRates()).thenReturn(Map.of());

            assertThat(service.overview().asOf()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("an empty portfolio is worth zero rather than failing")
        void handlesAnEmptyPortfolio() {
            when(accounts.findAll()).thenReturn(List.of());
            when(rates.currentRates()).thenReturn(Map.of());

            PortfolioValuation overview = service.overview();

            assertThat(overview.total()).isEqualTo(Money.ZERO_EUR);
            assertThat(overview.byAccount()).isEmpty();
        }

        @Test
        @DisplayName(
                "a stale price makes the whole overview stale, and the flag reaches the caller")
        void propagatesStalenessFromTheQuotePort() {
            Account account = pea();
            when(accounts.findAll()).thenReturn(List.of(account));
            when(rates.currentRates()).thenReturn(Map.of());
            when(quotes.findLatest(anyCollection()))
                    .thenReturn(
                            Map.of(
                                    WORLD,
                                    Quote.fresh(WORLD, Price.euros("125.00"), FIXED).asStale()));

            assertThat(service.overview().stale()).isTrue();
        }

        @Test
        @DisplayName("accrued interest on a passbook is included, using the published rate")
        void includesAccruedInterestOnRegulatedSavings() {
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
            when(accounts.findAll()).thenReturn(List.of(livretA));
            when(rates.currentRates())
                    .thenReturn(Map.of(AccountType.LIVRET_A, Percentage.ofPoints("3.0000")));
            when(transactions.findByAccountsSince(
                            anyCollection(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Map.of());

            // 1 January to 1 July is twelve completed fortnights: 10 000 * 3% * 12/24.
            assertThat(service.overview().total()).isEqualTo(Money.euros("10150.00"));
        }
    }

    @Nested
    @DisplayName("detail")
    class Detail {

        @Test
        @DisplayName("assembles positions with cost, market value and unrealised gain")
        void assemblesPositionDetails() {
            Account account = pea();
            when(accounts.findById(account.id())).thenReturn(Optional.of(account));
            when(rates.currentRates()).thenReturn(Map.of());
            when(quotes.findLatest(anyCollection()))
                    .thenReturn(Map.of(WORLD, Quote.fresh(WORLD, Price.euros("125.00"), FIXED)));

            AccountDetail detail = service.detail(account.id());

            assertThat(detail.label()).isEqualTo("PEA");
            assertThat(detail.cashBalance()).isEqualTo(Money.euros("10000.00"));
            assertThat(detail.totalValue()).isEqualTo(Money.euros("35000.00"));
            assertThat(detail.positions())
                    .singleElement()
                    .satisfies(
                            position -> {
                                assertThat(position.instrument()).isEqualTo(WORLD);
                                assertThat(position.totalCost()).isEqualTo(Money.euros("20000.00"));
                                assertThat(position.marketValue())
                                        .isEqualTo(Money.euros("25000.00"));
                                assertThat(position.unrealisedGain())
                                        .isEqualTo(Money.euros("5000.00"));
                                assertThat(position.currentPrice()).contains(Price.euros("125.00"));
                                assertThat(position.pricedAt()).contains(FIXED);
                                assertThat(position.isPriced()).isTrue();
                            });
        }

        @Test
        @DisplayName(
                "reports the remaining legal allowance, and its absence for uncapped envelopes")
        void reportsTheRemainingAllowance() {
            Account pea = pea();
            when(accounts.findById(pea.id())).thenReturn(Optional.of(pea));
            when(rates.currentRates()).thenReturn(Map.of());
            when(quotes.findLatest(anyCollection())).thenReturn(Map.of());

            // 150 000 ceiling less the 30 000 paid in.
            assertThat(service.detail(pea.id()).remainingAllowance())
                    .contains(Money.euros("120000.00"));
        }

        @Test
        @DisplayName("an uncapped envelope reports no allowance, which is not the same as zero")
        void distinguishesNoCeilingFromNoRoomLeft() {
            Account cto =
                    Account.open(
                            AccountId.newId(),
                            "CTO",
                            AccountType.CTO,
                            Money.EUR,
                            LocalDate.of(2021, 1, 1));
            cto.deposit(Money.euros("500000.00"));
            when(accounts.findById(cto.id())).thenReturn(Optional.of(cto));
            when(rates.currentRates()).thenReturn(Map.of());

            assertThat(service.detail(cto.id()).remainingAllowance()).isEmpty();
        }

        @Test
        @DisplayName("an unpriced position shows at cost with a zero gain, never at zero value")
        void fallsBackToCostBasisForAnUnpricedPosition() {
            Account account = pea();
            when(accounts.findById(account.id())).thenReturn(Optional.of(account));
            when(rates.currentRates()).thenReturn(Map.of());
            when(quotes.findLatest(anyCollection())).thenReturn(Map.of());

            AccountDetail detail = service.detail(account.id());

            PositionDetail position = detail.positions().getFirst();
            assertThat(position.currentPrice()).isEmpty();
            assertThat(position.isPriced()).isFalse();
            assertThat(position.marketValue()).isEqualTo(Money.euros("20000.00"));
            assertThat(position.unrealisedGain()).isEqualTo(Money.ZERO_EUR);
            assertThat(detail.warnings()).singleElement().asString().contains("IE00B4L5Y983");

            // The rows must sum to the account total shown above them, or the screen contradicts
            // itself: 10 000 cash + 20 000 at cost.
            assertThat(detail.totalValue()).isEqualTo(Money.euros("30000.00"));
        }

        @Test
        @DisplayName("only the requested account's instruments are priced")
        void doesNotPriceTheWholePortfolioForOneAccount() {
            Account account = pea();
            when(accounts.findById(account.id())).thenReturn(Optional.of(account));
            when(rates.currentRates()).thenReturn(Map.of());
            when(quotes.findLatest(anyCollection())).thenReturn(Map.of());

            service.detail(account.id());

            org.mockito.ArgumentCaptor<java.util.Collection<InstrumentId>> requested =
                    org.mockito.ArgumentCaptor.captor();
            org.mockito.Mockito.verify(quotes).findLatest(requested.capture());
            assertThat(requested.getValue()).containsExactly(WORLD).doesNotContain(TOTAL);
        }

        @Test
        void rejectsAnUnknownAccount() {
            AccountId missing = AccountId.newId();
            when(accounts.findById(missing)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> service.detail(missing))
                    .withMessageContaining("Account");
        }
    }
}
