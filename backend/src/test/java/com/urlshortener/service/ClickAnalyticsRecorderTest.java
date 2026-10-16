package com.urlshortener.service;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickAnalyticsRecorderTest {

    private final ClickEventRepository clickEventRepository = mock(ClickEventRepository.class);
    private final UrlRepository urlRepository = mock(UrlRepository.class);
    private final ClickAnalyticsRecorder recorder =
            new ClickAnalyticsRecorder(clickEventRepository, urlRepository);

    @Test
    void persistsOneClickEventWithRequestMetadata() {
        Instant clickedAt = Instant.parse("2026-10-16T07:00:00Z");
        Url url = new Url("https://example.com", "short", 7L);
        when(urlRepository.getReferenceById(23L)).thenReturn(url);

        recorder.recordClick(23L, clickedAt, "https://referrer.example", "Example Browser");

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).saveAndFlush(eventCaptor.capture());
        ClickEvent event = eventCaptor.getValue();
        assertEquals(url, event.getUrl());
        assertEquals(clickedAt, event.getClickedAt());
        assertEquals("https://referrer.example", event.getReferrer());
        assertEquals("Example Browser", event.getUserAgent());
    }

    @Test
    void databaseFailureDoesNotEscapeTheRecorder() {
        when(urlRepository.getReferenceById(23L)).thenThrow(new IllegalStateException("database unavailable"));

        recorder.recordClick(23L, Instant.now(), null, null);

        verify(clickEventRepository, org.mockito.Mockito.never()).saveAndFlush(any(ClickEvent.class));
    }
}
