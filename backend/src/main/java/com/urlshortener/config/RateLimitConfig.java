package com.urlshortener.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public class RateLimitConfig {

    @Valid
    private EndpointLimit createUrl = new EndpointLimit(10, 10.0 / 60.0);
    @Valid
    private EndpointLimit analytics = new EndpointLimit(30, 30.0 / 60.0);
    @Valid
    private EndpointLimit publicRedirect = new EndpointLimit(120, 2.0);

    public EndpointLimit getCreateUrl() {
        return createUrl;
    }

    public void setCreateUrl(EndpointLimit createUrl) {
        this.createUrl = createUrl;
    }

    public EndpointLimit getAnalytics() {
        return analytics;
    }

    public void setAnalytics(EndpointLimit analytics) {
        this.analytics = analytics;
    }

    public EndpointLimit getPublicRedirect() {
        return publicRedirect;
    }

    public void setPublicRedirect(EndpointLimit publicRedirect) {
        this.publicRedirect = publicRedirect;
    }

    public record EndpointLimit(@Positive int capacity, @Positive double refillRate) {
    }
}
