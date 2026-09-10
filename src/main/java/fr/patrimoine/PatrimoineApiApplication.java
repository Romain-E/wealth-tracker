package fr.patrimoine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Composition root.
 *
 * <p>Deliberately the only class in the root package: component scanning starts here, so anything
 * outside {@code fr.patrimoine} is invisible to Spring by construction.
 */
@SpringBootApplication
@EnableScheduling
public class PatrimoineApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatrimoineApiApplication.class, args);
    }
}
