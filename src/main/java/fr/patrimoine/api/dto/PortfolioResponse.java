package fr.patrimoine.api.dto;

import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.valuation.AccountValuation;
import fr.patrimoine.domain.valuation.PortfolioValuation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the portfolio is worth right now, with the breakdowns the dashboard shows: by account, by
 * envelope type, and by family of envelopes (savings, investments, property).
 *
 * @param stale true when any figure behind the total came from a cached price
 * @param warnings what could not be valued cleanly, in plain words, so a screen can show the caveat
 *     rather than present a suspiciously round number as fact
 */
@Schema(description = "Total wealth with its per-account, per-envelope and per-family breakdowns")
public record PortfolioResponse(
        LocalDate asOf,
        MoneyResponse total,
        List<AccountSummary> accounts,
        List<TypeSlice> byType,
        List<CategorySlice> byCategory,
        boolean stale,
        List<String> warnings) {

    public PortfolioResponse {
        accounts = List.copyOf(accounts);
        byType = List.copyOf(byType);
        byCategory = List.copyOf(byCategory);
        warnings = List.copyOf(warnings);
    }

    public static PortfolioResponse from(PortfolioValuation valuation) {
        return new PortfolioResponse(
                valuation.asOf(),
                MoneyResponse.of(valuation.total()),
                valuation.byAccount().stream().map(AccountSummary::from).toList(),
                valuation.byType().entrySet().stream()
                        .map(
                                slice ->
                                        new TypeSlice(
                                                slice.getKey().name(),
                                                slice.getKey().label(),
                                                slice.getKey().category().name(),
                                                MoneyResponse.of(slice.getValue())))
                        .toList(),
                valuation.byCategory().entrySet().stream()
                        .map(
                                slice ->
                                        new CategorySlice(
                                                slice.getKey().name(),
                                                slice.getKey().label(),
                                                MoneyResponse.of(slice.getValue()),
                                                Percentage.ofRatio(
                                                                slice.getValue()
                                                                        .ratioTo(valuation.total()))
                                                        .value()))
                        .toList(),
                valuation.stale(),
                valuation.warnings());
    }

    /**
     * @param sharePercent this account's share of the total, in percentage points: 12.5 is 12.5%
     */
    public record AccountSummary(
            UUID id,
            String type,
            String typeLabel,
            String category,
            String label,
            MoneyResponse value,
            BigDecimal sharePercent,
            boolean stale,
            List<String> warnings) {

        public AccountSummary {
            warnings = List.copyOf(warnings);
        }

        static AccountSummary from(AccountValuation valuation) {
            return new AccountSummary(
                    valuation.accountId().value(),
                    valuation.type().name(),
                    valuation.type().label(),
                    valuation.type().category().name(),
                    valuation.label(),
                    MoneyResponse.of(valuation.value()),
                    valuation.share().value(),
                    valuation.stale(),
                    valuation.warnings());
        }
    }

    /**
     * One envelope type and its total.
     *
     * <p>A list rather than an object keyed by type: the order is the deliberate one the domain
     * declares, and no client should have to rely on JSON object key order to keep it.
     */
    public record TypeSlice(String type, String label, String category, MoneyResponse value) {}

    /**
     * One family of envelopes, its total and its share of the whole. Accounts and envelope types
     * name their family in {@code category}, so a client groups them under these, in this order.
     *
     * @param sharePercent in percentage points: 12.5 is 12.5%
     */
    public record CategorySlice(
            String category, String label, MoneyResponse value, BigDecimal sharePercent) {}
}
