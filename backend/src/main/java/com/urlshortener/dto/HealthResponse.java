package com.urlshortener.dto;

import java.util.Map;

public record HealthResponse(String status, Map<String, String> checks) {
}
