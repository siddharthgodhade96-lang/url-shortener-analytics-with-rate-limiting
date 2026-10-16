package com.urlshortener.controller;

import com.urlshortener.dto.AnalyticsSummaryResponse;
import com.urlshortener.dto.DailyClickResponse;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.filter.RateLimitFilter;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.service.ApiKeyHasher;
import com.urlshortener.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private ApiKeyHasher apiKeyHasher;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @Test
    void returnsDailyClicksForTheAuthenticatedOwner() throws Exception {
        LocalDate date = LocalDate.parse("2026-10-01");
        when(analyticsService.getDailyClicks(23L, 7L, date, date))
                .thenReturn(List.of(new DailyClickResponse(date, 5L)));

        mockMvc.perform(get("/api/analytics/daily")
                        .param("urlId", "23")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-01")
                        .requestAttr("com.urlshortener.filter.ApiKeyFilter.ownerId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$[0].clicks").value(5));

        verify(analyticsService).getDailyClicks(23L, 7L, date, date);
    }

    @Test
    void returnsOwnerSummaryWithReferrerAndDeviceBreakdowns() throws Exception {
        when(analyticsService.getSummary(7L)).thenReturn(new AnalyticsSummaryResponse(
                8L,
                Map.of("(direct)", 3L),
                Map.of("mobile", 5L)
        ));

        mockMvc.perform(get("/api/analytics/summary")
                        .requestAttr("com.urlshortener.filter.ApiKeyFilter.ownerId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(8))
                .andExpect(jsonPath("$.clicksByReferrer['(direct)']").value(3))
                .andExpect(jsonPath("$.clicksByDeviceCategory.mobile").value(5));

        verify(analyticsService).getSummary(7L);
    }
}
