package com.urlshortener.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.config.RateLimitConfig;
import com.urlshortener.service.RateLimitResult;
import com.urlshortener.service.RateLimiter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RateLimitFilter filter =
            new RateLimitFilter(rateLimiter, new RateLimitConfig(), objectMapper);

    @Test
    void addsLimitHeadersAndContinuesWhenAllowed() throws Exception {
        MockHttpServletRequest request = createRequest("POST", "/api/urls");
        request.setAttribute(ApiKeyFilter.API_KEY_HASH_ATTRIBUTE, "hashed-key");
        request.setAttribute(ApiKeyFilter.API_KEY_CAPACITY_ATTRIBUTE, 10);
        request.setAttribute(ApiKeyFilter.API_KEY_REFILL_RATE_ATTRIBUTE, 10.0 / 60.0);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(rateLimiter.consume("rate_limit:{hashed-key}:{create}", 10.0, 10.0 / 60.0))
                .thenReturn(new RateLimitResult(true, 9, 0));

        filter.doFilter(request, response, chain);

        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
        assertEquals("9", response.getHeader("X-RateLimit-Remaining"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void returns429RetryAfterAndJsonErrorWhenBucketIsEmpty() throws Exception {
        MockHttpServletRequest request = createRequest("POST", "/api/urls");
        request.setAttribute(ApiKeyFilter.API_KEY_HASH_ATTRIBUTE, "hashed-key");
        request.setAttribute(ApiKeyFilter.API_KEY_CAPACITY_ATTRIBUTE, 10);
        request.setAttribute(ApiKeyFilter.API_KEY_REFILL_RATE_ATTRIBUTE, 10.0 / 60.0);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(rateLimiter.consume("rate_limit:{hashed-key}:{create}", 10.0, 10.0 / 60.0))
                .thenReturn(new RateLimitResult(false, 0, 6));

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("6", response.getHeader("Retry-After"));
        JsonNode error = objectMapper.readTree(response.getContentAsString());
        assertEquals("RATE_LIMIT_EXCEEDED", error.get("error").asText());
        assertEquals("Too many requests. Try again later.", error.get("message").asText());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void usesClientIpForPublicRedirectBuckets() throws Exception {
        MockHttpServletRequest request = createRequest("GET", "/short");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(rateLimiter.consume("rate_limit:{192.0.2.10}:{redirect}", 120.0, 2.0))
                .thenReturn(new RateLimitResult(true, 119, 0));

        filter.doFilter(request, response, chain);

        verify(rateLimiter).consume("rate_limit:{192.0.2.10}:{redirect}", 120.0, 2.0);
        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsCorsPreflightWithoutConsumingRateLimitTokens() throws Exception {
        MockHttpServletRequest request = createRequest("OPTIONS", "/api/urls");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(rateLimiter, never()).consume(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble()
        );
        verify(chain).doFilter(request, response);
    }

    @Test
    void readsUsageWithoutConsumingACreationToken() throws Exception {
        MockHttpServletRequest request = createRequest("GET", "/api/keys/me/usage");
        request.setAttribute(ApiKeyFilter.API_KEY_HASH_ATTRIBUTE, "hashed-key");
        request.setAttribute(ApiKeyFilter.API_KEY_CAPACITY_ATTRIBUTE, 10);
        request.setAttribute(ApiKeyFilter.API_KEY_REFILL_RATE_ATTRIBUTE, 10.0 / 60.0);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(rateLimiter.getState("rate_limit:{hashed-key}:{create}", 10.0, 10.0 / 60.0))
                .thenReturn(new RateLimitResult(true, 5.5, 0));

        filter.doFilter(request, response, chain);

        verify(rateLimiter).getState("rate_limit:{hashed-key}:{create}", 10.0, 10.0 / 60.0);
        verify(rateLimiter, never()).consume("rate_limit:{hashed-key}:{create}", 10.0, 10.0 / 60.0);
        assertEquals("5.5", response.getHeader("X-RateLimit-Remaining"));
        assertEquals(new RateLimitResult(true, 5.5, 0),
                request.getAttribute(RateLimitFilter.RATE_LIMIT_RESULT_ATTRIBUTE));
    }

    @Test
    void usesAnalyticsBucketForLinkAndAggregateAnalyticsEndpoints() throws Exception {
        for (String path : new String[]{"/api/urls/23/stats", "/api/analytics/daily", "/api/analytics/summary"}) {
            MockHttpServletRequest request = createRequest("GET", path);
            request.setAttribute(ApiKeyFilter.API_KEY_HASH_ATTRIBUTE, "hashed-key");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            when(rateLimiter.consume("rate_limit:{hashed-key}:{analytics}", 30.0, 0.5))
                    .thenReturn(new RateLimitResult(true, 29, 0));

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
        verify(rateLimiter, org.mockito.Mockito.times(3))
                .consume("rate_limit:{hashed-key}:{analytics}", 30.0, 0.5);
    }

    private MockHttpServletRequest createRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
