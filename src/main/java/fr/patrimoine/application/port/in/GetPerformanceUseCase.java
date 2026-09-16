package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.model.AccountId;
import java.time.LocalDate;

/** Driving port: how has this done, at portfolio level or for one account. */
public interface GetPerformanceUseCase {

    /**
     * Consolidated performance across every account.
     *
     * @param from start of the chart window, inclusive
     * @param to end of the chart window, inclusive
     */
    PerformanceView portfolioPerformance(LocalDate from, LocalDate to);

    /**
     * @throws fr.patrimoine.application.error.ResourceNotFoundException if no such account exists
     */
    PerformanceView accountPerformance(AccountId accountId, LocalDate from, LocalDate to);
}
