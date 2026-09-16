package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.valuation.PortfolioValuation;

/**
 * Driving port: what is this portfolio worth right now, and how is it split.
 *
 * <p>The API layer depends on this interface rather than on the implementing service, which is what
 * lets a controller be unit-tested against a two-line stub instead of a Spring context. It returns
 * the domain's own {@link PortfolioValuation}: inventing a parallel application-level DTO here
 * would be a copy of a record that already says exactly the right thing. The API layer does own its
 * wire format, and maps this into it &mdash; that boundary is where a DTO earns its keep, not this
 * one.
 */
public interface GetPortfolioOverviewUseCase {

    PortfolioValuation overview();
}
