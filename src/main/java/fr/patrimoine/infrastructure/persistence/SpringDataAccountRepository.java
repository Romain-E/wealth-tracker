package fr.patrimoine.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data access to {@link AccountEntity}.
 *
 * <p>Extends the bare {@link Repository} marker rather than {@code JpaRepository}, for the same
 * reason the port is small: only the methods declared here exist, instead of the thirty that {@code
 * JpaRepository} would expose to whoever finds this interface.
 */
interface SpringDataAccountRepository extends Repository<AccountEntity, UUID> {

    /**
     * Accounts with their positions in a single query. Without the fetch join, reading the
     * positions of N accounts costs N more queries: an N+1 that no test with one account notices.
     */
    @Query("select a from AccountEntity a left join fetch a.positions order by a.openedOn, a.id")
    List<AccountEntity> findAllWithPositions();

    @Query("select a from AccountEntity a left join fetch a.positions where a.id = :id")
    Optional<AccountEntity> findWithPositionsById(UUID id);

    /** Plain lookup: returns the managed instance without a query when it is already loaded. */
    Optional<AccountEntity> findById(UUID id);

    AccountEntity save(AccountEntity entity);
}
