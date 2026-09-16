package fr.patrimoine.application.service;

import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.application.port.in.RecordTransactionUseCase;
import fr.patrimoine.application.port.in.RecordValuationUseCase;
import fr.patrimoine.application.port.in.TransactionCommand;
import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.application.port.out.ValuationRepository;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.TransactionType;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side use cases.
 *
 * <p>Two things worth defending here.
 *
 * <p><b>The aggregate is mutated before anything is written.</b> Every branch calls the {@link
 * Account} method first and only then saves. If a rule refuses -- a Livret A over its ceiling,
 * crypto in a PEA, not enough cash to settle -- the exception is thrown before a single write, so
 * there is no partial state to unwind. The transaction boundary is the belt to that braces: the
 * account update and the movement row commit together or not at all, because a saved balance with
 * no matching movement would quietly corrupt every future performance calculation.
 *
 * <p><b>The switch has no default branch.</b> {@link TransactionCommand} is sealed, so the compiler
 * verifies this switch is exhaustive. A fifth kind of movement will not compile until it is handled
 * here, which is strictly better than a runtime failure found by a user.
 */
@Service
@Transactional
public class TransactionCommandService implements RecordTransactionUseCase, RecordValuationUseCase {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final ValuationRepository valuations;

    public TransactionCommandService(
            AccountRepository accounts,
            TransactionRepository transactions,
            ValuationRepository valuations) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.valuations = valuations;
    }

    @Override
    public Transaction record(TransactionCommand command) {
        Account account = load(command.accountId());

        Transaction movement =
                switch (command) {
                    case TransactionCommand.Deposit deposit -> {
                        account.deposit(deposit.amount());
                        yield Transaction.cashMovement(
                                account.id(),
                                TransactionType.DEPOSIT,
                                deposit.date(),
                                deposit.amount());
                    }
                    case TransactionCommand.Withdrawal withdrawal -> {
                        account.withdraw(withdrawal.amount());
                        // Signed from the account's point of view: money leaving is negative.
                        yield Transaction.cashMovement(
                                account.id(),
                                TransactionType.WITHDRAWAL,
                                withdrawal.date(),
                                withdrawal.amount().negated());
                    }
                    case TransactionCommand.Purchase purchase -> {
                        account.buy(
                                purchase.instrument(),
                                purchase.kind(),
                                purchase.quantity(),
                                purchase.unitPrice(),
                                purchase.fees());
                        Money cost =
                                purchase.unitPrice()
                                        .times(purchase.quantity())
                                        .plus(purchase.fees());
                        yield Transaction.trade(
                                account.id(),
                                TransactionType.BUY,
                                purchase.date(),
                                cost.negated(),
                                purchase.instrument(),
                                purchase.quantity(),
                                purchase.unitPrice());
                    }
                    case TransactionCommand.Sale sale -> {
                        account.sell(
                                sale.instrument(), sale.quantity(), sale.unitPrice(), sale.fees());
                        Money proceeds = sale.unitPrice().times(sale.quantity()).minus(sale.fees());
                        yield Transaction.trade(
                                account.id(),
                                TransactionType.SELL,
                                sale.date(),
                                proceeds,
                                sale.instrument(),
                                sale.quantity(),
                                sale.unitPrice());
                    }
                };

        accounts.save(account);
        return transactions.save(movement);
    }

    @Override
    public Valuation record(RecordValuationCommand command) {
        Account account = load(command.accountId());
        return valuations.save(
                new Valuation(account.id(), command.on(), command.value(), ValuationSource.MANUAL));
    }

    private Account load(AccountId accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));
    }
}
