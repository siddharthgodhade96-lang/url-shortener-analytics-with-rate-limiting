package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.dto.UrlClickStatsResponse;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.filter.RateLimitFilter;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.service.ApiKeyHasher;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(UrlController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private ApiKeyHasher apiKeyHasher;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @Test
    void createsShortUrl() throws Exception {
        when(urlService.createUrl(any(CreateUrlRequest.class), eq(7L))).thenReturn(
                new CreateUrlResponse(1L, "https://example.com", "1", "/1", Instant.now())
        );

        mockMvc.perform(post("/api/urls")
                        .requestAttr("com.urlshortener.filter.ApiKeyFilter.ownerId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("1"));
    }

    @Test
    void rejectsInvalidUrlShape() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .requestAttr("com.urlshortener.filter.ApiKeyFilter.ownerId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/urls"));
    }

    @Test
    void redirectsWith302ToTheOriginalUrl() throws Exception {
        when(urlService.getRedirectTarget("short", "https://referrer.example", "Test Agent"))
                .thenReturn(URI.create("https://example.com/path"));

        mockMvc.perform(get("/short")
                        .header("Referer", "https://referrer.example")
                        .header("User-Agent", "Test Agent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/path"));

        verify(urlService).getRedirectTarget("short", "https://referrer.example", "Test Agent");
    }

    @Test
    void returnsJson404ForUnknownCode() throws Exception {
        when(urlService.getRedirectTarget("missing", null, null))
                .thenThrow(new UrlNotFoundException("missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/missing"));

        verify(urlService).getRedirectTarget("missing", null, null);
    }

    @Test
    void returnsClickStatsForTheAuthenticatedOwner() throws Exception {
        when(analyticsService.getUrlStats(23L, 7L)).thenReturn(new UrlClickStatsResponse(23L, 12L));

        mockMvc.perform(get("/api/urls/23/stats")
                        .requestAttr("com.urlshortener.filter.ApiKeyFilter.ownerId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlId").value(23))
                .andExpect(jsonPath("$.totalClicks").value(12));

        verify(analyticsService).getUrlStats(23L, 7L);
    }

    @Test
    void scopesUrlDetailRequestsToTheAuthenticatedOwner() throws Exception {
        when(urlService.getUrl(23L, 7L)).thenReturn(
                new CreateUrlResponse(23L, "https://example.com", "code", "/code", Instant.now())
        );

        mockMvc.perform(get("/api/urls/23")
                        .requestAttr("com.urlshortener.filter.ApiKeyFilter.ownerId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(23));

        verify(urlService).getUrl(23L, 7L);
    }

    @Test
    void deactivatesUrlUsingAuthenticatedOwnerId() throws Exception {
        mockMvc.perform(delete("/api/urls/23")
                        .requestAttr("com.urlshortener.filter.ApiKeyFilter.ownerId", 7L))
                .andExpect(status().isNoContent());

        verify(urlService).deactivateUrl(23L, 7L);
    }
}
