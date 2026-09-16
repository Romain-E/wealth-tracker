package fr.patrimoine.application.service;

import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.application.port.in.AccountDetail;
import fr.patrimoine.application.port.in.GetAccountDetailUseCase;
import fr.patrimoine.application.port.in.GetPortfolioOverviewUseCase;
import fr.patrimoine.application.port.in.PositionDetail;
import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Position;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.domain.valuation.PortfolioValuation;
import fr.patrimoine.domain.valuation.PortfolioValuator;
import fr.patrimoine.domain.valuation.ValuationContext;
import fr.patrimoine.domain.valuation.ValuationResult;
import fr.patrimoine.domain.valuation.ValuationStrategy;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side use cases: what the portfolio is worth, and what is inside one envelope.
 *
 * <p>On the Spring annotations in this layer. The domain is uncompromisingly framework-free and
 * ArchUnit enforces that. The application layer is a deliberate exception, limited to two things:
 * the {@code @Service} stereotype and declarative transactions. Transaction demarcation genuinely
 * <em>is</em> a use-case concern &mdash; the use case is the unit of work, so it is the right place
 * to say so &mdash; and the alternative, hand-rolled transaction ports wrapping every call in a
 * lambda, buys purity at a real cost in noise. A separate ArchUnit rule keeps the line honest by
 * banning everything else: no {@code spring-web}, no {@code spring-data}, no JPA, no Jackson in
 * this package.
 *
 * <p>{@code readOnly = true} is not decorative either: it lets Hibernate skip dirty-checking on
 * flush and lets the driver route the query to a read replica if one is ever added.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioQueryService implements GetPortfolioOverviewUseCase, GetAccountDetailUseCase {

    /**
     * Instantiated, not injected. These are stateless pure functions over their arguments: there is
     * no second implementation, nothing to configure, and stubbing them in a test would only assert
     * that the stub was called. Injection would be ceremony that makes the DI graph larger and the
     * tests weaker.
     */
    private final PortfolioValuator valuator = new PortfolioValuator();

    private final AccountRepository accounts;
    private final ValuationContextAssembler contexts;
    private final Clock clock;

    public PortfolioQueryService(
            AccountRepository accounts, ValuationContextAssembler contexts, Clock clock) {
        this.accounts = accounts;
        this.contexts = contexts;
        this.clock = clock;
    }

    @Override
    public PortfolioValuation overview() {
        LocalDate asOf = LocalDate.now(clock);
        List<Account> all = accounts.findAll();
        return valuator.value(all, contexts.assemble(all, asOf));
    }

    @Override
    public AccountDetail detail(AccountId accountId) {
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Account", accountId.toString()));

        LocalDate asOf = LocalDate.now(clock);
        ValuationContext context = contexts.assemble(List.of(account), asOf);
        ValuationResult result = ValuationStrategy.forAccount(account).value(account, context);

        return new AccountDetail(
                account.id(),
                account.label(),
                account.type(),
                account.currency(),
                account.openedOn(),
                account.cashBalance(),
                result.value(),
                account.remainingAllowance(),
                account.positions().stream().map(position -> describe(position, context)).toList(),
                result.stale(),
                result.warnings());
    }

    /**
     * Mirrors {@code MarketValuation}'s fallback on purpose: an unpriced position shows at its cost
     * basis with a zero gain, never at zero value. The two must agree, or the sum of the position
     * rows on screen would not match the account total above them.
     */
    private static PositionDetail describe(Position position, ValuationContext context) {
        Optional<Quote> quote = context.quoteFor(position.instrument());
        return new PositionDetail(
                position.instrument(),
                position.kind(),
                position.quantity(),
                position.averageCost(),
                position.totalCost(),
                quote.map(Quote::price),
                quote.map(position::marketValue).orElseGet(position::totalCost),
                quote.map(position::unrealisedGain)
                        .orElseGet(() -> Money.zero(position.averageCost().currency())),
                quote.map(Quote::asOf),
                quote.map(Quote::stale).orElse(false));
    }
}
