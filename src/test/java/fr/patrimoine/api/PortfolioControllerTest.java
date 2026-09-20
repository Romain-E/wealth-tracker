package fr.patrimoine.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.patrimoine.api.correlation.CorrelationIdFilter;
import fr.patrimoine.application.port.in.GetPortfolioOverviewUseCase;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.AccountType;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.valuation.AccountValuation;
import fr.patrimoine.domain.valuation.PortfolioValuation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PortfolioController.class)
@DisplayName("GET /api/v1/portfolio")
class PortfolioControllerTest {

    private static final AccountId PEA = AccountId.newId();
    private static final AccountId FLAT = AccountId.newId();

    @Autowired private MockMvc mvc;
    @MockitoBean private GetPortfolioOverviewUseCase overview;

    private static AccountValuation line(
            AccountId id, String label, AccountType type, String value, String share) {
        return new AccountValuation(
                id, label, type, Money.euros(value), Percentage.ofPoints(share), false, List.of());
    }

    @Test
    @DisplayName("renders the total with its breakdowns")
    void rendersTheTotalAndItsBreakdowns() throws Exception {
        when(overview.overview())
                .thenReturn(
                        new PortfolioValuation(
                                LocalDate.of(2026, 9, 13),
                                Money.euros("365050.30"),
                                List.of(
                                        line(PEA, "PEA", AccountType.PEA, "65050.30", "17.8195"),
                                        line(
                                                FLAT,
                                                "Appartement Lyon",
                                                AccountType.REAL_ESTATE,
                                                "300000.00",
                                                "82.1805")),
                                Map.of(
                                        AccountType.PEA, Money.euros("65050.30"),
                                        AccountType.REAL_ESTATE, Money.euros("300000.00")),
                                false,
                                List.of()));

        mvc.perform(get("/api/v1/portfolio"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "asOf": "2026-09-13",
                                          "total": {"amount": 365050.30, "currency": "EUR"},
                                          "accounts": [
                                            {
                                              "id": "%s",
                                              "type": "PEA",
                                              "typeLabel": "Plan d'epargne en actions",
                                              "category": "INVESTMENTS",
                                              "label": "PEA",
                                              "value": {"amount": 65050.30, "currency": "EUR"},
                                              "sharePercent": 17.8195,
                                              "stale": false
                                            },
                                            {"id": "%s", "type": "REAL_ESTATE", "label": "Appartement Lyon"}
                                          ],
                                          "stale": false,
                                          "warnings": []
                                        }
                                        """
                                                .formatted(PEA.value(), FLAT.value()),
                                        false))
                // The breakdown keeps the domain's declared order, which a JSON object would not
                // promise: passbooks first, property last.
                .andExpect(jsonPath("$.byType[0].type").value("PEA"))
                .andExpect(jsonPath("$.byType[1].type").value("REAL_ESTATE"))
                .andExpect(jsonPath("$.byType[1].label").value("Immobilier"))
                .andExpect(jsonPath("$.byType[1].category").value("REAL_ESTATE"))
                // One level up, each family with its share, for the dashboard to group under.
                .andExpect(jsonPath("$.byCategory.length()").value(2))
                .andExpect(jsonPath("$.byCategory[0].category").value("INVESTMENTS"))
                .andExpect(jsonPath("$.byCategory[0].label").value("Placements"))
                .andExpect(jsonPath("$.byCategory[0].value.amount").value(65050.30))
                .andExpect(jsonPath("$.byCategory[0].sharePercent").value(17.8195))
                .andExpect(jsonPath("$.byCategory[1].category").value("REAL_ESTATE"));
    }

    @Test
    @DisplayName("passes staleness and warnings through instead of hiding them")
    void reportsCaveats() throws Exception {
        when(overview.overview())
                .thenReturn(
                        new PortfolioValuation(
                                LocalDate.of(2026, 9, 13),
                                Money.euros("1000.00"),
                                List.of(),
                                Map.of(),
                                true,
                                List.of("No price for BITCOIN; valued at cost")));

        mvc.perform(get("/api/v1/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.warnings[0]").value("No price for BITCOIN; valued at cost"));
    }
}
