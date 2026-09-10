package fr.patrimoine.domain.model;

/**
 * What a transaction did.
 *
 * <p>{@code countsAsPayment} is what feeds the regulatory ceilings, and {@code isExternalFlow} is
 * what feeds the return calculation: only money crossing the boundary between the user's pocket and
 * the portfolio is a cash flow for XIRR purposes. A dividend reinvested inside the envelope is
 * income, not a new investment, and treating it as one would understate the measured return.
 */
public enum TransactionType {
    DEPOSIT(true, true),
    WITHDRAWAL(false, true),
    BUY(false, false),
    SELL(false, false),
    DIVIDEND(false, false),
    INTEREST(false, false),
    FEE(false, false),
    TAX(false, false);

    private final boolean countsAsPayment;
    private final boolean externalFlow;

    TransactionType(boolean countsAsPayment, boolean externalFlow) {
        this.countsAsPayment = countsAsPayment;
        this.externalFlow = externalFlow;
    }

    /** Whether this movement consumes ceiling room (a payment into the envelope). */
    public boolean countsAsPayment() {
        return countsAsPayment;
    }

    /** Whether this movement crosses the portfolio boundary and is therefore a return cash flow. */
    public boolean isExternalFlow() {
        return externalFlow;
    }
}
