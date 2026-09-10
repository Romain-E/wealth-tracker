package fr.patrimoine.domain.model;

import fr.patrimoine.domain.error.DepositCeilingExceededException;
import fr.patrimoine.domain.error.IneligibleInstrumentException;
import fr.patrimoine.domain.error.InsufficientCashException;
import fr.patrimoine.domain.error.InsufficientPositionException;
import fr.patrimoine.domain.error.PositionsNotSupportedException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root: one wealth envelope, its cash, and its positions.
 *
 * <p>Every regulatory rule lives here rather than in a service. That is the whole point of an
 * aggregate: there is no code path that can put an {@code Account} into an illegal state, because
 * the only way to change one is through a method that checks first. A validation layer sitting
 * outside the object can always be bypassed by the next caller who forgets to call it.
 *
 * <p>Mutable, unlike the value objects around it. An aggregate has a lifecycle and an identity; the
 * things it holds do not. Mutation is confined to three private fields and every one of them is
 * guarded.
 */
public final class Account {

    private final AccountId id;
    private final String label;
    private final AccountType type;
    private final Currency currency;
    private final LocalDate openedOn;

    private Money cashBalance;

    /**
     * Payments minus withdrawals, floored at zero. Feeds a {@link CeilingBasis#NET_PAYMENTS} cap.
     */
    private Money netPayments;

    /** Cumulative payments, never decreased. Feeds a {@link CeilingBasis#GROSS_PAYMENTS} cap. */
    private Money grossPayments;

    private final Map<InstrumentId, Position> positions = new LinkedHashMap<>();

