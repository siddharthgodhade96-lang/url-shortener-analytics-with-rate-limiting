package com.urlshortener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.RedirectCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedirectCache {

    private static final Logger logger = LoggerFactory.getLogger(RedirectCache.class);
    private static final String KEY_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedirectCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<RedirectCacheEntry> get(String code) {
        try {
            String value = redisTemplate.opsForValue().get(key(code));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(value, RedirectCacheEntry.class));
        } catch (DataAccessException exception) {
            logger.warn("Could not read redirect cache for code {}", code, exception);
            return Optional.empty();
        } catch (JsonProcessingException exception) {
            logger.warn("Could not decode redirect cache for code {}", code, exception);
            return Optional.empty();
        }
    }

    public void put(String code, RedirectCacheEntry entry) {
        try {
            String value = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(key(code), value, CACHE_TTL);
        } catch (DataAccessException exception) {
            logger.warn("Could not write redirect cache for code {}", code, exception);
        } catch (JsonProcessingException exception) {
            logger.warn("Could not encode redirect cache for code {}", code, exception);
        }
    }

    public void evict(String code) {
        try {
            redisTemplate.delete(key(code));
        } catch (DataAccessException exception) {
            logger.warn("Could not evict redirect cache for code {}", code, exception);
        }
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}
