package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.infrastructure.config.TimeConfiguration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@PersistenceSlice
@DisplayName("JdbcRegulatedRateProvider")
class JdbcRegulatedRateProviderIT {

    @Autowired private JdbcClient jdbc;

    private Map<AccountType, Percentage> ratesOn(String date) {
        Clock clock =
                Clock.fixed(
                        LocalDate.parse(date).atStartOfDay(TimeConfiguration.PARIS).toInstant(),
                        TimeConfiguration.PARIS);
        return new JdbcRegulatedRateProvider(jdbc, clock).currentRates();
    }

    @ParameterizedTest(name = "{0}: {1}%")
    @CsvSource({
        "2025-07-31, 2.40",
        "2025-08-01, 1.70",
        "2026-03-15, 1.50",
        "2026-09-11, 1.70",
    })
    @DisplayName("applies the rate in force on the day, from the migrated history")
    void appliesTheRateInForce(String date, String points) {
        Percentage expected = Percentage.ofPoints(points);

        assertThat(ratesOn(date))
                .containsOnly(
                        entry(AccountType.LIVRET_A, expected), entry(AccountType.LDDS, expected));
    }

    @Test
    @DisplayName("a rate merged ahead of its effective date does not apply early")
    void ignoresAFutureRate() {
        jdbc.sql(
                        """
                        INSERT INTO regulated_rate (account_type, effective_from, rate)
                        VALUES ('LIVRET_A', DATE '2027-02-01', 2.0000)
                        """)
                .update();

        assertThat(ratesOn("2027-01-31"))
                .containsEntry(AccountType.LIVRET_A, Percentage.ofPoints("1.70"));
        assertThat(ratesOn("2027-02-01"))
                .containsEntry(AccountType.LIVRET_A, Percentage.ofPoints("2.00"));
    }
}
