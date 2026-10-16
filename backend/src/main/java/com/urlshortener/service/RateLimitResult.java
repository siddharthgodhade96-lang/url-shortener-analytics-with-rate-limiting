package com.urlshortener.service;

public record RateLimitResult(boolean allowed, double remainingTokens, long retryAfterSeconds) {
}
