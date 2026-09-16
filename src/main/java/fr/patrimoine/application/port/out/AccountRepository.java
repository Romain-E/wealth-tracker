package fr.patrimoine.application.port.out;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import java.util.List;
import java.util.Optional;

/**
 * Driven port for account storage.
 *
 * <p>Declared here, in the application layer, and implemented out in infrastructure. That inversion
 * is the whole point: the use cases depend on this interface, the JPA adapter depends on it too,
 * and neither the use cases nor the domain ever learn that Postgres exists.
 *
 * <p>Note what is absent. There is no {@code findByType}, no {@code findByLabelContaining}, no
 * pagination. A port declares exactly the queries the use cases actually need, which keeps it a
 * contract rather than a leaked ORM surface. Spring Data would happily generate fifty methods; that
 * would make the interface a description of the database instead of a description of the domain's
 * needs.
 */
public interface AccountRepository {

    /** Every account, in a stable order suitable for a portfolio breakdown. */
    List<Account> findAll();

    Optional<Account> findById(AccountId id);

    /** Persists the aggregate as a whole, positions included. */
    Account save(Account account);
}
