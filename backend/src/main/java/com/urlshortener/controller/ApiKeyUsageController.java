package com.urlshortener.controller;

import com.urlshortener.dto.RateLimitUsageResponse;
import com.urlshortener.filter.ApiKeyFilter;
import com.urlshortener.filter.RateLimitFilter;
import com.urlshortener.service.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiKeyUsageController {

    @GetMapping("/api/keys/me/usage")
    public RateLimitUsageResponse getUsage(HttpServletRequest request) {
        int capacity = (int) request.getAttribute(ApiKeyFilter.API_KEY_CAPACITY_ATTRIBUTE);
        double refillRate = (double) request.getAttribute(ApiKeyFilter.API_KEY_REFILL_RATE_ATTRIBUTE);
        Object rateLimitResult = request.getAttribute(RateLimitFilter.RATE_LIMIT_RESULT_ATTRIBUTE);
        if (!(rateLimitResult instanceof RateLimitResult state)) {
            throw new IllegalStateException("Rate-limit filter did not provide the bucket state");
        }
        return new RateLimitUsageResponse(capacity, state.remainingTokens(), refillRate);
    }
}
