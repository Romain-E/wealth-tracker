package fr.patrimoine.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the {@link Clock} every use case reads "today" from.
 *
 * <p>Injecting a clock rather than calling {@code LocalDate.now()} is what makes the read services
 * testable: a test pins the date and asserts on an exact accrued-interest figure instead of on
 * whatever today happens to be.
 *
 * <p>The zone is explicit and is not a detail. Containers habitually run with {@code TZ=UTC}, and a
 * default-zone clock would then roll over to the next day at 01:00 or 02:00 Paris time. For this
 * domain that is a correctness bug, not cosmetics: the fortnight boundaries of the <i>regle des
 * quinzaines</i> fall on French calendar days, so a valuation dated one day early can count an
 * extra fortnight of interest.
 */
@Configuration
public class TimeConfiguration {

    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Bean
    public Clock clock() {
        return Clock.system(PARIS);
    }
}
