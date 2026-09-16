package fr.patrimoine.application.service;

import fr.patrimoine.application.port.out.QuoteRepository;
import fr.patrimoine.application.port.out.RegulatedRateProvider;
import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.application.port.out.ValuationRepository;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Position;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationMethod;
import fr.patrimoine.domain.valuation.ValuationContext;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Gathers the outside-world data a valuation needs, and nothing more.
 *
 * <p>This class exists because {@link ValuationContext} is the seam that keeps the domain pure: the
 * strategies never fetch anything, so someone has to fetch on their behalf. Doing it here rather
 * than in each use case means the loading rules are written once.
 *
 * <p>The interesting part is that it loads <em>per valuation family</em> rather than loading
 * everything for everyone. Quotes are only fetched for instruments actually held; this year's
 * movements are only loaded for regulated savings accounts, which are the only ones that accrue
 * interest; snapshots are only loaded for accounts that have no market price. A portfolio of three
 * passbooks issues no quote lookup at all. The naive alternative &mdash; fetch everything, let the
 * strategies ignore what they do not need &mdash; would work identically and cost a network round
 * trip per page view.
 */
@Component
public class ValuationContextAssembler {

    private final QuoteRepository quotes;
    private final ValuationRepository valuations;
    private final TransactionRepository transactions;
    private final RegulatedRateProvider rates;

    public ValuationContextAssembler(
            QuoteRepository quotes,
            ValuationRepository valuations,
            TransactionRepository transactions,
            RegulatedRateProvider rates) {
        this.quotes = quotes;
        this.valuations = valuations;
        this.transactions = transactions;
        this.rates = rates;
    }

    public ValuationContext assemble(Collection<Account> accounts, LocalDate asOf) {
        return new ValuationContext(
                asOf,
                quotesFor(accounts),
                snapshotsFor(accounts),
                movementsFor(accounts, asOf),
                rates.currentRates());
    }

    private Map<InstrumentId, Quote> quotesFor(Collection<Account> accounts) {
        Set<InstrumentId> held =
                accounts.stream()
                        .flatMap(account -> account.positions().stream())
                        .map(Position::instrument)
                        .collect(Collectors.toSet());
        return held.isEmpty() ? Map.of() : quotes.findLatest(held);
    }

    private Map<AccountId, Valuation> snapshotsFor(Collection<Account> accounts) {
        List<AccountId> ids = idsValuedBy(accounts, ValuationMethod.SNAPSHOT);
        return ids.isEmpty() ? Map.of() : valuations.latestByAccount(ids);
    }

    /**
     * Only this year's movements, because interest is capitalised on 31 December and the accrual
     * therefore restarts on 1 January. Loading the full history would be correct but would grow
     * without bound for no benefit.
     */
    private Map<AccountId, List<Transaction>> movementsFor(
            Collection<Account> accounts, LocalDate asOf) {
        List<AccountId> ids = idsValuedBy(accounts, ValuationMethod.REGULATED_SAVINGS);
        if (ids.isEmpty()) {
            return Map.of();
        }
        LocalDate startOfYear = LocalDate.of(asOf.getYear(), Month.JANUARY, 1);
        return transactions.findByAccountsSince(ids, startOfYear);
    }

    private static List<AccountId> idsValuedBy(
            Collection<Account> accounts, ValuationMethod method) {
        return accounts.stream()
                .filter(account -> account.type().valuationMethod() == method)
                .map(Account::id)
                .toList();
    }
}
