package fr.patrimoine.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import fr.patrimoine.domain.valuation.ValuationContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValuationContextAssembler: loads per valuation family, not everything for everyone")
class ValuationContextAssemblerTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 7, 1);
    private static final InstrumentId WORLD = InstrumentId.of("IE00B4L5Y983");

    @Mock private QuoteRepository quotes;
    @Mock private ValuationRepository valuations;
    @Mock private TransactionRepository transactions;
    @Mock private RegulatedRateProvider rates;

    private ValuationContextAssembler assembler() {
        return new ValuationContextAssembler(quotes, valuations, transactions, rates);
    }

    private static Account account(AccountType type) {
        return Account.open(
                AccountId.newId(), type.label(), type, Money.EUR, LocalDate.of(2020, 1, 1));
    }

    @Test
    @DisplayName("asks for quotes only for instruments that are actually held")
    void requestsQuotesOnlyForHeldInstruments() {
        Account pea = account(AccountType.PEA);
        pea.deposit(Money.euros("10000.00"));
        pea.buy(WORLD, InstrumentKind.ETF, Quantity.of(10), Price.euros("100.00"), Money.ZERO_EUR);
        when(rates.currentRates()).thenReturn(Map.of());
        when(quotes.findLatest(anyMap())).thenReturn(Map.of());

        assembler().assemble(List.of(pea), ASOF);

        ArgumentCaptor<Map<InstrumentId, InstrumentKind>> requested = ArgumentCaptor.captor();
        verify(quotes).findLatest(requested.capture());
        // The kind travels with the id: it is what routes the lookup to the right source.
        assertThat(requested.getValue()).containsExactly(Map.entry(WORLD, InstrumentKind.ETF));
    }

    @Test
    @DisplayName("a portfolio of passbooks issues no quote lookup at all")
    void skipsTheQuoteLookupEntirelyWhenNothingIsHeld() {
        Account livretA = account(AccountType.LIVRET_A);
        livretA.deposit(Money.euros("10000.00"));
        when(rates.currentRates())
                .thenReturn(Map.of(AccountType.LIVRET_A, Percentage.ofPoints("1.7000")));
        when(transactions.findByAccountsSince(anyCollection(), any())).thenReturn(Map.of());

        assembler().assemble(List.of(livretA), ASOF);

        verifyNoInteractions(quotes);
    }

    @Test
    @DisplayName("loads this year's movements only for accounts that accrue interest")
    void loadsMovementsOnlyForRegulatedSavings() {
        Account livretA = account(AccountType.LIVRET_A);
        Account cto = account(AccountType.CTO);
        when(rates.currentRates()).thenReturn(Map.of());
        when(transactions.findByAccountsSince(anyCollection(), any())).thenReturn(Map.of());

        assembler().assemble(List.of(livretA, cto), ASOF);

        ArgumentCaptor<Collection<AccountId>> ids = ArgumentCaptor.captor();
        ArgumentCaptor<LocalDate> since = ArgumentCaptor.captor();
        verify(transactions).findByAccountsSince(ids.capture(), since.capture());

        assertThat(ids.getValue()).containsExactly(livretA.id());
        // Interest capitalises on 31 December, so the accrual window starts on 1 January.
        assertThat(since.getValue()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("loads snapshots only for accounts that have no market price")
    void loadsSnapshotsOnlyForUnpriceableAccounts() {
        Account flat = account(AccountType.REAL_ESTATE);
        Account contract = account(AccountType.ASSURANCE_VIE);
        Account cto = account(AccountType.CTO);
        when(rates.currentRates()).thenReturn(Map.of());
        when(valuations.latestByAccount(anyCollection())).thenReturn(Map.of());

        assembler().assemble(List.of(flat, contract, cto), ASOF);

        ArgumentCaptor<Collection<AccountId>> ids = ArgumentCaptor.captor();
        verify(valuations).latestByAccount(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(flat.id(), contract.id());
    }

    @Test
    @DisplayName("an empty portfolio touches no repository except the rate table")
    void doesNothingForAnEmptyPortfolio() {
        when(rates.currentRates()).thenReturn(Map.of());

        ValuationContext context = assembler().assemble(List.of(), ASOF);

        assertThat(context.asOf()).isEqualTo(ASOF);
        assertThat(context.quotes()).isEmpty();
        verifyNoInteractions(quotes, valuations, transactions);
    }

    @Test
    @DisplayName("passes the published rates straight through to the domain")
    void forwardsTheRegulatedRates() {
        Account livretA = account(AccountType.LIVRET_A);
        Map<AccountType, Percentage> published =
                Map.of(AccountType.LIVRET_A, Percentage.ofPoints("1.7000"));
        when(rates.currentRates()).thenReturn(published);
        when(transactions.findByAccountsSince(anyCollection(), any())).thenReturn(Map.of());

        ValuationContext context = assembler().assemble(List.of(livretA), ASOF);

        assertThat(context.rateFor(AccountType.LIVRET_A)).contains(Percentage.ofPoints("1.7000"));
        verify(quotes, never()).findLatest(anyMap());
    }
}
