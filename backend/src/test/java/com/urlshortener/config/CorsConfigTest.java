package com.urlshortener.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorsConfigTest {

    @Test
    void handlesBrowserPreflightAndExposesRateLimitHeaders() throws Exception {
        FilterRegistrationBean<CorsFilter> registration =
                new CorsConfig().corsFilter("http://localhost:5173");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/urls");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Content-Type,X-API-Key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        registration.getFilter().doFilter(request, response, new MockFilterChain());

        assertEquals(Ordered.HIGHEST_PRECEDENCE, registration.getOrder());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After, Location",
                response.getHeader("Access-Control-Expose-Headers"));
        assertEquals("GET,POST,DELETE,OPTIONS", response.getHeader("Access-Control-Allow-Methods"));
    }
}
