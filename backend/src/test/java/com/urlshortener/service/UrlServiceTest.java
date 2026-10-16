package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.dto.RedirectCacheEntry;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlConflictException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UrlServiceTest {

    private final UrlRepository urlRepository = mock(UrlRepository.class);
    private final RedirectCache redirectCache = mock(RedirectCache.class);
    private final ClickAnalyticsRecorder clickAnalyticsRecorder = mock(ClickAnalyticsRecorder.class);
    private final Base62Encoder base62Encoder = new Base62Encoder();
    private final UrlService urlService = new UrlService(
            urlRepository,
            base62Encoder,
            redirectCache,
            clickAnalyticsRecorder
    );

    @Test
    void createsUrlByEncodingTheDatabaseGeneratedId() {
        Url inserted = new Url("https://example.com/path", null, 7L);
        setId(inserted, 62L);
        Url saved = new Url("https://example.com/path", "10", 7L);
        setId(saved, 62L);
        setCreatedAt(saved, Instant.parse("2026-01-01T00:00:00Z"));
        when(urlRepository.saveAndFlush(any(Url.class))).thenReturn(inserted, saved);

        CreateUrlResponse response = urlService.createUrl(
                new CreateUrlRequest("https://example.com/path", null),
                7L
        );

        assertEquals(62L, response.id());
        assertEquals("10", response.shortCode());
        assertEquals("/10", response.shortUrl());
        verify(urlRepository, times(2)).saveAndFlush(any(Url.class));
    }

    @Test
    void returnsConflictWhenCustomAliasViolatesTheUniqueConstraint() {
        SQLException duplicateKey = new SQLException(
                "Duplicate entry 'my-alias' for key 'urls.uk_urls_short_code'",
                "23000",
                1062
        );
        when(urlRepository.saveAndFlush(any(Url.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate alias", duplicateKey));

        assertThrows(UrlConflictException.class, () -> urlService.createUrl(
                new CreateUrlRequest("https://example.com", "my-alias"),
                7L
        ));
    }

    @Test
    void rejectsNonHttpUrlsBeforeAccessingTheRepository() {
        assertThrows(InvalidUrlException.class, () -> urlService.createUrl(
                new CreateUrlRequest("javascript:alert(1)", null),
                7L
        ));
        assertThrows(InvalidUrlException.class, () -> urlService.createUrl(
                new CreateUrlRequest("https:///missing-host", null),
                7L
        ));
        verify(urlRepository, never()).saveAndFlush(any(Url.class));
    }

    @Test
    void rejectsReservedCustomAliases() {
        assertThrows(InvalidUrlException.class, () -> urlService.createUrl(
                new CreateUrlRequest("https://example.com", "Admin"),
                7L
        ));
        verify(urlRepository, never()).saveAndFlush(any(Url.class));
    }

    @Test
    void redirectsToTheStoredUrl() {
        Url url = new Url("https://example.com/path", "custom", 7L);
        setId(url, 23L);
        when(urlRepository.findByShortCode("custom")).thenReturn(Optional.of(url));

        URI target = urlService.getRedirectTarget("custom", "https://referrer.example", "Test Agent");

        assertEquals(URI.create("https://example.com/path"), target);
        verify(clickAnalyticsRecorder).recordClick(
                eq(23L),
                any(Instant.class),
                eq("https://referrer.example"),
                eq("Test Agent")
        );
    }

    @Test
    void redirectsFromCacheWithoutQueryingMySqlAgain() {
        Url url = new Url("https://example.com/path", "custom", 7L);
        setId(url, 23L);
        RedirectCacheEntry cachedEntry = new RedirectCacheEntry(23L, url.getOriginalUrl(), true, null);
        when(redirectCache.get("custom"))
                .thenReturn(Optional.empty(), Optional.of(cachedEntry));
        when(urlRepository.findByShortCode("custom")).thenReturn(Optional.of(url));

        assertEquals(URI.create("https://example.com/path"), urlService.getRedirectTarget("custom"));
        assertEquals(URI.create("https://example.com/path"), urlService.getRedirectTarget("custom"));

        verify(urlRepository, times(1)).findByShortCode("custom");
        verify(redirectCache).put("custom", cachedEntry);
    }

    @Test
    void rejectsUnknownCodes() {
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> urlService.getRedirectTarget("missing"));
        verify(clickAnalyticsRecorder, never()).recordClick(
                any(),
                any(Instant.class),
                any(),
                any()
        );
    }

    @Test
    void returnsGoneForAnExpiredCachedLinkRegardlessOfCacheTtl() {
        when(redirectCache.get("expired")).thenReturn(Optional.of(new RedirectCacheEntry(
                23L,
                "https://example.com/path",
                true,
                Instant.parse("2026-01-01T00:00:00Z")
        )));

        assertThrows(UrlExpiredException.class, () -> urlService.getRedirectTarget("expired"));
        verify(urlRepository, never()).findByShortCode("expired");
    }

    @Test
    void hidesUrlsOwnedByAnotherApiKey() {
        when(urlRepository.findByIdAndOwnerId(12L, 7L)).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> urlService.getUrl(12L, 7L));
        verify(urlRepository).findByIdAndOwnerId(12L, 7L);
    }

    @Test
    void deactivatesOnlyAnOwnedUrl() {
        Url url = new Url("https://example.com/path", "custom", 7L);
        when(urlRepository.findByIdAndOwnerId(12L, 7L)).thenReturn(Optional.of(url));

        urlService.deactivateUrl(12L, 7L);

        org.junit.jupiter.api.Assertions.assertFalse(url.isActive());
        verify(urlRepository).findByIdAndOwnerId(12L, 7L);
    }

    @Test
    void deactivationEvictsCachedRedirectAndTheInactiveLinkReturnsNotFound() {
        Url url = new Url("https://example.com/path", "custom", 7L);
        setId(url, 23L);
        when(redirectCache.get("custom")).thenReturn(
                Optional.of(new RedirectCacheEntry(23L, url.getOriginalUrl(), true, null)),
                Optional.empty()
        );
        when(urlRepository.findByIdAndOwnerId(12L, 7L)).thenReturn(Optional.of(url));
        when(urlRepository.findByShortCode("custom")).thenReturn(Optional.of(url));

        assertEquals(URI.create("https://example.com/path"), urlService.getRedirectTarget("custom"));
        urlService.deactivateUrl(12L, 7L);

        verify(redirectCache).evict("custom");
        assertThrows(UrlNotFoundException.class, () -> urlService.getRedirectTarget("custom"));
        verify(urlRepository).findByShortCode("custom");
    }

    @Test
    void dropsSaturatedAnalyticsQueueWithoutFailingRedirect() {
        when(redirectCache.get("custom")).thenReturn(Optional.of(
                new RedirectCacheEntry(23L, "https://example.com/path", true, null)
        ));
        org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException("queue full"))
                .when(clickAnalyticsRecorder)
                .recordClick(eq(23L), any(Instant.class), eq(null), eq(null));

        assertEquals(URI.create("https://example.com/path"), urlService.getRedirectTarget("custom"));
    }

    @Test
    void listsUrlsUsingOnlyTheAuthenticatedOwnerId() {
        when(urlRepository.findAllByOwnerIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(new Url("https://example.com/path", "custom", 7L)));

        var urls = urlService.getUrls(7L);

        assertEquals(1, urls.size());
        assertEquals("custom", urls.get(0).shortCode());
        verify(urlRepository).findAllByOwnerIdOrderByCreatedAtDesc(7L);
    }

    private void setId(Url url, Long id) {
        try {
            var field = Url.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(url, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void setCreatedAt(Url url, Instant createdAt) {
        try {
            var field = Url.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(url, createdAt);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
