package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.application.port.out.RegulatedRateProvider;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Percentage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads regulated rates from their published history.
 *
 * <p>"Current" means in force today, which is not the same as the newest row: a rate change is
 * merged before its effective date, so the latest row may not apply yet. Hence the date filter and
 * the injected clock.
 */
@Component
public class JdbcRegulatedRateProvider implements RegulatedRateProvider {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcRegulatedRateProvider(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Map<AccountType, Percentage> currentRates() {
        return jdbc
                .sql(
                        """
                        SELECT DISTINCT ON (account_type) account_type, rate
                        FROM regulated_rate
                        WHERE effective_from <= :today
                        ORDER BY account_type, effective_from DESC
                        """)
                .param("today", LocalDate.now(clock))
                .query(
                        (row, rowNumber) ->
                                Map.entry(
                                        AccountType.valueOf(row.getString("account_type")),
                                        new Percentage(row.getBigDecimal("rate"))))
                .list()
                .stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (first, second) -> first,
                                () -> new EnumMap<>(AccountType.class)));
    }
}
