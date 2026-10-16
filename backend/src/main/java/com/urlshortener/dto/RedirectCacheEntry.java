package com.urlshortener.dto;

import java.time.Instant;

public record RedirectCacheEntry(Long urlId, String destination, boolean active, Instant expiresAt) {
}
