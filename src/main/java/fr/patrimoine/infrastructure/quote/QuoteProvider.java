package fr.patrimoine.infrastructure.quote;

import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Quote;
import java.util.Map;
import java.util.Set;

/**
 * One upstream source of prices.
 *
 * <p>The contract separates two outcomes that are easy to conflate. An instrument the source does
 * not know is simply absent from the result: that is an answer, and it must not count against the
 * source's health. A source that cannot answer at all throws {@link QuoteProviderException}: that
 * is what the circuit breaker counts, and what sends the caller to the remembered prices.
 */
interface QuoteProvider {

    /** Also the name of this provider's circuit breaker and retry instances. */
    String name();

    /** The kinds of instrument this source can price. */
    Set<InstrumentKind> kinds();

    /**
     * @throws QuoteProviderException when the source itself is failing
     */
    Map<InstrumentId, Quote> fetch(Set<InstrumentId> instruments);
}
