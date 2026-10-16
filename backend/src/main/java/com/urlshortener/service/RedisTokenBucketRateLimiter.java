package com.urlshortener.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rate-limit", name = "provider", havingValue = "lua")
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.tokenBucketScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult consume(String bucketKey, double capacity, double refillRate) {
        return execute(bucketKey, capacity, refillRate, 1);
    }

    @Override
    public RateLimitResult getState(String bucketKey, double capacity, double refillRate) {
        return execute(bucketKey, capacity, refillRate, 0);
    }

    private RateLimitResult execute(String bucketKey, double capacity, double refillRate, int cost) {
        long expiryMillis = Math.max(1, (long) Math.ceil(2_000 * capacity / refillRate));
        List<?> result = redisTemplate.execute(
                tokenBucketScript,
                List.of(bucketKey),
                Double.toString(capacity),
                Double.toString(refillRate),
                Integer.toString(cost),
                Long.toString(expiryMillis)
        );
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Redis token bucket script returned an invalid result");
        }

        return new RateLimitResult(
                asLong(result.get(0)) == 1,
                Double.parseDouble(result.get(1).toString()),
                asLong(result.get(2))
        );
    }

    private long asLong(Object value) {
        return Long.parseLong(value.toString());
    }
}
