package fr.patrimoine.domain.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountCategory;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PortfolioValuator: consolidating envelopes that value in different ways")
class PortfolioValuatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final Instant NOW = Instant.parse("2026-09-10T07:00:00Z");
    private static final InstrumentId WORLD = InstrumentId.of("IE00B4L5Y983");

    private final PortfolioValuator valuator = new PortfolioValuator();

    @Test
    @DisplayName("totals a mixed portfolio and breaks it down by account and by type")
    void totalsAMixedPortfolio() {
        Account livretA = savings(AccountType.LIVRET_A, "15000.00");
        Account ldds = savings(AccountType.LDDS, "5000.00");
        Account pea = Account.open(AccountId.newId(), "PEA", AccountType.PEA, Money.EUR, TODAY);
        pea.deposit(Money.euros("30000.00"));
        pea.buy(WORLD, InstrumentKind.ETF, Quantity.of(200), Price.euros("100.00"), Money.ZERO_EUR);
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
                        Map.of(WORLD, Quote.fresh(WORLD, Price.euros("125.00"), NOW)),
                        Map.of(
                                flat.id(),
                                new Valuation(
                                        flat.id(),
                                        TODAY,
                                        Money.euros("250000.00"),
                                        ValuationSource.MANUAL)),
                        Map.of(),
                        Map.of());

        PortfolioValuation portfolio = valuator.value(List.of(livretA, ldds, pea, flat), context);

        // 15 000 + 5 000 + (10 000 cash + 25 000 ETF) + 250 000
        assertThat(portfolio.total()).isEqualTo(Money.euros("305000.00"));
        assertThat(portfolio.asOf()).isEqualTo(TODAY);
        assertThat(portfolio.byAccount()).hasSize(4);
        assertThat(portfolio.byType())
                .containsEntry(AccountType.LIVRET_A, Money.euros("15000.00"))
                .containsEntry(AccountType.LDDS, Money.euros("5000.00"))
                .containsEntry(AccountType.PEA, Money.euros("35000.00"))
                .containsEntry(AccountType.REAL_ESTATE, Money.euros("250000.00"));
        // Passbooks, then investments, then property, whatever order the accounts came in.
        assertThat(portfolio.byCategory())
                .containsExactly(
                        Map.entry(AccountCategory.SAVINGS, Money.euros("20000.00")),
                        Map.entry(AccountCategory.INVESTMENTS, Money.euros("35000.00")),
                        Map.entry(AccountCategory.REAL_ESTATE, Money.euros("250000.00")));
    }

    @Test
    @DisplayName("a family with no account is left out of the breakdown rather than shown at zero")
    void leavesOutEmptyFamilies() {
        Account cto = savings(AccountType.CTO, "8000.00");
        Account livretA = savings(AccountType.LIVRET_A, "2000.00");

        PortfolioValuation portfolio =
                valuator.value(List.of(cto, livretA), ValuationContext.of(TODAY, Map.of()));

        assertThat(portfolio.byCategory())
                .containsExactly(
                        Map.entry(AccountCategory.SAVINGS, Money.euros("2000.00")),
                        Map.entry(AccountCategory.INVESTMENTS, Money.euros("8000.00")));
    }

    @Test
    @DisplayName("groups several accounts of the same type into one line of the breakdown")
    void groupsAccountsOfTheSameType() {
        Account first = savings(AccountType.LIVRET_A, "10000.00");
        Account second = savings(AccountType.LIVRET_A, "12000.00");

        PortfolioValuation portfolio =
                valuator.value(List.of(first, second), ValuationContext.of(TODAY, Map.of()));

        assertThat(portfolio.byAccount()).hasSize(2);
        assertThat(portfolio.byType()).containsExactly(entry(AccountType.LIVRET_A, "22000.00"));
    }

    @Test
    @DisplayName("shares are computed against the final total, so they add up to 100%")
    void sharesAddUpToOneHundredPercent() {
        // Uncapped envelopes here on purpose: 25 000 EUR would not fit in a Livret A, and the
        // aggregate is right to refuse it.
        Account first = savings(AccountType.CTO, "25000.00");
        Account second = savings(AccountType.PEA, "75000.00");

        PortfolioValuation portfolio =
                valuator.value(List.of(first, second), ValuationContext.of(TODAY, Map.of()));

        assertThat(portfolio.byAccount())
                .extracting(AccountValuation::share)
                .containsExactly(Percentage.ofPoints("25.0000"), Percentage.ofPoints("75.0000"));

        BigDecimal sum =
                portfolio.byAccount().stream()
                        .map(AccountValuation::share)
                        .map(Percentage::value)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("staleness and warnings bubble up from any single account to the portfolio")
    void aggregatesStalenessAndWarnings() {
        Account pea = Account.open(AccountId.newId(), "PEA", AccountType.PEA, Money.EUR, TODAY);
        pea.deposit(Money.euros("10000.00"));
        pea.buy(WORLD, InstrumentKind.ETF, Quantity.of(50), Price.euros("100.00"), Money.ZERO_EUR);
        Account flat =
                Account.open(
                        AccountId.newId(),
                        "Appartement",
                        AccountType.REAL_ESTATE,
                        Money.EUR,
                        TODAY);

        ValuationContext context =
                ValuationContext.of(
                        TODAY,
                        Map.of(WORLD, Quote.fresh(WORLD, Price.euros("110.00"), NOW).asStale()));

        PortfolioValuation portfolio = valuator.value(List.of(pea, flat), context);

        assertThat(portfolio.stale()).isTrue();
        assertThat(portfolio.isComplete()).isFalse();
        assertThat(portfolio.warnings()).singleElement().asString().contains("Appartement");
    }

    @Test
    void valuesAnEmptyPortfolioAtZero() {
        PortfolioValuation portfolio =
                valuator.value(List.of(), ValuationContext.of(TODAY, Map.of()));

        assertThat(portfolio.total()).isEqualTo(Money.ZERO_EUR);
        assertThat(portfolio.byAccount()).isEmpty();
        assertThat(portfolio.stale()).isFalse();
    }

    private static Account savings(AccountType type, String amount) {
        Account account = Account.open(AccountId.newId(), type.label(), type, Money.EUR, TODAY);
        account.deposit(Money.euros(amount));
        return account;
    }

    private static Entry<AccountType, Money> entry(AccountType type, String amount) {
        return Map.entry(type, Money.euros(amount));
    }
}
