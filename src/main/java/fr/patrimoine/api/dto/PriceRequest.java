package fr.patrimoine.api.dto;

import fr.patrimoine.domain.model.Price;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Currency;

/**
 * A unit price in a request body, held to eight decimals rather than two.
 *
 * <p>Separate from {@link AmountRequest} for the reason the domain separates {@code Price} from
 * {@code Money}: one SHIB trades at about 0.00001 EUR, and validating a price at cent precision
 * would reject every crypto order at the door.
 */
@Schema(description = "A unit price, up to eight decimals")
public record PriceRequest(
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 8) @Schema(example = "540.00")
                BigDecimal amount,
        @NotNull
                @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three-letter currency code")
                @Schema(example = "EUR")
                String currency) {

    public Price toPrice() {
        return Price.of(amount, Currency.getInstance(currency));
    }

    public Currency asCurrency() {
        return Currency.getInstance(currency);
    }
}
