package com.urlshortener.service;

import com.urlshortener.config.RateLimitConfig;
import com.urlshortener.entity.ApiKey;
import com.urlshortener.repository.ApiKeyRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiKeyProvisioningServiceTest {

    private final ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
    private final ApiKeyHasher apiKeyHasher = new ApiKeyHasher();
    private final ApiKeyProvisioningService provisioningService =
            new ApiKeyProvisioningService(apiKeyRepository, apiKeyHasher, new RateLimitConfig());

    @Test
    void storesOnlyTheHashAndReturnsTheRawKeyOnce() {
        String rawKey = provisioningService.createApiKey();

        assertEquals(43, rawKey.length());
        assertNotEquals(rawKey, apiKeyHasher.hash(rawKey));
        verify(apiKeyRepository).saveAndFlush(any(ApiKey.class));
    }

    @Test
    void generatesDifferentKeys() {
        String firstKey = provisioningService.createApiKey();
        String secondKey = provisioningService.createApiKey();

        assertNotEquals(firstKey, secondKey);
    }
}
