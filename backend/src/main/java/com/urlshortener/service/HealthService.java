package com.urlshortener.service;

import com.urlshortener.dto.HealthResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HealthService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public HealthService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public HealthResponse getHealth() {
        String databaseStatus = checkDatabase();
        String redisStatus = checkRedis();
        String overallStatus = UP.equals(databaseStatus) && UP.equals(redisStatus) ? UP : DOWN;

        return new HealthResponse(overallStatus, Map.of(
                "database", databaseStatus,
                "redis", redisStatus
        ));
    }

    private String checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result) ? UP : DOWN;
        } catch (DataAccessException exception) {
            return DOWN;
        }
    }

    private String checkRedis() {
        try {
            String response = redisTemplate.execute(
                    (RedisCallback<String>) RedisConnection::ping
            );
            return "PONG".equalsIgnoreCase(response) ? UP : DOWN;
        } catch (DataAccessException exception) {
            return DOWN;
        }
    }
}
