package com.urlshortener.dto;

public record UrlClickStatsResponse(Long urlId, long totalClicks) {
}
