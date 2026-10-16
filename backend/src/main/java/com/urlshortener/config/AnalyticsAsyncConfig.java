package com.urlshortener.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AnalyticsAsyncConfig implements AsyncConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsAsyncConfig.class);

    @Value("${app.analytics.executor.core-pool-size:2}")
    private int corePoolSize = 2;

    @Value("${app.analytics.executor.max-pool-size:4}")
    private int maxPoolSize = 4;

    @Value("${app.analytics.executor.queue-capacity:500}")
    private int queueCapacity = 500;

    @Bean(name = "clickAnalyticsExecutor")
    public ThreadPoolTaskExecutor clickAnalyticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("click-analytics-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, parameters) ->
                logger.error("Uncaught async analytics failure in {}", method.getName(), exception);
    }
}
