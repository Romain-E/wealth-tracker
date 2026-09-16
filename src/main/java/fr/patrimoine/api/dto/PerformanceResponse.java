package fr.patrimoine.api.dto;

import fr.patrimoine.application.port.in.PerformanceView;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.performance.PerformanceReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * How an account or the whole portfolio has done, plus the curve behind the headline.
 *
 * @param netGainPercent cumulative gain over the money actually put in, in percentage points
 * @param annualisedReturnPercent the money-weighted (XIRR) return per year, null when there is
 *     nothing to annualise: no deposits, or a portfolio funded yesterday. Null rather than 0.0,
 *     because "we cannot say" and "it returned nothing" are different statements.
 * @param series the portfolio's value at each stored snapshot date, oldest first
 */
@Schema(description = "Headline performance figures and the value curve behind them")
public record PerformanceResponse(
        String currency,
        MoneyResponse netInvested,
        MoneyResponse currentValue,
        MoneyResponse netGain,
        BigDecimal netGainPercent,
        @Schema(nullable = true) BigDecimal annualisedReturnPercent,
        List<Point> series) {

    public PerformanceResponse {
        series = List.copyOf(series);
    }

    public static PerformanceResponse from(PerformanceView view) {
        PerformanceReport report = view.report();
        return new PerformanceResponse(
                report.currentValue().currency().getCurrencyCode(),
                MoneyResponse.of(report.netInvested()),
                MoneyResponse.of(report.currentValue()),
                MoneyResponse.of(report.netGain()),
                report.netGainRatio().value(),
                report.annualisedReturn().map(Percentage::value).orElse(null),
                view.series().stream()
                        .map(point -> new Point(point.on(), point.value().amount()))
                        .toList());
    }

    /**
     * One point of the curve: a bare amount, with the currency stated once on the response.
     * Repeating "EUR" on several hundred points would be noise no chart ever reads.
     */
    public record Point(LocalDate date, BigDecimal value) {}
}
