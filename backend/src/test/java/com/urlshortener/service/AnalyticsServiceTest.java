package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsSummaryResponse;
import com.urlshortener.dto.DailyClickResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private final UrlRepository urlRepository = mock(UrlRepository.class);
    private final ClickEventRepository clickEventRepository = mock(ClickEventRepository.class);
    private final AnalyticsService analyticsService = new AnalyticsService(urlRepository, clickEventRepository);

    @Test
    void returnsTotalClicksOnlyForTheLinkOwner() {
        when(urlRepository.findByIdAndOwnerId(23L, 7L))
                .thenReturn(Optional.of(new Url("https://example.com", "short", 7L)));
        when(clickEventRepository.countByUrl_Id(23L)).thenReturn(12L);

        assertEquals(12L, analyticsService.getUrlStats(23L, 7L).totalClicks());

        verify(urlRepository).findByIdAndOwnerId(23L, 7L);
        verify(clickEventRepository).countByUrl_Id(23L);
    }

    @Test
    void doesNotExposeAnalyticsForAnotherOwnersLink() {
        when(urlRepository.findByIdAndOwnerId(23L, 7L)).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> analyticsService.getUrlStats(23L, 7L));
        verify(clickEventRepository, never()).countByUrl_Id(23L);
    }

    @Test
    void groupsDailyClicksAcrossInclusiveUtcDates() {
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate to = LocalDate.parse("2026-10-02");
        ClickEventRepository.DailyClickCount row = mock(ClickEventRepository.DailyClickCount.class);
        when(row.getClickDate()).thenReturn(from);
        when(row.getClicks()).thenReturn(4L);
        when(urlRepository.findByIdAndOwnerId(23L, 7L))
                .thenReturn(Optional.of(new Url("https://example.com", "short", 7L)));
        when(clickEventRepository.findDailyClicks(
                23L,
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        )).thenReturn(List.of(row));

        List<DailyClickResponse> result = analyticsService.getDailyClicks(23L, 7L, from, to);

        assertEquals(List.of(new DailyClickResponse(from, 4L)), result);
    }

    @Test
    void rejectsInvalidDateRange() {
        LocalDate from = LocalDate.parse("2026-10-02");
        LocalDate to = LocalDate.parse("2026-10-01");
        when(urlRepository.findByIdAndOwnerId(23L, 7L))
                .thenReturn(Optional.of(new Url("https://example.com", "short", 7L)));

        assertThrows(InvalidUrlException.class, () -> analyticsService.getDailyClicks(23L, 7L, from, to));
        verify(clickEventRepository, never()).findDailyClicks(
                org.mockito.ArgumentMatchers.eq(23L),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
    }

    @Test
    void summarizesReferrersAndDeviceCategoriesForOnlyTheOwner() {
        ClickEventRepository.AnalyticsCount referrer = countRow("https://search.example", 3L);
        ClickEventRepository.AnalyticsCount tablet = countRow("Mozilla iPad", 2L);
        ClickEventRepository.AnalyticsCount mobile = countRow("Mozilla Android Mobile", 4L);
        ClickEventRepository.AnalyticsCount desktop = countRow("Mozilla Chrome", 3L);
        when(clickEventRepository.countClicksForOwner(7L)).thenReturn(9L);
        when(clickEventRepository.countClicksByReferrerForOwner(7L)).thenReturn(List.of(referrer));
        when(clickEventRepository.countClicksByUserAgentForOwner(7L))
                .thenReturn(List.of(tablet, mobile, desktop));

        AnalyticsSummaryResponse result = analyticsService.getSummary(7L);

        assertEquals(9L, result.totalClicks());
        assertEquals(Map.of("https://search.example", 3L), result.clicksByReferrer());
        assertEquals(
                Map.of("tablet", 2L, "mobile", 4L, "desktop", 3L),
                result.clicksByDeviceCategory()
        );
    }

    private ClickEventRepository.AnalyticsCount countRow(String label, long clicks) {
        ClickEventRepository.AnalyticsCount row = mock(ClickEventRepository.AnalyticsCount.class);
        when(row.getLabel()).thenReturn(label);
        when(row.getClicks()).thenReturn(clicks);
        return row;
    }
}
