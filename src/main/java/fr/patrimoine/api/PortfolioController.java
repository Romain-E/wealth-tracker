package fr.patrimoine.api;

import fr.patrimoine.api.dto.PortfolioResponse;
import fr.patrimoine.application.port.in.GetPortfolioOverviewUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard endpoint.
 *
 * <p>It depends on the use-case interface, never on the service behind it, which is what lets it be
 * tested against a two-line stub rather than a Spring context with a database. An ArchUnit rule
 * enforces that, so the shortcut cannot be taken later under time pressure.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Portfolio", description = "Consolidated wealth across every envelope")
class PortfolioController {

    private final GetPortfolioOverviewUseCase overview;

    PortfolioController(GetPortfolioOverviewUseCase overview) {
        this.overview = overview;
    }

    @GetMapping
    @Operation(
            summary = "What the portfolio is worth right now",
            description =
                    "Values every account at today's prices. Accounts that could not be valued"
                            + " cleanly still count towards the total and explain themselves in"
                            + " warnings, rather than being silently dropped.")
    PortfolioResponse portfolio() {
        return PortfolioResponse.from(overview.overview());
    }
}
