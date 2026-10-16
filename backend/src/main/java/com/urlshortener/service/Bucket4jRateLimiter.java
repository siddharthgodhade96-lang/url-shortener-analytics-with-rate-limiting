package com.urlshortener.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@ConditionalOnProperty(
        prefix = "rate-limit",
        name = "provider",
        havingValue = "bucket4j",
        matchIfMissing = true
)
public class Bucket4jRateLimiter implements RateLimiter {

    private static final Duration IDLE_RETENTION = Duration.ofMinutes(30);
    private static final String REDIS_KEY_PREFIX = "bucket4j:";

    private final RedisClient redisClient;
    private volatile StatefulRedisConnection<byte[], byte[]> redisConnection;
    private volatile LettuceBasedProxyManager<byte[]> proxyManager;

    public Bucket4jRateLimiter(RedisProperties redisProperties) {
        this.redisClient = RedisClient.create(createRedisUri(redisProperties));
    }

    @Override
    public RateLimitResult consume(String bucketKey, double capacity, double refillRate) {
        ConsumptionProbe probe = bucket(bucketKey, capacity, refillRate).tryConsumeAndReturnRemaining(1);
        return new RateLimitResult(
                probe.isConsumed(),
                probe.getRemainingTokens(),
                probe.isConsumed() ? 0 : nanosToSeconds(probe.getNanosToWaitForRefill())
        );
    }

    @Override
    public RateLimitResult getState(String bucketKey, double capacity, double refillRate) {
        long remainingTokens = bucket(bucketKey, capacity, refillRate).getAvailableTokens();
        return new RateLimitResult(true, remainingTokens, 0);
    }

    @PreDestroy
    public void close() {
        StatefulRedisConnection<byte[], byte[]> connection = redisConnection;
        if (connection != null) {
            connection.close();
        }
        redisClient.shutdown();
    }

    private Bucket bucket(String bucketKey, double capacity, double refillRate) {
        if (!Double.isFinite(capacity) || capacity < 1 || capacity != Math.rint(capacity)
                || !Double.isFinite(refillRate) || refillRate <= 0) {
            throw new IllegalArgumentException("Capacity must be a positive integer and refill rate must be positive");
        }

        long refillPeriodNanos = Math.max(1, (long) Math.ceil(1_000_000_000d / refillRate));
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity((long) capacity)
                        .refillGreedy(1, Duration.ofNanos(refillPeriodNanos))
                        .build())
                .build();

        String redisKey = REDIS_KEY_PREFIX + bucketKey;
        return proxyManager().builder().build(redisKey.getBytes(StandardCharsets.UTF_8), configuration);
    }

    private LettuceBasedProxyManager<byte[]> proxyManager() {
        LettuceBasedProxyManager<byte[]> manager = proxyManager;
        if (manager == null) {
            synchronized (this) {
                manager = proxyManager;
                if (manager == null) {
                    redisConnection = redisClient.connect(ByteArrayCodec.INSTANCE);
                    manager = Bucket4jLettuce.casBasedBuilder(redisConnection)
                            .expirationAfterWrite(
                                    ExpirationAfterWriteStrategy
                                            .basedOnTimeForRefillingBucketUpToMax(IDLE_RETENTION)
                            )
                            .build();
                    proxyManager = manager;
                }
            }
        }
        return manager;
    }

    private long nanosToSeconds(long nanoseconds) {
        long seconds = nanoseconds / 1_000_000_000L;
        if (nanoseconds % 1_000_000_000L != 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }

    private static RedisURI createRedisUri(RedisProperties properties) {
        if (properties.getUrl() != null && !properties.getUrl().isBlank()) {
            return RedisURI.create(properties.getUrl());
        }
        RedisURI.Builder builder = RedisURI.Builder.redis(properties.getHost(), properties.getPort())
                .withDatabase(properties.getDatabase());

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            String password = properties.getPassword();
            builder.withAuthentication(
                    properties.getUsername(),
                    password == null ? new char[0] : password.toCharArray()
            );
        } else if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            builder.withPassword(properties.getPassword().toCharArray());
        }
        if (properties.getTimeout() != null) {
            builder.withTimeout(properties.getTimeout());
        }
        return builder.build();
    }
}
