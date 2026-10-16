package com.urlshortener.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.entity.ApiKey;
import com.urlshortener.exception.ApiErrorResponse;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.service.ApiKeyHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String OWNER_ID_ATTRIBUTE = ApiKeyFilter.class.getName() + ".ownerId";
    public static final String API_KEY_HASH_ATTRIBUTE = ApiKeyFilter.class.getName() + ".keyHash";
    public static final String API_KEY_CAPACITY_ATTRIBUTE = ApiKeyFilter.class.getName() + ".capacity";
    public static final String API_KEY_REFILL_RATE_ATTRIBUTE = ApiKeyFilter.class.getName() + ".refillRate";
    private static final String HEADER_NAME = "X-API-Key";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(
            ApiKeyRepository apiKeyRepository,
            ApiKeyHasher apiKeyHasher,
            ObjectMapper objectMapper
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !("/api/urls".equals(path)
                || path.startsWith("/api/urls/")
                || "/api/analytics".equals(path)
                || path.startsWith("/api/analytics/")
                || "/api/keys/me/usage".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER_NAME);
        if (apiKey == null || apiKey.isBlank()) {
            writeUnauthorized(request, response);
            return;
        }

        String keyHash = apiKeyHasher.hash(apiKey);
        Optional<ApiKey> storedKey = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);
        if (storedKey.isEmpty()) {
            writeUnauthorized(request, response);
            return;
        }

        request.setAttribute(OWNER_ID_ATTRIBUTE, storedKey.get().getId());
        request.setAttribute(API_KEY_HASH_ATTRIBUTE, keyHash);
        request.setAttribute(API_KEY_CAPACITY_ATTRIBUTE, storedKey.get().getCapacity());
        request.setAttribute(API_KEY_REFILL_RATE_ATTRIBUTE, storedKey.get().getRefillRate());
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, HEADER_NAME);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "A valid X-API-Key header is required",
                request.getRequestURI()
        ));
    }
}
