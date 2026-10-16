package com.urlshortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.RedirectCacheEntry;
import com.urlshortener.entity.Url;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectCacheTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    RedirectCacheTest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storesRedirectDtoUnderUrlPrefixForThirtyMinutes() {
        RedirectCache cache = new RedirectCache(redisTemplate, objectMapper);
        RedirectCacheEntry entry = new RedirectCacheEntry(23L, "https://example.com/path", true, null);

        cache.put("short", entry);

        verify(valueOperations).set(
                eq("url:short"),
                eq("{\"urlId\":23,\"destination\":\"https://example.com/path\",\"active\":true,\"expiresAt\":null}"),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void fallsBackToMysqlWhenRedisIsUnavailable() {
        when(valueOperations.get("url:short"))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(valueOperations).set(eq("url:short"), anyString(), eq(Duration.ofMinutes(30)));

        UrlRepository urlRepository = mock(UrlRepository.class);
        Url url = new Url("https://example.com/path", "short", 7L);
        setId(url, 23L);
        when(urlRepository.findByShortCode("short")).thenReturn(Optional.of(url));
        UrlService urlService = new UrlService(
                urlRepository,
                new Base62Encoder(),
                new RedirectCache(redisTemplate, objectMapper),
                new ClickAnalyticsRecorder(mock(com.urlshortener.repository.ClickEventRepository.class), urlRepository)
        );

        URI target = urlService.getRedirectTarget("short");

        assertEquals(URI.create("https://example.com/path"), target);
        verify(urlRepository, times(1)).findByShortCode("short");
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
}
