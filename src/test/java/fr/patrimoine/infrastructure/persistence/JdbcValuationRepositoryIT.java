package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationSource;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PersistenceSlice
@DisplayName("JdbcValuationRepository")
class JdbcValuationRepositoryIT {

    @Autowired private JdbcValuationRepository valuations;
    @Autowired private JpaAccountRepository accounts;
    @Autowired private TestEntityManager entityManager;

    private AccountId pea;
    private AccountId flat;

    @BeforeEach
    void createAccounts() {
        pea = persisted(AccountType.PEA);
        flat = persisted(AccountType.REAL_ESTATE);
        // Valuation rows reference the accounts by foreign key, and JDBC does not see unflushed
        // JPA.
        entityManager.flush();
    }

    private AccountId persisted(AccountType type) {
        Account account =
                Account.open(
                        AccountId.newId(), type.label(), type, Money.EUR, LocalDate.of(2020, 1, 1));
        accounts.save(account);
        return account.id();
    }

    private static Valuation valuation(
            AccountId account, String date, String amount, ValuationSource source) {
        return new Valuation(account, LocalDate.parse(date), Money.euros(amount), source);
    }

    @Test
    @DisplayName("a second valuation on the same day replaces the first instead of duplicating it")
    void keepsOneValuationPerDay() {
        valuations.save(valuation(pea, "2026-06-30", "1000.00", ValuationSource.COMPUTED));
        Valuation corrected =
                valuations.save(valuation(pea, "2026-06-30", "1100.00", ValuationSource.MANUAL));

        assertThat(
                        valuations.findByAccountBetween(
                                pea, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-31")))
                .containsExactly(corrected);
    }

    @Test
    @DisplayName("finds each account's latest valuation, and the latest one strictly before a date")
    void findsLatestValuations() {
        Valuation peaDecember =
                valuations.save(valuation(pea, "2025-12-31", "9000.00", ValuationSource.COMPUTED));
        Valuation peaJune =
                valuations.save(valuation(pea, "2026-06-30", "9500.00", ValuationSource.COMPUTED));
        Valuation appraisal =
                valuations.save(valuation(flat, "2024-06-30", "300000.00", ValuationSource.MANUAL));

        assertThat(valuations.latestByAccount(List.of(pea, flat)))
                .containsOnly(entry(pea, peaJune), entry(flat, appraisal));
        // Strictly before: a chart window opening on 30 June already holds that day's own row.
        assertThat(valuations.latestByAccountBefore(LocalDate.parse("2026-06-30")))
                .containsOnly(entry(pea, peaDecember), entry(flat, appraisal));
    }

    @Test
    @DisplayName("a chart window includes both of its ends")
    void includesBothEndsOfTheWindow() {
        valuations.save(valuation(pea, "2026-01-01", "1.00", ValuationSource.COMPUTED));
        valuations.save(valuation(flat, "2026-03-31", "2.00", ValuationSource.MANUAL));
        valuations.save(valuation(pea, "2026-04-01", "3.00", ValuationSource.COMPUTED));

        assertThat(
                        valuations.findAllBetween(
                                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31")))
                .extracting(Valuation::on)
                .containsExactly(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31"));
    }
}
