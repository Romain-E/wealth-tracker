package fr.patrimoine.infrastructure.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * The persistence adapters against a real, migrated Postgres, without the web layer.
 *
 * <p>One annotation for every adapter test so they all share one Spring context, and therefore one
 * container: the test context cache only reuses a context whose configuration is identical. Each
 * test still runs in a transaction that is rolled back.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PostgresContainerConfiguration.class,
    JpaAccountRepository.class,
    JpaTransactionRepository.class,
    JdbcValuationRepository.class,
    JdbcQuoteStore.class,
    JdbcInstrumentSymbolStore.class
})
@interface PersistenceSlice {}
