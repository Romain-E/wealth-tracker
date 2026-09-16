package fr.patrimoine.application.service;

import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.application.port.in.GetPerformanceUseCase;
import fr.patrimoine.application.port.in.PerformanceView;
import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.application.port.out.ValuationRepository;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.performance.PerformanceCalculator;
import fr.patrimoine.domain.valuation.PortfolioValuator;
import fr.patrimoine.domain.valuation.ValuationContext;
import fr.patrimoine.domain.valuation.ValuationStrategy;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side use cases for performance.
 *
 * <p>Both methods do the same two things from different sources: establish today's value, then
 * measure it against what went in. Note that the current value is <em>computed</em>, not read from
 * the valuation table &mdash; the latest stored snapshot may be hours old, and a return figure that
 * silently lags the total shown next to it on the same screen is the sort of inconsistency users
 * report as a bug. Stored snapshots are used only for the historical curve, which is the one thing
 * that genuinely cannot be recomputed from today's prices.
 */
@Service
@Transactional(readOnly = true)
public class PerformanceQueryService implements GetPerformanceUseCase {

    private final PerformanceCalculator calculator = new PerformanceCalculator();
    private final PortfolioValuator valuator = new PortfolioValuator();

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final ValuationRepository valuations;
    private final ValuationContextAssembler contexts;
    private final Clock clock;

    public PerformanceQueryService(
            AccountRepository accounts,
            TransactionRepository transactions,
            ValuationRepository valuations,
            ValuationContextAssembler contexts,
            Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.valuations = valuations;
        this.contexts = contexts;
        this.clock = clock;
    }

    @Override
    public PerformanceView portfolioPerformance(LocalDate from, LocalDate to) {
        LocalDate asOf = LocalDate.now(clock);
        List<Account> all = accounts.findAll();

        Money currentValue = valuator.value(all, contexts.assemble(all, asOf)).total();

        List<Transaction> history =
                transactions
                        .findByAccounts(all.stream().map(Account::id).toList())
                        .values()
                        .stream()
                        .flatMap(List::stream)
                        .toList();

        List<Valuation> snapshots =
                new ArrayList<>(valuations.latestByAccountBefore(from).values());
        snapshots.addAll(valuations.findAllBetween(from, to));

        return new PerformanceView(
                calculator.report(history, currentValue, asOf),
                calculator.aggregateSeries(snapshots, from));
    }

    @Override
    public PerformanceView accountPerformance(AccountId accountId, LocalDate from, LocalDate to) {
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Account", accountId.toString()));

        LocalDate asOf = LocalDate.now(clock);
        ValuationContext context = contexts.assemble(List.of(account), asOf);
        Money currentValue = ValuationStrategy.forAccount(account).value(account, context).value();

        return new PerformanceView(
                calculator.report(transactions.findByAccount(accountId), currentValue, asOf),
                calculator.series(valuations.findByAccountBetween(accountId, from, to)));
    }
}
