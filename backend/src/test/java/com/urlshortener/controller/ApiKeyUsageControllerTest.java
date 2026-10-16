package com.urlshortener.controller;

import com.urlshortener.filter.ApiKeyFilter;
import com.urlshortener.filter.RateLimitFilter;
import com.urlshortener.service.RateLimitResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyUsageControllerTest {

    private final ApiKeyUsageController controller = new ApiKeyUsageController();

    @Test
    void reportsTheBucketStateFromTheRateLimitFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyFilter.API_KEY_CAPACITY_ATTRIBUTE, 10);
        request.setAttribute(ApiKeyFilter.API_KEY_REFILL_RATE_ATTRIBUTE, 10.0 / 60.0);
        request.setAttribute(
                RateLimitFilter.RATE_LIMIT_RESULT_ATTRIBUTE,
                new RateLimitResult(true, 6.5, 0)
        );

        var usage = controller.getUsage(request);

        assertEquals(10, usage.capacity());
        assertEquals(6.5, usage.remainingTokens());
        assertEquals(10.0 / 60.0, usage.refillRate());
    }
}
