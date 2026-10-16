package com.urlshortener.dto;

import java.util.Map;

public record AnalyticsSummaryResponse(
        long totalClicks,
        Map<String, Long> clicksByReferrer,
        Map<String, Long> clicksByDeviceCategory
) {
}
