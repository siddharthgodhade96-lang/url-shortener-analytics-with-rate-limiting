package com.urlshortener.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.config.RateLimitConfig;
import com.urlshortener.service.RateLimitResult;
import com.urlshortener.service.RateLimitBucketKey;
import com.urlshortener.service.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String RATE_LIMIT_RESULT_ATTRIBUTE =
            RateLimitFilter.class.getName() + ".rateLimitResult";
    private static final String REDIRECT_GROUP = "redirect";
    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";
    private static final String ERROR_MESSAGE = "Too many requests. Try again later.";

    private final RateLimiter rateLimiter;
    private final RateLimitConfig config;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitConfig config, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return selectPolicy(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        BucketPolicy policy = selectPolicy(request);
        String bucketKey = RateLimitBucketKey.forClientAndGroup(
                policy.clientIdentifier(),
                policy.endpointGroup()
        );
        RateLimitResult result;
        try {
            result = isUsageRequest(request)
                    ? rateLimiter.getState(bucketKey, policy.limit().capacity(), policy.limit().refillRate())
                    : rateLimiter.consume(bucketKey, policy.limit().capacity(), policy.limit().refillRate());
        } catch (RuntimeException exception) {
            if (!REDIRECT_GROUP.equals(policy.endpointGroup())) {
                throw exception;
            }
            // Redirects must still work through MySQL when Redis is down.
            logger.warn("Rate limiter unavailable; allowing redirect without rate limiting", exception);
            filterChain.doFilter(request, response);
            return;
        }
        if (isUsageRequest(request)) {
            request.setAttribute(RATE_LIMIT_RESULT_ATTRIBUTE, result);
        }

        if (!result.allowed()) {
            writeRateLimitError(response, result.retryAfterSeconds());
            return;
        }

        response.setHeader("X-RateLimit-Limit", formatTokens(policy.limit().capacity()));
        response.setHeader("X-RateLimit-Remaining", formatTokens(result.remainingTokens()));
        filterChain.doFilter(request, response);
    }

    private BucketPolicy selectPolicy(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        if ((path.startsWith("/api/urls/") && path.endsWith("/stats"))
                || path.startsWith("/api/analytics/") || "/api/analytics".equals(path)) {
            return new BucketPolicy(
                    "analytics",
                    config.getAnalytics(),
                    authenticatedIdentifier(request)
            );
        }
        if ("POST".equals(method) && "/api/urls".equals(path)) {
            return new BucketPolicy(
                    "create",
                    authenticatedLimit(request),
                    authenticatedIdentifier(request)
            );
        }
        if ("GET".equals(method) && "/api/keys/me/usage".equals(path)) {
            return new BucketPolicy("create", authenticatedLimit(request), authenticatedIdentifier(request));
        }
        if (path.matches("/[^/]+") && !path.startsWith("/api")) {
            return new BucketPolicy(REDIRECT_GROUP, config.getPublicRedirect(), request.getRemoteAddr());
        }
        if (path.startsWith("/api/urls/") || "/api/urls".equals(path)) {
            return new BucketPolicy(
                    "api",
                    authenticatedLimit(request),
                    authenticatedIdentifier(request)
            );
        }
        return null;
    }

    private String authenticatedIdentifier(HttpServletRequest request) {
        String hash = (String) request.getAttribute(ApiKeyFilter.API_KEY_HASH_ATTRIBUTE);
        if (hash == null) {
            throw new IllegalStateException("API-key filter did not set the authenticated key hash");
        }
        return hash;
    }

    private RateLimitConfig.EndpointLimit authenticatedLimit(HttpServletRequest request) {
        int capacity = (int) request.getAttribute(ApiKeyFilter.API_KEY_CAPACITY_ATTRIBUTE);
        double refillRate = (double) request.getAttribute(ApiKeyFilter.API_KEY_REFILL_RATE_ATTRIBUTE);
        return new RateLimitConfig.EndpointLimit(capacity, refillRate);
    }

    private boolean isUsageRequest(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "/api/keys/me/usage".equals(request.getServletPath());
    }

    private void writeRateLimitError(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "error", ERROR_CODE,
                "message", ERROR_MESSAGE
        ));
    }

    private String formatTokens(double tokens) {
        if (tokens == Math.rint(tokens)) {
            return Long.toString((long) tokens);
        }
        return Double.toString(tokens);
    }

    private record BucketPolicy(
            String endpointGroup,
            RateLimitConfig.EndpointLimit limit,
            String clientIdentifier
    ) {
    }
}
