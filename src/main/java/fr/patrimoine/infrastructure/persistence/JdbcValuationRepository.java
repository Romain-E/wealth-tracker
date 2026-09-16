package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.application.port.out.ValuationRepository;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Valuation;
import fr.patrimoine.domain.model.ValuationSource;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC adapter for {@link ValuationRepository}.
 *
 * <p>Plain SQL rather than JPA, deliberately. A valuation is a point in a time series, not an
 * aggregate: there is no lifecycle to track, nothing to dirty-check, no read-modify-write to lock.
 * What it does need are two things JPQL cannot say &mdash; "the latest row per account", which
 * Postgres answers with {@code DISTINCT ON}, and an atomic upsert with {@code ON CONFLICT}. An
 * entity mapped only to be bypassed by native queries would be JPA in name only.
 */
@Repository
public class JdbcValuationRepository implements ValuationRepository {

    private static final RowMapper<Valuation> VALUATION =
            (row, rowNumber) ->
                    new Valuation(
                            new AccountId(row.getObject("account_id", UUID.class)),
                            row.getObject("valuation_date", LocalDate.class),
                            Money.of(
                                    row.getBigDecimal("amount"),
                                    Currency.getInstance(row.getString("currency"))),
                            ValuationSource.valueOf(row.getString("source")));

    private final JdbcClient jdbc;

    public JdbcValuationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code DISTINCT ON} keeps the first row of each account in {@code ORDER BY} order: the
     * newest.
     */
    @Override
    public Map<AccountId, Valuation> latestByAccount(Collection<AccountId> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return byAccount(
                jdbc.sql(
                                """
                                SELECT DISTINCT ON (account_id)
                                       account_id, valuation_date, amount, currency, source
                                FROM valuation
                                WHERE account_id IN (:accountIds)
                                ORDER BY account_id, valuation_date DESC
                                """)
                        .param("accountIds", accountIds.stream().map(AccountId::value).toList())
                        .query(VALUATION)
                        .list());
    }

    @Override
    public List<Valuation> findByAccountBetween(AccountId accountId, LocalDate from, LocalDate to) {
        return jdbc.sql(
                        """
                        SELECT account_id, valuation_date, amount, currency, source
                        FROM valuation
                        WHERE account_id = :accountId AND valuation_date BETWEEN :from AND :to
                        ORDER BY valuation_date
                        """)
                .param("accountId", accountId.value())
                .param("from", from)
                .param("to", to)
                .query(VALUATION)
                .list();
    }

    @Override
    public List<Valuation> findAllBetween(LocalDate from, LocalDate to) {
        return jdbc.sql(
                        """
                        SELECT account_id, valuation_date, amount, currency, source
                        FROM valuation
                        WHERE valuation_date BETWEEN :from AND :to
                        ORDER BY valuation_date, account_id
                        """)
                .param("from", from)
                .param("to", to)
                .query(VALUATION)
                .list();
    }

    @Override
    public Map<AccountId, Valuation> latestByAccountBefore(LocalDate date) {
        return byAccount(
                jdbc.sql(
                                """
                                SELECT DISTINCT ON (account_id)
                                       account_id, valuation_date, amount, currency, source
                                FROM valuation
                                WHERE valuation_date < :date
                                ORDER BY account_id, valuation_date DESC
                                """)
                        .param("date", date)
                        .query(VALUATION)
                        .list());
    }

    /**
     * An upsert, because a day has one valuation and recomputing it replaces it. {@code ON
     * CONFLICT} makes that atomic: a select-then-insert lets two writers for the same day both see
     * no row, and the second one fails on the primary key.
     */
    @Override
    public Valuation save(Valuation valuation) {
        jdbc.sql(
                        """
                        INSERT INTO valuation (account_id, valuation_date, amount, currency, source)
                        VALUES (:accountId, :valuationDate, :amount, :currency, :source)
                        ON CONFLICT (account_id, valuation_date) DO UPDATE
                        SET amount = EXCLUDED.amount,
                            currency = EXCLUDED.currency,
                            source = EXCLUDED.source
                        """)
                .param("accountId", valuation.accountId().value())
                .param("valuationDate", valuation.on())
                .param("amount", valuation.value().amount())
                .param("currency", valuation.value().currency().getCurrencyCode())
                .param("source", valuation.source().name())
                .update();
        return valuation;
    }

    private static Map<AccountId, Valuation> byAccount(List<Valuation> latest) {
        return latest.stream().collect(Collectors.toMap(Valuation::accountId, Function.identity()));
    }
}
