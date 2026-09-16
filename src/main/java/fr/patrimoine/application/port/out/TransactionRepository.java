package fr.patrimoine.application.port.out;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Transaction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Driven port for transaction history. */
public interface TransactionRepository {

    List<Transaction> findByAccount(AccountId accountId);

    /**
     * Movements for several accounts at once, from {@code since} inclusive.
     *
     * <p>Batched by design. Valuing a portfolio needs the year's movements for every regulated
     * savings account, and calling a single-account finder in a loop is how a page that renders in
     * 40 ms becomes a page that renders in 400 ms once someone opens six passbooks. The port makes
     * the batch the natural call, so the N+1 never gets written in the first place.
     */
    Map<AccountId, List<Transaction>> findByAccountsSince(
            Collection<AccountId> accountIds, LocalDate since);

    /** Full history for several accounts, for a consolidated performance calculation. */
    Map<AccountId, List<Transaction>> findByAccounts(Collection<AccountId> accountIds);

    Transaction save(Transaction transaction);
}
