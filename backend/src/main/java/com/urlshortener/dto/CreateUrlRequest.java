package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUrlRequest(
        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL must be at most 2048 characters")
        String originalUrl,
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]{3,30}$",
                message = "Custom alias must be 3 to 30 letters, digits, underscores, or hyphens"
        )
        String customAlias
) {
}
