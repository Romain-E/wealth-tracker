package fr.patrimoine.api.dto;

import fr.patrimoine.application.port.in.AccountDetail;
import fr.patrimoine.application.port.in.PositionDetail;
import fr.patrimoine.domain.model.Price;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One envelope, its holdings marked to market, and what may still legally be paid into it.
 *
 * @param remainingAllowance null when the envelope has no ceiling at all, which is emphatically not
 *     the same as no room left. A PEA that has used its 150 000 EUR and a compte-titres that never
 *     had a limit must not render identically, so the absence stays null instead of becoming zero.
 */
@Schema(description = "One account with its positions and its remaining legal allowance")
public record AccountDetailResponse(
        UUID id,
        String label,
        String type,
        String typeLabel,
        String currency,
        LocalDate openedOn,
        MoneyResponse cashBalance,
        MoneyResponse totalValue,
        @Schema(nullable = true) MoneyResponse remainingAllowance,
        List<PositionResponse> positions,
        boolean stale,
        List<String> warnings) {

    public AccountDetailResponse {
        positions = List.copyOf(positions);
        warnings = List.copyOf(warnings);
    }

    public static AccountDetailResponse from(AccountDetail detail) {
        return new AccountDetailResponse(
                detail.id().value(),
                detail.label(),
                detail.type().name(),
                detail.type().label(),
                detail.currency().getCurrencyCode(),
                detail.openedOn(),
                MoneyResponse.of(detail.cashBalance()),
                MoneyResponse.of(detail.totalValue()),
                detail.remainingAllowance().map(MoneyResponse::of).orElse(null),
                detail.positions().stream().map(PositionResponse::from).toList(),
                detail.stale(),
                detail.warnings());
    }

    /**
     * @param currentPrice null when the instrument could not be priced. {@code marketValue} then
     *     falls back to what was paid for it and {@code unrealisedGain} is zero, rather than the
     *     total loss that a missing price would otherwise look like.
     * @param pricedAt when that price was observed upstream, so a screen can show its age
     */
    public record PositionResponse(
            String instrument,
            String kind,
            BigDecimal quantity,
            BigDecimal averageCost,
            MoneyResponse totalCost,
            @Schema(nullable = true) BigDecimal currentPrice,
            MoneyResponse marketValue,
            MoneyResponse unrealisedGain,
            @Schema(nullable = true) Instant pricedAt,
            boolean stale) {

        static PositionResponse from(PositionDetail position) {
            return new PositionResponse(
                    position.instrument().value(),
                    position.kind().name(),
                    position.quantity().value(),
                    position.averageCost().value(),
                    MoneyResponse.of(position.totalCost()),
                    position.currentPrice().map(Price::value).orElse(null),
                    MoneyResponse.of(position.marketValue()),
                    MoneyResponse.of(position.unrealisedGain()),
                    position.pricedAt().orElse(null),
                    position.stale());
        }
    }
}
