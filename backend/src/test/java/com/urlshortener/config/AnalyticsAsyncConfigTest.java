package com.urlshortener.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AnalyticsAsyncConfigTest {

    @Test
    void configuresABoundedExecutorThatRejectsInsteadOfRunningOnTheCaller() {
        ThreadPoolTaskExecutor executor = new AnalyticsAsyncConfig().clickAnalyticsExecutor();
        executor.initialize();

        try {
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(4, executor.getMaxPoolSize());
            assertEquals(500, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
            assertInstanceOf(
                    ThreadPoolExecutor.AbortPolicy.class,
                    executor.getThreadPoolExecutor().getRejectedExecutionHandler()
            );
        } finally {
            executor.shutdown();
        }
    }
}
