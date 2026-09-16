package fr.patrimoine.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.patrimoine.application.port.in.GetPerformanceUseCase;
import fr.patrimoine.application.port.in.PerformanceView;
import fr.patrimoine.domain.model.AccountId;
import fr.patrimoine.domain.model.Money;
import fr.patrimoine.domain.model.Percentage;
import fr.patrimoine.domain.performance.PerformancePoint;
import fr.patrimoine.domain.performance.PerformanceReport;
import fr.patrimoine.infrastructure.config.TimeConfiguration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PerformanceController.class)
@Import(PerformanceControllerTest.FixedClock.class)
@DisplayName("/api/v1/performance")
class PerformanceControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    /** A pinned day, so the default window is an exact expectation rather than "about a year". */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(
                    TODAY.atStartOfDay(TimeConfiguration.PARIS).toInstant(),
                    TimeConfiguration.PARIS);
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private GetPerformanceUseCase performance;

    private static PerformanceView view() {
        return new PerformanceView(
                new PerformanceReport(
                        Money.euros("45000.00"),
                        Money.euros("65050.30"),
                        Money.euros("20050.30"),
                        Percentage.ofPoints("44.5562"),
                        Optional.of(Percentage.ofPoints("7.1234"))),
                List.of(new PerformancePoint(LocalDate.of(2026, 8, 31), Money.euros("64000.00"))));
    }

    @Test
    @DisplayName("defaults the window to the last year, counted from the injected clock")
    void defaultsToTheLastYear() throws Exception {
        when(performance.portfolioPerformance(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(view());

        mvc.perform(get("/api/v1/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.netGainPercent").value(44.5562))
                .andExpect(jsonPath("$.annualisedReturnPercent").value(7.1234))
                .andExpect(jsonPath("$.series[0].date").value("2026-08-31"))
                .andExpect(jsonPath("$.series[0].value").value(64000.00));

        verify(performance).portfolioPerformance(LocalDate.of(2025, 9, 13), TODAY);
    }

    @Test
    @DisplayName("an explicit window is passed through untouched")
    void honoursAnExplicitWindow() throws Exception {
        when(performance.accountPerformance(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(view());
        AccountId account = AccountId.newId();

        mvc.perform(
                        get("/api/v1/accounts/{id}/performance", account.value())
                                .param("from", "2024-01-01")
                                .param("to", "2024-12-31"))
                .andExpect(status().isOk());

        verify(performance)
                .accountPerformance(account, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    }

    @Test
    @DisplayName("no annualised return is reported as null, never as zero")
    void reportsAnAbsentReturnAsNull() throws Exception {
        when(performance.portfolioPerformance(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(
                        new PerformanceView(
                                new PerformanceReport(
                                        Money.ZERO_EUR,
                                        Money.ZERO_EUR,
                                        Money.ZERO_EUR,
                                        Percentage.ZERO,
                                        Optional.empty()),
                                List.of()));

        mvc.perform(get("/api/v1/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualisedReturnPercent").doesNotExist());
    }

    @Test
    @DisplayName("a window that ends before it starts is the caller's mistake")
    void rejectsABackwardsWindow() throws Exception {
        mvc.perform(
                        get("/api/v1/performance")
                                .param("from", "2026-01-01")
                                .param("to", "2025-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));
    }
}
