package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.dto.UrlClickStatsResponse;
import com.urlshortener.filter.ApiKeyFilter;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    public UrlController(UrlService urlService, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<CreateUrlResponse> createUrl(
            @Valid @RequestBody CreateUrlRequest request,
            HttpServletRequest httpRequest
    ) {
        CreateUrlResponse response = urlService.createUrl(request, ownerId(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/urls")
    public List<CreateUrlResponse> getUrls(HttpServletRequest request) {
        return urlService.getUrls(ownerId(request));
    }

    @GetMapping("/api/urls/{id}")
    public CreateUrlResponse getUrl(@PathVariable Long id, HttpServletRequest request) {
        return urlService.getUrl(id, ownerId(request));
    }

    @DeleteMapping("/api/urls/{id}")
    public ResponseEntity<Void> deactivateUrl(@PathVariable Long id, HttpServletRequest request) {
        urlService.deactivateUrl(id, ownerId(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        URI target = urlService.getRedirectTarget(
                code,
                request.getHeader("Referer"),
                request.getHeader("User-Agent")
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target.toString())
                .build();
    }

    @GetMapping("/api/urls/{id}/stats")
    public UrlClickStatsResponse getUrlStats(@PathVariable Long id, HttpServletRequest request) {
        return analyticsService.getUrlStats(id, ownerId(request));
    }

    private Long ownerId(HttpServletRequest request) {
        return (Long) request.getAttribute(ApiKeyFilter.OWNER_ID_ATTRIBUTE);
    }
}
