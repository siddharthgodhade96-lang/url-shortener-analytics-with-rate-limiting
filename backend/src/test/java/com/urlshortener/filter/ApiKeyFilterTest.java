package com.urlshortener.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.entity.ApiKey;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.service.ApiKeyHasher;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyFilterTest {

    private final ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
    private final ApiKeyHasher apiKeyHasher = new ApiKeyHasher();
    private final ApiKeyFilter filter = new ApiKeyFilter(
            apiKeyRepository,
            apiKeyHasher,
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void rejectsRequestsWithNoKey() throws Exception {
        MockHttpServletRequest request = urlsRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptsHashedKeyAndSetsItsOwnerOnTheRequest() throws Exception {
        MockHttpServletRequest request = urlsRequest();
        request.addHeader("X-API-Key", "test-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        ApiKey apiKey = new ApiKey(apiKeyHasher.hash("test-key"));
        setId(apiKey, 17L);
        when(apiKeyRepository.findByKeyHashAndActiveTrue(apiKeyHasher.hash("test-key")))
                .thenReturn(Optional.of(apiKey));

        filter.doFilter(request, response, chain);

        assertEquals(17L, request.getAttribute(ApiKeyFilter.OWNER_ID_ATTRIBUTE));
        assertEquals(apiKeyHasher.hash("test-key"), request.getAttribute(ApiKeyFilter.API_KEY_HASH_ATTRIBUTE));
        assertEquals(10, request.getAttribute(ApiKeyFilter.API_KEY_CAPACITY_ATTRIBUTE));
        assertEquals(10.0 / 60.0, request.getAttribute(ApiKeyFilter.API_KEY_REFILL_RATE_ATTRIBUTE));
        verify(apiKeyRepository).findByKeyHashAndActiveTrue(apiKeyHasher.hash("test-key"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsUnknownKeys() throws Exception {
        MockHttpServletRequest request = urlsRequest();
        request.addHeader("X-API-Key", "unknown-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(apiKeyRepository.findByKeyHashAndActiveTrue(apiKeyHasher.hash("unknown-key")))
                .thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void skipsNonUrlApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsCorsPreflightWithoutApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/urls");
        request.setServletPath("/api/urls");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(apiKeyRepository, never()).findByKeyHashAndActiveTrue(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void requiresApiKeyForAnalyticsEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/summary");
        request.setServletPath("/api/analytics/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    private MockHttpServletRequest urlsRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/urls");
        request.setServletPath("/api/urls");
        return request;
    }

    private void setId(ApiKey apiKey, Long id) {
        try {
            var field = ApiKey.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(apiKey, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
