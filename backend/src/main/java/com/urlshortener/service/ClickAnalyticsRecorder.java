package com.urlshortener.service;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;

@Service
public class ClickAnalyticsRecorder {

    private static final Logger logger = LoggerFactory.getLogger(ClickAnalyticsRecorder.class);

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;

    public ClickAnalyticsRecorder(ClickEventRepository clickEventRepository, UrlRepository urlRepository) {
        this.clickEventRepository = clickEventRepository;
        this.urlRepository = urlRepository;
    }

    @Async("clickAnalyticsExecutor")
    public void recordClick(Long urlId, Instant clickedAt, String referrer, String userAgent) {
        try {
            ClickEvent event = new ClickEvent(
                    urlRepository.getReferenceById(urlId),
                    clickedAt,
                    referrer,
                    userAgent
            );
            clickEventRepository.saveAndFlush(event);
        } catch (RuntimeException exception) {
            logger.warn("Could not record click analytics for URL {}", urlId, exception);
        }
    }
}
