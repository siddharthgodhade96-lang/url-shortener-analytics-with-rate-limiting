package com.urlshortener.dto;

public record RateLimitUsageResponse(int capacity, double remainingTokens, double refillRate) {
}
