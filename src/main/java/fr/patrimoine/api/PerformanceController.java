package fr.patrimoine.api;

import fr.patrimoine.api.dto.PerformanceResponse;
import fr.patrimoine.application.port.in.GetPerformanceUseCase;
import fr.patrimoine.domain.model.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Performance, for the whole portfolio or for one envelope.
 *
 * <p>The chart window defaults to the last year. That default is computed from the injected {@link
 * Clock} rather than {@code LocalDate.now()}, so a test can pin the day and assert the exact window
 * the controller asked the use case for.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Performance", description = "Gains, annualised returns and the curve behind them")
class PerformanceController {

    private final GetPerformanceUseCase performance;
    private final Clock clock;

    PerformanceController(GetPerformanceUseCase performance, Clock clock) {
        this.performance = performance;
        this.clock = clock;
    }

    @GetMapping("/performance")
    @Operation(
            summary = "Consolidated performance across every account",
            description =
                    "Reports both the cumulative gain and the money-weighted annualised return"
                            + " (XIRR), which answer different questions and often disagree.")
    PerformanceResponse portfolio(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    @Parameter(description = "Window start, inclusive. Defaults to a year ago.")
                    LocalDate from,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    @Parameter(description = "Window end, inclusive. Defaults to today.")
                    LocalDate to) {
        Window window = window(from, to);
        return PerformanceResponse.from(performance.portfolioPerformance(window.from, window.to));
    }

    @GetMapping("/accounts/{accountId}/performance")
    @Operation(summary = "Performance of a single envelope")
    PerformanceResponse account(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        Window window = window(from, to);
        return PerformanceResponse.from(
                performance.accountPerformance(new AccountId(accountId), window.from, window.to));
    }

    private Window window(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusYears(1);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "The start of the window (%s) is after its end (%s)".formatted(start, end));
        }
        return new Window(start, end);
    }

    private record Window(LocalDate from, LocalDate to) {}
}