    private Account(
            AccountId id,
            String label,
            AccountType type,
            Currency currency,
            LocalDate openedOn,
            Money cashBalance,
            Money netPayments,
            Money grossPayments) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = requireLabel(label);
        this.type = Objects.requireNonNull(type, "type");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.openedOn = Objects.requireNonNull(openedOn, "openedOn");
        this.cashBalance = Objects.requireNonNull(cashBalance, "cashBalance");
        this.netPayments = Objects.requireNonNull(netPayments, "netPayments");
        this.grossPayments = Objects.requireNonNull(grossPayments, "grossPayments");
    }

    /** Opens a brand-new, empty envelope. */
    public static Account open(
            AccountId id, String label, AccountType type, Currency currency, LocalDate openedOn) {
        Money zero = Money.zero(currency);
        return new Account(id, label, type, currency, openedOn, zero, zero, zero);
    }

    /**
     * Rebuilds an account from stored state, <b>without</b> re-running the ceiling checks.
     *
     * <p>This is not a loophole, it is a correctness requirement. A Livret A that has been open for
     * fifteen years legitimately sits above its 22 950 EUR ceiling because capitalised interest is
     * not a payment. Re-validating on load would make perfectly legal accounts unreadable. The
     * ceiling constrains the {@link #deposit} transition, not the state.
     */
    public static Account rehydrate(
            AccountId id,
            String label,
            AccountType type,
            Currency currency,
            LocalDate openedOn,
            Money cashBalance,
            Money netPayments,
            Money grossPayments,
            Collection<Position> positions) {
        Account account =
                new Account(
                        id,
                        label,
                        type,
                        currency,
                        openedOn,
                        cashBalance,
                        netPayments,
                        grossPayments);
        positions.forEach(p -> account.positions.put(p.instrument(), p));
        return account;
    }

    // ------------------------------------------------------------------ cash

    /**
     * Pays money into the envelope.
     *
     * @throws DepositCeilingExceededException if the payment would breach the regulatory cap
     */
    public void deposit(Money amount) {
        requirePositive(amount, "A deposit");
        Money newNet = netPayments.plus(amount);
        Money newGross = grossPayments.plus(amount);
        enforceCeiling(newNet, newGross);
        cashBalance = cashBalance.plus(amount);
        netPayments = newNet;
        grossPayments = newGross;
    }

    /**
     * Takes money out.
     *
     * <p>Net payments are floored at zero: once you have withdrawn more than you ever paid in, you
     * have your whole allowance back, and letting the counter go negative would hand you extra room
     * you are not entitled to. Gross payments are untouched, which is exactly the PEA rule.
     */
    public void withdraw(Money amount) {
        requirePositive(amount, "A withdrawal");
        if (cashBalance.isLessThan(amount)) {
            throw new InsufficientCashException(cashBalance.toString(), amount.toString());
        }
        cashBalance = cashBalance.minus(amount);
        Money remaining = netPayments.minus(amount);
        netPayments = remaining.isNegative() ? Money.zero(currency) : remaining;
    }

    /**
     * Credits interest or a dividend without consuming ceiling room. This is the transition that
     * legally carries a Livret A above 22 950 EUR.
     */
    public void credit(Money amount) {
        requirePositive(amount, "A credit");
        cashBalance = cashBalance.plus(amount);
    }

    /** Debits a fee or a tax. */
    public void debit(Money amount) {
        requirePositive(amount, "A debit");
        if (cashBalance.isLessThan(amount)) {
            throw new InsufficientCashException(cashBalance.toString(), amount.toString());
        }
        cashBalance = cashBalance.minus(amount);
    }

    // --------------------------------------------------------------- trading

    /**
     * Buys units, settling in cash from this account.
     *
     * @throws PositionsNotSupportedException on an envelope that holds no securities
     * @throws IneligibleInstrumentException if the envelope may not legally hold this kind of asset
     * @throws InsufficientCashException if the all-in cost exceeds the cash balance
     */
    public void buy(
            InstrumentId instrument,
            InstrumentKind kind,
            Quantity quantity,
            Price unitPrice,
            Money fees) {
        requireTradable(kind);
        requirePositiveQuantity(quantity);
        Money cost = unitPrice.times(quantity).plus(fees);
        if (cashBalance.isLessThan(cost)) {
            throw new InsufficientCashException(cashBalance.toString(), cost.toString());
        }
        cashBalance = cashBalance.minus(cost);
        Position existing = positions.get(instrument);
        positions.put(
                instrument,
                existing == null
                        ? Position.opening(instrument, kind, quantity, unitPrice, fees)
                        : existing.addLot(quantity, unitPrice, fees));
    }

    /**
     * Sells units, crediting the net proceeds to cash.
     *
     * @throws InsufficientPositionException if the account does not hold that many units
     */
    public void sell(InstrumentId instrument, Quantity quantity, Price unitPrice, Money fees) {
        requirePositiveQuantity(quantity);
        Position held = positions.get(instrument);
        if (held == null || quantity.isGreaterThan(held.quantity())) {
            throw new InsufficientPositionException(
                    instrument.toString(),
                    held == null ? "0" : held.quantity().toString(),
                    quantity.toString());
        }
        Money proceeds = unitPrice.times(quantity).minus(fees);
        if (proceeds.isNegative()) {
            throw new IllegalArgumentException("Fees exceed the sale proceeds");
        }
        cashBalance = cashBalance.plus(proceeds);
        held.reduceBy(quantity)
                .ifPresentOrElse(
                        remaining -> positions.put(instrument, remaining),
                        () -> positions.remove(instrument));
    }

    // ------------------------------------------------------------- accessors

    public AccountId id() {
        return id;
    }

    public String label() {
        return label;
    }

    public AccountType type() {
        return type;
    }

    public Currency currency() {
        return currency;
    }

    public LocalDate openedOn() {
        return openedOn;
    }

    public Money cashBalance() {
        return cashBalance;
    }

    public Money netPayments() {
        return netPayments;
    }

    public Money grossPayments() {
        return grossPayments;
    }

    public List<Position> positions() {
        return List.copyOf(positions.values());
    }

    public Optional<Position> position(InstrumentId instrument) {
        return Optional.ofNullable(positions.get(instrument));
    }

    /** Remaining room before the regulatory cap, empty when the envelope is uncapped. */
    public Optional<Money> remainingAllowance() {
        return type.ceiling()
                .map(
                        ceiling -> {
                            Money used =
                                    type.ceilingBasis() == CeilingBasis.GROSS_PAYMENTS
                                            ? grossPayments
                                            : netPayments;
                            Money left = ceiling.minus(used);
                            return left.isNegative() ? Money.zero(currency) : left;
                        });
    }

    // ---------------------------------------------------------------- guards

    private void enforceCeiling(Money newNet, Money newGross) {
        Optional<Money> ceiling = type.ceiling();
        if (ceiling.isEmpty()) {
            return;
        }
        Money limit = ceiling.get();
        Money candidate =
                switch (type.ceilingBasis()) {
                    case NET_PAYMENTS -> newNet;
                    case GROSS_PAYMENTS -> newGross;
                    case NONE -> null;
                };
        if (candidate != null && candidate.isGreaterThan(limit)) {
            throw new DepositCeilingExceededException(
                    type.label(), limit.toString(), candidate.toString());
        }
    }

    private void requireTradable(InstrumentKind kind) {
        if (!type.holdsPositions()) {
            throw new PositionsNotSupportedException(type.label());
        }
        if (!type.accepts(kind)) {
            throw new IneligibleInstrumentException(type.label(), kind.name());
        }
    }

    private void requirePositive(Money amount, String what) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(what + " must be strictly positive, was " + amount);
        }
    }

    private static void requirePositiveQuantity(Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException("A trade must move a strictly positive quantity");
        }
    }

    private static String requireLabel(String label) {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("An account label cannot be blank");
        }
        return label.trim();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Account account && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s[%s, %s]".formatted(label, type, id);
    }
}
