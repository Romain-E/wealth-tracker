package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link AccountRepository}.
 *
 * <p><b>Why the account carries a version.</b> A regulatory ceiling is a read-modify-write rule:
 * read the payments so far, check the new deposit fits, write. Two concurrent 15 000 EUR deposits
 * into the same Livret A both read zero, both pass the 22 950 EUR check and, without a lock, both
 * commit &mdash; 30 000 EUR in a capped envelope. With {@code @Version} the second commit's {@code
 * UPDATE ... WHERE version = ?} matches no row and fails instead. Optimistic rather than {@code
 * SELECT ... FOR UPDATE}: conflicting writes to one person's accounts are rare, and an occasional
 * retry is cheaper than locking a row on every read.
 *
 * <p><b>Why {@link #save} demands a transaction.</b> The version compared at commit must be the one
 * read <em>before</em> the aggregate was mutated, so the load and the save have to share a
 * persistence context. Called outside a transaction, save would reload the current version, compare
 * against that, and the check would verify nothing. It refuses rather than silently opening its
 * own.
 */
@Repository
public class JpaAccountRepository implements AccountRepository {

    private final SpringDataAccountRepository jpa;

    JpaAccountRepository(SpringDataAccountRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Account> findAll() {
        return jpa.findAllWithPositions().stream().map(AccountMapper::toDomain).toList();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpa.findWithPositionsById(id.value()).map(AccountMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Account save(Account account) {
        // When the use case loaded this account, the entity is still in the persistence context:
        // no query, and its version is the one read before the mutation.
        AccountEntity entity = jpa.findById(account.id().value()).orElseGet(AccountEntity::new);
        AccountMapper.copyInto(account, entity);
        jpa.save(entity);
        return account;
    }
}
