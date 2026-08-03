package com.linkforge.analytics.repository;

import com.linkforge.analytics.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    long countByShortUrlId(UUID shortUrlId);

    long countByShortUrlIdAndIsBot(UUID shortUrlId, boolean isBot);

    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.shortUrl.id = :urlId AND c.timestamp >= :from AND c.timestamp <= :to")
    long countByUrlIdAndPeriod(@Param("urlId") UUID urlId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT DATE(c.timestamp) as date, COUNT(c) as clicks
        FROM ClickEvent c
        WHERE c.shortUrl.id = :urlId
        AND c.timestamp >= :from
        AND c.isBot = false
        GROUP BY DATE(c.timestamp)
        ORDER BY DATE(c.timestamp)
        """)
    List<Object[]> findDailyClicksByUrlId(
        @Param("urlId") UUID urlId,
        @Param("from") LocalDateTime from
    );

    @Query("""
        SELECT c.country, COUNT(c) as clicks
        FROM ClickEvent c
        WHERE c.shortUrl.id = :urlId AND c.isBot = false
        GROUP BY c.country
        ORDER BY clicks DESC
        """)
    List<Object[]> findClicksByCountry(@Param("urlId") UUID urlId);

    @Query("""
        SELECT c.device, COUNT(c) as clicks
        FROM ClickEvent c
        WHERE c.shortUrl.id = :urlId AND c.isBot = false
        GROUP BY c.device
        ORDER BY clicks DESC
        """)
    List<Object[]> findClicksByDevice(@Param("urlId") UUID urlId);

    @Query("""
        SELECT c.browser, COUNT(c) as clicks
        FROM ClickEvent c
        WHERE c.shortUrl.id = :urlId AND c.isBot = false
        GROUP BY c.browser
        ORDER BY clicks DESC
        """)
    List<Object[]> findClicksByBrowser(@Param("urlId") UUID urlId);

    @Query("""
        SELECT c.referer, COUNT(c) as clicks
        FROM ClickEvent c
        WHERE c.shortUrl.id = :urlId AND c.referer IS NOT NULL AND c.isBot = false
        GROUP BY c.referer
        ORDER BY clicks DESC
        """)
    List<Object[]> findTopReferrers(@Param("urlId") UUID urlId);

    @Query("""
        SELECT DATE(c.timestamp) as date, COUNT(c) as clicks
        FROM ClickEvent c
        WHERE c.shortUrl.user.id = :userId
        AND c.timestamp >= :from
        AND c.isBot = false
        GROUP BY DATE(c.timestamp)
        ORDER BY DATE(c.timestamp)
        """)
    List<Object[]> findDailyClicksByUserId(@Param("userId") UUID userId, @Param("from") LocalDateTime from);

    /** Check if this IP has clicked this URL before (unique click detection). */
    boolean existsByShortUrlIdAndIpHash(UUID shortUrlId, String ipHash);

    @Query("DELETE FROM ClickEvent c WHERE c.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    long countByShortUrlUserIdAndTimestampAfter(UUID userId, LocalDateTime after);
}
