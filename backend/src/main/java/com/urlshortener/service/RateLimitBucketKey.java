package com.urlshortener.service;

public final class RateLimitBucketKey {

    private RateLimitBucketKey() {
    }

    public static String forClientAndGroup(String identifier, String endpointGroup) {
        return "rate_limit:{" + identifier + "}:{" + endpointGroup + "}";
    }
}
