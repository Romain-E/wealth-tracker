package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Price;
import fr.patrimoine.domain.model.Quantity;
import java.time.LocalDate;

/**
 * What the caller is asking to record.
 *
 * <p>Sealed, with the four variants nested inside it. This is not decoration: the handling service
 * switches over it with no {@code default}, so adding a fifth kind of movement is a compile error
 * at the one place that must change, rather than a silent fall-through that records nothing and
 * returns 200 OK.
 *
 * <p>Modelling them as four records instead of one struct with nullable instrument fields also
 * means a deposit is literally incapable of carrying a quantity. There is no validation to forget,
 * because the illegal combination cannot be constructed.
 */
public sealed interface TransactionCommand {

    AccountId accountId();

    LocalDate date();

    /** Money paid into the envelope from outside. Subject to the regulatory ceiling. */
    record Deposit(AccountId accountId, LocalDate date, Money amount)
            implements TransactionCommand {}

    /** Money taken out of the envelope. */
    record Withdrawal(AccountId accountId, LocalDate date, Money amount)
            implements TransactionCommand {}

    /** Buying units, settled from the envelope's own cash. */
    record Purchase(
            AccountId accountId,
            LocalDate date,
            InstrumentId instrument,
            InstrumentKind kind,
            Quantity quantity,
            Price unitPrice,
            Money fees)
            implements TransactionCommand {}

    /** Selling units, with the net proceeds credited to the envelope's cash. */
    record Sale(
            AccountId accountId,
            LocalDate date,
            InstrumentId instrument,
            Quantity quantity,
            Price unitPrice,
            Money fees)
            implements TransactionCommand {}
}
