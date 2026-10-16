package com.urlshortener.dto;

import java.time.Instant;

public record CreateUrlResponse(
        Long id,
        String originalUrl,
        String shortCode,
        String shortUrl,
        Instant createdAt,
        boolean active
) {

    public CreateUrlResponse(
            Long id,
            String originalUrl,
            String shortCode,
            String shortUrl,
            Instant createdAt
    ) {
        this(id, originalUrl, shortCode, shortUrl, createdAt, true);
    }
}
