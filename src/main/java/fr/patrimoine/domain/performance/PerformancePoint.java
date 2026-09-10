package fr.patrimoine.domain.performance;

import fr.patrimoine.domain.model.Money;
import java.time.LocalDate;
import java.util.Objects;

/** One point on the performance chart. */
public record PerformancePoint(LocalDate on, Money value) {

    public PerformancePoint {
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(value, "value");
    }
}
