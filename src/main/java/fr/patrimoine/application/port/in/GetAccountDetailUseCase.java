package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.model.AccountId;

/** Driving port: one account, its positions marked to market, and its remaining legal allowance. */
public interface GetAccountDetailUseCase {

    /**
     * @throws fr.patrimoine.application.error.ResourceNotFoundException if no such account exists
     */
    AccountDetail detail(AccountId accountId);
}
