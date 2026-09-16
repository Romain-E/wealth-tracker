package fr.patrimoine.application.port.in;

import fr.patrimoine.domain.performance.PerformancePoint;
import fr.patrimoine.domain.performance.PerformanceReport;
import java.util.List;
import java.util.Objects;

/**
 * A performance answer: the headline numbers and the curve behind them.
 *
 * <p>Two things a screen always shows together but which come from different places &mdash; the
 * report is computed from the transaction history, the series is read from stored snapshots.
 * Pairing them here means the controller does not have to make two calls and hope they are
 * consistent.
 */
public record PerformanceView(PerformanceReport report, List<PerformancePoint> series) {

    public PerformanceView {
        Objects.requireNonNull(report, "report");
        series = List.copyOf(Objects.requireNonNullElse(series, List.of()));
    }
}
