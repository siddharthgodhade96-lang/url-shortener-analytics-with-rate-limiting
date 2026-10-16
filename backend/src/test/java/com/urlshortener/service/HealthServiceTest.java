package com.urlshortener.service;

import com.urlshortener.dto.HealthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RedisConnection redisConnection = mock(RedisConnection.class);
    private final HealthService healthService = new HealthService(jdbcTemplate, redisTemplate);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRedisPing() {
        doAnswer(invocation -> {
            RedisCallback<String> callback = invocation.getArgument(0);
            return callback.doInRedis(redisConnection);
        }).when(redisTemplate).execute(any(RedisCallback.class));
        when(redisConnection.ping()).thenReturn("PONG");
    }

    @Test
    void reportsUpWhenBothServicesAreAvailable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        HealthResponse response = healthService.getHealth();

        assertEquals("UP", response.status());
        assertEquals("UP", response.checks().get("database"));
        assertEquals("UP", response.checks().get("redis"));
    }

    @Test
    void reportsDatabaseDownWhenTheDatabaseCannotBeReached() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        HealthResponse response = healthService.getHealth();

        assertEquals("DOWN", response.status());
        assertEquals("DOWN", response.checks().get("database"));
        assertEquals("UP", response.checks().get("redis"));
    }

    @Test
    void reportsRedisDownWhenRedisDoesNotRespondWithPong() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(redisConnection.ping()).thenReturn(null);

        HealthResponse response = healthService.getHealth();

        assertEquals("DOWN", response.status());
        assertEquals("UP", response.checks().get("database"));
        assertEquals("DOWN", response.checks().get("redis"));
    }
}
