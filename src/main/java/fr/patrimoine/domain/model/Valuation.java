package fr.patrimoine.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The value of one account on one day.
 *
 * <p>Serves two purposes that could look separate but are the same record: it is the authoritative
 * value for envelopes that have no market price (real estate, assurance vie), and it is the
 * historical series that the performance chart is drawn from. Storing computed valuations daily is
 * what makes past performance answerable at all &mdash; you cannot reconstruct what a portfolio was
 * worth last March from today's prices.
 *
 * <p>Dated with {@link LocalDate}, not {@code Instant}: a valuation is a calendar-day fact, and two
 * valuations on the same day are the same valuation regardless of the hour they were computed.
 */
public record Valuation(AccountId accountId, LocalDate on, Money value, ValuationSource source) {

    public Valuation {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
    }
}
