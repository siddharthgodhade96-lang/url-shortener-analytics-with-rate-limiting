package com.urlshortener.service;

public interface RateLimiter {

    RateLimitResult consume(String bucketKey, double capacity, double refillRate);

    RateLimitResult getState(String bucketKey, double capacity, double refillRate);
}
