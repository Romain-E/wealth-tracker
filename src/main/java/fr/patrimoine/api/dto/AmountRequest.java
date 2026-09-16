package fr.patrimoine.api.dto;

import fr.patrimoine.domain.model.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Currency;

/**
 * An amount of money in a request body.
 *
 * <p>{@code @Digits(fraction = 2)} is the point of this type: 10.005 EUR is refused rather than
 * quietly rounded to 10.00 or 10.01 on the way in. Refusing to guess what someone meant by half a
 * cent is cheaper than explaining, months later, which way the API rounds.
 */
@Schema(description = "An amount of money, at cent precision")
public record AmountRequest(
        @NotNull @Digits(integer = 17, fraction = 2) @Schema(example = "1000.00") BigDecimal amount,
        @NotNull
                @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three-letter currency code")
                @Schema(example = "EUR")
                String currency) {

    public Money toMoney() {
        return Money.of(amount, Currency.getInstance(currency));
    }
}
