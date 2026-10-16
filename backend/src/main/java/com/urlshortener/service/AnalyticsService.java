package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsSummaryResponse;
import com.urlshortener.dto.DailyClickResponse;
import com.urlshortener.dto.UrlClickStatsResponse;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(UrlRepository urlRepository, ClickEventRepository clickEventRepository) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional(readOnly = true)
    public UrlClickStatsResponse getUrlStats(Long urlId, Long ownerId) {
        requireOwnedUrl(urlId, ownerId);
        return new UrlClickStatsResponse(urlId, clickEventRepository.countByUrl_Id(urlId));
    }

    @Transactional(readOnly = true)
    public List<DailyClickResponse> getDailyClicks(
            Long urlId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        requireOwnedUrl(urlId, ownerId);
        if (from.isAfter(to)) {
            throw new InvalidUrlException("The from date must be on or before the to date");
        }

        return clickEventRepository.findDailyClicks(
                        urlId,
                        from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                ).stream()
                .map(row -> new DailyClickResponse(row.getClickDate(), row.getClicks()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(Long ownerId) {
        Map<String, Long> referrers = new LinkedHashMap<>();
        clickEventRepository.countClicksByReferrerForOwner(ownerId)
                .forEach(row -> referrers.put(row.getLabel(), row.getClicks()));

        Map<String, Long> deviceCategories = new LinkedHashMap<>();
        clickEventRepository.countClicksByUserAgentForOwner(ownerId).forEach(row ->
                deviceCategories.merge(deviceCategory(row.getLabel()), row.getClicks(), Long::sum)
        );

        return new AnalyticsSummaryResponse(
                clickEventRepository.countClicksForOwner(ownerId),
                referrers,
                deviceCategories
        );
    }

    private void requireOwnedUrl(Long urlId, Long ownerId) {
        if (urlRepository.findByIdAndOwnerId(urlId, ownerId).isEmpty()) {
            throw new UrlNotFoundException(urlId.toString());
        }
    }

    private String deviceCategory(String userAgent) {
        String value = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (value.contains("ipad") || value.contains("tablet") || value.contains("kindle")
                || value.contains("silk") || (value.contains("android") && !value.contains("mobile"))) {
            return "tablet";
        }
        if (value.contains("mobile") || value.contains("iphone") || value.contains("ipod")
                || value.contains("android")) {
            return "mobile";
        }
        return "desktop";
    }
}
