package com.urlshortener.service;

import com.urlshortener.config.RateLimitConfig;
import com.urlshortener.entity.ApiKey;
import com.urlshortener.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyProvisioningService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final RateLimitConfig rateLimitConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyProvisioningService(
            ApiKeyRepository apiKeyRepository,
            ApiKeyHasher apiKeyHasher,
            RateLimitConfig rateLimitConfig
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.rateLimitConfig = rateLimitConfig;
    }

    @Transactional
    public String createApiKey() {
        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        apiKeyRepository.saveAndFlush(new ApiKey(
                apiKeyHasher.hash(apiKey),
                rateLimitConfig.getCreateUrl().capacity(),
                rateLimitConfig.getCreateUrl().refillRate()
        ));
        return apiKey;
    }
}
