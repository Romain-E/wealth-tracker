package fr.patrimoine.application.port.out;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Valuation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Driven port for valuation snapshots, used both as authoritative values and as chart history. */
public interface ValuationRepository {

    /** The most recent snapshot for each of the given accounts. Batched, for the same reason. */
    Map<AccountId, Valuation> latestByAccount(Collection<AccountId> accountIds);

    List<Valuation> findByAccountBetween(AccountId accountId, LocalDate from, LocalDate to);

    /** Every account's snapshots over a window, for the consolidated portfolio chart. */
    List<Valuation> findAllBetween(LocalDate from, LocalDate to);

    /**
     * Each account's last snapshot strictly before {@code date}: where every account stood when a
     * chart window opens. Without it, a flat appraised once a year would be missing from the start
     * of the curve until its next appraisal.
     */
    Map<AccountId, Valuation> latestByAccountBefore(LocalDate date);

    Valuation save(Valuation valuation);
}
