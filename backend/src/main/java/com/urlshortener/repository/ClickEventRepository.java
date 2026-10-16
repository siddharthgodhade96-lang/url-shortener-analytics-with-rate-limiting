package com.urlshortener.repository;

import com.urlshortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByUrl_Id(Long urlId);

    @Query(value = """
            SELECT DATE(ce.clicked_at) AS clickDate, COUNT(ce.id) AS clicks
            FROM click_events ce
            WHERE ce.url_id = :urlId
              AND ce.clicked_at >= :fromInclusive
              AND ce.clicked_at < :toExclusive
            GROUP BY DATE(ce.clicked_at)
            ORDER BY DATE(ce.clicked_at)
            """, nativeQuery = true)
    List<DailyClickCount> findDailyClicks(
            @Param("urlId") Long urlId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    @Query(value = """
            SELECT COUNT(ce.id)
            FROM click_events ce
            JOIN urls u ON u.id = ce.url_id
            WHERE u.owner_id = :ownerId
            """, nativeQuery = true)
    long countClicksForOwner(@Param("ownerId") Long ownerId);

    @Query(value = """
            SELECT COALESCE(NULLIF(TRIM(ce.referrer), ''), '(direct)') AS label, COUNT(ce.id) AS clicks
            FROM click_events ce
            JOIN urls u ON u.id = ce.url_id
            WHERE u.owner_id = :ownerId
            GROUP BY COALESCE(NULLIF(TRIM(ce.referrer), ''), '(direct)')
            ORDER BY clicks DESC
            """, nativeQuery = true)
    List<AnalyticsCount> countClicksByReferrerForOwner(@Param("ownerId") Long ownerId);

    @Query(value = """
            SELECT ce.user_agent AS label, COUNT(ce.id) AS clicks
            FROM click_events ce
            JOIN urls u ON u.id = ce.url_id
            WHERE u.owner_id = :ownerId
            GROUP BY ce.user_agent
            """, nativeQuery = true)
    List<AnalyticsCount> countClicksByUserAgentForOwner(@Param("ownerId") Long ownerId);

    interface DailyClickCount {
        java.time.LocalDate getClickDate();

        long getClicks();
    }

    interface AnalyticsCount {
        String getLabel();

        long getClicks();
    }
}
