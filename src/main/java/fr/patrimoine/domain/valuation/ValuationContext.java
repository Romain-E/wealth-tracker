package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.model.Quote;
import fr.patrimoine.domain.model.Transaction;
import fr.patrimoine.domain.model.Valuation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the domain needs from the outside world in order to value a portfolio, gathered once
 * and passed in.
 *
 * <p>This is what keeps the domain pure. A valuation strategy never calls a repository, never
 * fetches a quote, and therefore never has a hidden dependency on Spring, on the network, or on the
 * order in which things happen. The application layer assembles this record; the domain reads it.
 * That single rule is what makes every strategy testable with plain JUnit and no mocking framework.
 *
 * @param asOf the calendar day being valued
 * @param quotes latest known price per instrument, possibly stale
 * @param latestValuations most recent snapshot per account, for envelopes with no market price
 * @param movements transactions since the last interest capitalisation, per account
 * @param regulatedRates the current rate per regulated product &mdash; keyed by {@link AccountType}
 *     and not held on the account itself, because the Livret A rate is set by the State for
 *     everyone, not negotiated per saver
 */
public record ValuationContext(
        LocalDate asOf,
        Map<InstrumentId, Quote> quotes,
        Map<AccountId, Valuation> latestValuations,
        Map<AccountId, List<Transaction>> movements,
        Map<AccountType, Percentage> regulatedRates) {

    public ValuationContext {
        Objects.requireNonNull(asOf, "asOf");
        quotes = Map.copyOf(Objects.requireNonNullElse(quotes, Map.of()));
        latestValuations = Map.copyOf(Objects.requireNonNullElse(latestValuations, Map.of()));
        movements = Map.copyOf(Objects.requireNonNullElse(movements, Map.of()));
        regulatedRates = Map.copyOf(Objects.requireNonNullElse(regulatedRates, Map.of()));
    }

    /** Minimal context for market-only valuation. */
    public static ValuationContext of(LocalDate asOf, Map<InstrumentId, Quote> quotes) {
        return new ValuationContext(asOf, quotes, Map.of(), Map.of(), Map.of());
    }

    public Optional<Quote> quoteFor(InstrumentId instrument) {
        return Optional.ofNullable(quotes.get(instrument));
    }

    public Optional<Valuation> latestValuationFor(AccountId accountId) {
        return Optional.ofNullable(latestValuations.get(accountId));
    }

    public List<Transaction> movementsFor(AccountId accountId) {
        return movements.getOrDefault(accountId, List.of());
    }

    public Optional<Percentage> rateFor(AccountType type) {
        return Optional.ofNullable(regulatedRates.get(type));
    }
}
