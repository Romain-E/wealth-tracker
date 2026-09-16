package fr.patrimoine.infrastructure.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real Postgres for integration tests, wired in through {@code @ServiceConnection}.
 *
 * <p>Not H2. The adapters use {@code DISTINCT ON}, {@code ON CONFLICT} and a PL/pgSQL seed, none of
 * which an in-memory database runs; and a test suite that passes on a different database than the
 * one deployed proves less than it appears to.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
