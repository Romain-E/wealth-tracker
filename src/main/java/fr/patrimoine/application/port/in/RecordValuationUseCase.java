package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Valuation;
import java.time.LocalDate;

/**
 * Driving port: record what an unpriceable asset is currently worth.
 *
 * <p>The counterpart to market valuation. Property and euro-denominated life insurance have no
 * quote, so their value arrives by appraisal or by statement, and this is how it gets in.
 */
public interface RecordValuationUseCase {

    Valuation record(RecordValuationCommand command);

    record RecordValuationCommand(AccountId accountId, LocalDate on, Money value) {}
}
