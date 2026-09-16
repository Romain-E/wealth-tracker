package fr.patrimoine.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Spring Data queries over the ledger, always in date order then insertion order. */
interface SpringDataTransactionRepository extends Repository<TransactionEntity, UUID> {

    @Query(
            "select t from TransactionEntity t where t.accountId = :accountId"
                    + " order by t.tradeDate, t.sequenceNumber")
    List<TransactionEntity> findByAccount(UUID accountId);

    @Query(
            "select t from TransactionEntity t where t.accountId in :accountIds"
                    + " order by t.tradeDate, t.sequenceNumber")
    List<TransactionEntity> findByAccounts(Collection<UUID> accountIds);

    @Query(
            "select t from TransactionEntity t where t.accountId in :accountIds"
                    + " and t.tradeDate >= :since order by t.tradeDate, t.sequenceNumber")
    List<TransactionEntity> findByAccountsSince(Collection<UUID> accountIds, LocalDate since);
}
