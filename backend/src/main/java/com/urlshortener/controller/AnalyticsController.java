package com.urlshortener.controller;

import com.urlshortener.dto.AnalyticsSummaryResponse;
import com.urlshortener.dto.DailyClickResponse;
import com.urlshortener.filter.ApiKeyFilter;
import com.urlshortener.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/analytics/daily")
    public List<DailyClickResponse> getDailyClicks(
            @RequestParam Long urlId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request
    ) {
        return analyticsService.getDailyClicks(urlId, ownerId(request), from, to);
    }

    @GetMapping("/api/analytics/summary")
    public AnalyticsSummaryResponse getSummary(HttpServletRequest request) {
        return analyticsService.getSummary(ownerId(request));
    }

    private Long ownerId(HttpServletRequest request) {
        return (Long) request.getAttribute(ApiKeyFilter.OWNER_ID_ATTRIBUTE);
    }
}
