package fr.patrimoine.domain.performance;

import fr.patrimoine.domain.model.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A dated movement of money across the boundary of the portfolio, <b>signed from the investor's
 * point of view</b>: negative when you put money in, positive when you take it out or when the
 * portfolio is finally worth something.
 *
 * <p>That sign convention is the opposite of the one on {@link
 * fr.patrimoine.domain.model.Transaction}, which is signed from the account's point of view, and
 * getting the two confused is the classic way to compute a return with the wrong sign. The
 * conversion happens in exactly one place, in {@link PerformanceCalculator}.
 */
public record CashFlow(LocalDate on, Money amount) {

    public CashFlow {
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(amount, "amount");
    }

    /** Money leaving your pocket and entering the portfolio. */
    public static CashFlow invested(LocalDate on, Money amount) {
        return new CashFlow(on, amount.abs().negated());
    }

    /** Money coming back out, including the terminal value of what is still held. */
    public static CashFlow returned(LocalDate on, Money amount) {
        return new CashFlow(on, amount.abs());
    }

    public boolean isOutflow() {
        return amount.isNegative();
    }
}
