package com.urlshortener.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.Assumptions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisTokenBucketRateLimiterIntegrationTest {

    private GenericContainer<?> redisContainer;
    private LettuceConnectionFactory connectionFactory;
    private RateLimiter luaRateLimiter;
    private Bucket4jRateLimiter bucket4jRateLimiter;

    @BeforeAll
    void connectToRedis() {
        String redisHost = System.getenv("TEST_REDIS_HOST");
        int redisPort;
        if (redisHost != null && !redisHost.isBlank()) {
            redisPort = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
        } else {
            Assumptions.assumeTrue(
                    DockerClientFactory.instance().isDockerAvailable(),
                    "Docker is unavailable; set TEST_REDIS_HOST and TEST_REDIS_PORT to run Redis integration tests"
            );
            redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redisContainer.start();
            redisHost = redisContainer.getHost();
            redisPort = redisContainer.getMappedPort(6379);
        }
        connectionFactory = new LettuceConnectionFactory(redisHost, redisPort);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        luaRateLimiter = new RedisTokenBucketRateLimiter(redisTemplate);
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost(redisHost);
        redisProperties.setPort(redisPort);
        bucket4jRateLimiter = new Bucket4jRateLimiter(redisProperties);
    }

    @AfterAll
    void closeConnection() {
        if (bucket4jRateLimiter != null) {
            bucket4jRateLimiter.close();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    Stream<Arguments> providers() {
        return Stream.of(
                Arguments.of("lua", luaRateLimiter),
                Arguments.of("bucket4j", bucket4jRateLimiter)
        );
    }

    @ParameterizedTest(name = "{0}: allows burst of ten and rejects the eleventh request")
    @MethodSource("providers")
    void allowsBurstOfTenAndRejectsTheEleventhRequest(String provider, RateLimiter rateLimiter) {
        String bucket = newBucket();

        for (int request = 0; request < 10; request++) {
            assertTrue(rateLimiter.consume(bucket, 10, 10.0 / 60.0).allowed());
        }

        RateLimitResult eleventh = rateLimiter.consume(bucket, 10, 10.0 / 60.0);
        assertFalse(eleventh.allowed());
        assertTrue(eleventh.remainingTokens() < 1);
        assertTrue(eleventh.retryAfterSeconds() > 0);
    }

    @ParameterizedTest(name = "{0}: replenishes a token after waiting")
    @MethodSource("providers")
    void replenishesTokensAfterWaiting(String provider, RateLimiter rateLimiter) throws InterruptedException {
        String bucket = newBucket();
        assertTrue(rateLimiter.consume(bucket, 1, 10).allowed());
        assertFalse(rateLimiter.consume(bucket, 1, 10).allowed());

        Thread.sleep(150);

        assertTrue(rateLimiter.consume(bucket, 1, 10).allowed());
    }

    @ParameterizedTest(name = "{0}: keeps buckets separate for different API keys")
    @MethodSource("providers")
    void keepsBucketsSeparateForDifferentApiKeys(String provider, RateLimiter rateLimiter) {
        String runId = UUID.randomUUID().toString();
        String firstKeyBucket = newBucketFor(runId + ":key-one");
        String secondKeyBucket = newBucketFor(runId + ":key-two");
        assertTrue(rateLimiter.consume(firstKeyBucket, 1, 1).allowed());
        assertTrue(rateLimiter.consume(secondKeyBucket, 1, 1).allowed());
        assertFalse(rateLimiter.consume(firstKeyBucket, 1, 1).allowed());
    }

    @ParameterizedTest(name = "{0}: concurrent requests do not exceed bucket capacity")
    @MethodSource("providers")
    void concurrentRequestsDoNotExceedBucketCapacity(String provider, RateLimiter rateLimiter) throws Exception {
        String bucket = newBucket();
        int requestCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int request = 0; request < requestCount; request++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return rateLimiter.consume(bucket, 10, 0.001).allowed();
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertEquals(10, allowed);
        } finally {
            executor.shutdownNow();
        }
    }

    private String newBucket() {
        return newBucketFor(UUID.randomUUID().toString());
    }

    private String newBucketFor(String identifier) {
        return "rate_limit:{" + identifier + "}:{integration}";
    }
}
