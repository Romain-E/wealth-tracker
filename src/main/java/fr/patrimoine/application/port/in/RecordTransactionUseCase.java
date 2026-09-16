package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.model.Transaction;

/**
 * Driving port: record a movement, letting the aggregate enforce whether it is legal.
 *
 * <p>This use case exists so the domain's invariants are reachable from the outside. Without it,
 * every ceiling check and eligibility rule in {@link fr.patrimoine.domain.model.Account} would be
 * unreachable code as far as the API is concerned &mdash; which is exactly the criticism a reviewer
 * would make of a read-only wealth API sitting on top of a rich aggregate.
 */
public interface RecordTransactionUseCase {

    /**
     * @return the persisted transaction
     * @throws fr.patrimoine.domain.error.DomainException if a business rule refuses the movement
     * @throws fr.patrimoine.application.error.ResourceNotFoundException if no such account exists
     */
    Transaction record(TransactionCommand command);
}
