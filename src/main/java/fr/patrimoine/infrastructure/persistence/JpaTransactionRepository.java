package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.application.port.out.TransactionRepository;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Transaction;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link TransactionRepository}. */
@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final SpringDataTransactionRepository jpa;
    private final EntityManager entityManager;

    JpaTransactionRepository(SpringDataTransactionRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public List<Transaction> findByAccount(AccountId accountId) {
        return jpa.findByAccount(accountId.value()).stream()
                .map(TransactionMapper::toDomain)
                .toList();
    }

    @Override
    public Map<AccountId, List<Transaction>> findByAccountsSince(
            Collection<AccountId> accountIds, LocalDate since) {
        return accountIds.isEmpty()
                ? Map.of()
                : byAccount(jpa.findByAccountsSince(uuids(accountIds), since));
    }

    @Override
    public Map<AccountId, List<Transaction>> findByAccounts(Collection<AccountId> accountIds) {
        return accountIds.isEmpty() ? Map.of() : byAccount(jpa.findByAccounts(uuids(accountIds)));
    }

    /**
     * {@code persist}, not Spring Data's {@code save}. The id is assigned by the domain, so {@code
     * save} cannot tell a new row from an existing one and falls back to {@code merge}: a {@code
     * SELECT} before every {@code INSERT}, on the one table that only ever grows.
     *
     * <p>Mandatory transaction: a movement is only ever written together with the account balance
     * it explains, and a ledger row committed on its own would contradict that balance.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction save(Transaction transaction) {
        entityManager.persist(TransactionMapper.toEntity(transaction));
        return transaction;
    }

    private static List<UUID> uuids(Collection<AccountId> accountIds) {
        return accountIds.stream().map(AccountId::value).toList();
    }

    private static Map<AccountId, List<Transaction>> byAccount(List<TransactionEntity> rows) {
        return rows.stream()
                .map(TransactionMapper::toDomain)
                .collect(
                        Collectors.groupingBy(
                                Transaction::accountId, LinkedHashMap::new, Collectors.toList()));
    }
}
