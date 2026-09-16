package fr.patrimoine.application.port.out;

import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Percentage;
import java.util.Map;

/**
 * Driven port for the posted rate of each regulated savings product.
 *
 * <p>Called a provider rather than a repository on purpose: these are not stored aggregates with a
 * lifecycle, they are reference data set by the State &mdash; the same 1.7% applies to every Livret
 * A in France. Keeping the naming honest about that distinction is cheaper than pretending
 * everything behind a port is a repository.
 *
 * <p>Types with no regulated rate are simply absent from the map.
 */
public interface RegulatedRateProvider {

    Map<AccountType, Percentage> currentRates();
}
