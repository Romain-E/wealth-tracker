package fr.patrimoine.api.dto;

import fr.patrimoine.domain.model.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * An amount on the wire: the number and the currency it is counted in, never one without the other.
 *
 * <p>Serialised as a JSON number rather than a string. A client is never asked to do arithmetic on
 * money here &mdash; every total, gain and share in this API is computed server-side in {@code
 * BigDecimal} &mdash; so the usual argument for strings does not apply, and a number is what a
 * chart library expects.
 */
@Schema(description = "A monetary amount with its currency")
public record MoneyResponse(
        @Schema(example = "17000.00") BigDecimal amount, @Schema(example = "EUR") String currency) {

    public static MoneyResponse of(Money money) {
        return new MoneyResponse(money.amount(), money.currency().getCurrencyCode());
    }
}
