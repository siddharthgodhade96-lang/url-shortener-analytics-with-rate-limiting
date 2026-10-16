package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.dto.RedirectCacheEntry;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.ShortCodeConstraintViolation;
import com.urlshortener.exception.UrlConflictException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

@Service
public class UrlService {

    private static final Logger logger = LoggerFactory.getLogger(UrlService.class);
    private static final Set<String> RESERVED_ALIASES = Set.of("api", "admin", "error", "static");

    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;
    private final RedirectCache redirectCache;
    private final ClickAnalyticsRecorder clickAnalyticsRecorder;

    public UrlService(
            UrlRepository urlRepository,
            Base62Encoder base62Encoder,
            RedirectCache redirectCache,
            ClickAnalyticsRecorder clickAnalyticsRecorder
    ) {
        this.urlRepository = urlRepository;
        this.base62Encoder = base62Encoder;
        this.redirectCache = redirectCache;
        this.clickAnalyticsRecorder = clickAnalyticsRecorder;
    }

    @Transactional
    public CreateUrlResponse createUrl(CreateUrlRequest request, Long ownerId) {
        validateUrl(request.originalUrl());
        validateAlias(request.customAlias());

        Url url = new Url(request.originalUrl(), request.customAlias(), ownerId);
        try {
            Url savedUrl = urlRepository.saveAndFlush(url);
            if (request.customAlias() == null) {
                savedUrl.setShortCode(base62Encoder.encode(savedUrl.getId()));
                savedUrl = urlRepository.saveAndFlush(savedUrl);
            }
            return toResponse(savedUrl);
        } catch (DataIntegrityViolationException exception) {
            if (request.customAlias() != null && ShortCodeConstraintViolation.isDuplicate(exception)) {
                throw new UrlConflictException("Custom alias is already in use");
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public URI getRedirectTarget(String code) {
        return getRedirectTarget(code, null, null);
    }

    @Transactional(readOnly = true)
    public URI getRedirectTarget(String code, String referrer, String userAgent) {
        RedirectCacheEntry entry = redirectCache.get(code)
                .orElseGet(() -> loadRedirectEntry(code));
        if (entry.urlId() == null) {
            entry = loadRedirectEntry(code);
        }

        if (!entry.active()) {
            throw new UrlNotFoundException(code);
        }
        if (entry.expiresAt() != null && !entry.expiresAt().isAfter(Instant.now())) {
            throw new UrlExpiredException(code);
        }
        URI target = URI.create(entry.destination());
        try {
            clickAnalyticsRecorder.recordClick(entry.urlId(), Instant.now(), referrer, userAgent);
        } catch (RejectedExecutionException exception) {
            logger.warn("Click analytics queue is full; dropping event for URL {}", entry.urlId(), exception);
        }
        return target;
    }

    @Transactional(readOnly = true)
    public List<CreateUrlResponse> getUrls(Long ownerId) {
        return urlRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CreateUrlResponse getUrl(Long id, Long ownerId) {
        Url url = findOwnedUrl(id, ownerId);
        return toResponse(url);
    }

    @Transactional
    public void deactivateUrl(Long id, Long ownerId) {
        Url url = findOwnedUrl(id, ownerId);
        url.deactivate();
        evictRedirectAfterCommit(url.getShortCode());
    }

    private RedirectCacheEntry loadRedirectEntry(String code) {
        Url url = urlRepository.findByShortCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        RedirectCacheEntry entry = new RedirectCacheEntry(
                url.getId(),
                url.getOriginalUrl(),
                url.isActive(),
                url.getExpiresAt()
        );
        redirectCache.put(code, entry);
        return entry;
    }

    private void evictRedirectAfterCommit(String code) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redirectCache.evict(code);
                }
            });
        } else {
            redirectCache.evict(code);
        }
    }

    private Url findOwnedUrl(Long id, Long ownerId) {
        return urlRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new UrlNotFoundException(id.toString()));
    }

    private void validateUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || scheme == null
                    || (!scheme.toLowerCase(Locale.ROOT).equals("http")
                    && !scheme.toLowerCase(Locale.ROOT).equals("https"))
                    || uri.getHost() == null) {
                throw new InvalidUrlException("URL must be an absolute HTTP or HTTPS URL with a host");
            }
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException("URL is not valid");
        }
    }

    private void validateAlias(String alias) {
        if (alias != null && RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException("Custom alias is reserved");
        }
    }

    private CreateUrlResponse toResponse(Url url) {
        return new CreateUrlResponse(
                url.getId(),
                url.getOriginalUrl(),
                url.getShortCode(),
                "/" + url.getShortCode(),
                url.getCreatedAt(),
                url.isActive()
        );
    }
}
