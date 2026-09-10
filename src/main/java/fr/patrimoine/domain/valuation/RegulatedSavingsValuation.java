package fr.patrimoine.domain.valuation;

import fr.patrimoine.domain.model.Account;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.model.Transaction;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

/**
 * Values a Livret A or an LDDS: the current balance, plus interest accrued but not yet capitalised.
 *
 * <p>Simply reporting the balance would be wrong for most of the year. Interest on these products
 * is only credited on 31 December, so a Livret A looked at in November is genuinely worth more than
 * its balance shows &mdash; eleven months of accrued interest are already earned, just not yet
 * paid. The accrual itself follows the fortnight rule in {@link QuinzaineInterestCalculator}.
 *
 * <p>The opening balance is reconstructed by rewinding this year's movements off the current
 * balance, rather than being stored. One less denormalised field to keep consistent, and the
 * movements have to be loaded anyway to know when the money arrived.
 */
public final class RegulatedSavingsValuation implements ValuationStrategy {

    static final RegulatedSavingsValuation INSTANCE = new RegulatedSavingsValuation();

    private RegulatedSavingsValuation() {}

    @Override
    public ValuationResult value(Account account, ValuationContext context) {
        LocalDate asOf = context.asOf();
        LocalDate periodStart = lastCapitalisation(asOf, account.openedOn());

        Optional<Percentage> rate = context.rateFor(account.type());
        if (rate.isEmpty()) {
            return new ValuationResult(
                    account.id(),
                    account.cashBalance(),
                    false,
                    List.of(
                            "No published rate for %s; accrued interest is not included"
                                    .formatted(account.type().label())));
        }

        List<Transaction> movements =
                context.movementsFor(account.id()).stream()
                        .filter(t -> !t.date().isBefore(periodStart) && !t.date().isAfter(asOf))
                        .toList();

        Money opening = account.cashBalance();
        for (Transaction movement : movements) {
            opening = opening.minus(movement.amount());
        }

        List<QuinzaineInterestCalculator.BalanceChange> changes =
                movements.stream()
                        .map(
                                t ->
                                        new QuinzaineInterestCalculator.BalanceChange(
                                                t.date(), t.amount()))
                        .toList();

        Money accrued =
                QuinzaineInterestCalculator.accrue(opening, changes, rate.get(), periodStart, asOf);

        return ValuationResult.clean(account.id(), account.cashBalance().plus(accrued));
    }

    /**
     * Interest is capitalised on 31 December, so the accrual period starts on 1 January &mdash; or
     * on the opening date, for an account opened this year.
     */
    private static LocalDate lastCapitalisation(LocalDate asOf, LocalDate openedOn) {
        LocalDate firstOfYear = LocalDate.of(asOf.getYear(), Month.JANUARY, 1);
        return openedOn.isAfter(firstOfYear) ? openedOn : firstOfYear;
    }
}
