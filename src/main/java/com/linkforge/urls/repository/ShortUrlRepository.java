package com.linkforge.urls.repository;

import com.linkforge.urls.entity.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Page<ShortUrl> findByUserId(UUID userId, Pageable pageable);

    @Query("""
        SELECT s FROM ShortUrl s
        WHERE s.user.id = :userId
        AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(s.shortCode) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(s.originalUrl) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:isActive IS NULL OR s.isActive = :isActive)
        AND (:hasExpiry IS NULL OR (s.expiresAt IS NOT NULL) = :hasExpiry)
        """)
    Page<ShortUrl> searchByUser(
        @Param("userId") UUID userId,
        @Param("search") String search,
        @Param("isActive") Boolean isActive,
        @Param("hasExpiry") Boolean hasExpiry,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + 1 WHERE s.id = :id")
    void incrementClickCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE ShortUrl s SET s.uniqueClickCount = s.uniqueClickCount + 1 WHERE s.id = :id")
    void incrementUniqueClickCount(@Param("id") UUID id);

    /** Find all expired URLs for cleanup job. */
    @Query("SELECT s FROM ShortUrl s WHERE s.expiresAt IS NOT NULL AND s.expiresAt < :now AND s.isActive = true")
    List<ShortUrl> findExpiredUrls(@Param("now") LocalDateTime now);

    /** Find top N most clicked URLs for a user. */
    @Query("SELECT s FROM ShortUrl s WHERE s.user.id = :userId ORDER BY s.clickCount DESC")
    List<ShortUrl> findTopByUserId(@Param("userId") UUID userId, Pageable pageable);

    /** URLs that need health check (not checked in last 4 hours). */
    @Query("SELECT s FROM ShortUrl s WHERE s.isActive = true AND " +
           "(s.lastHealthCheckAt IS NULL OR s.lastHealthCheckAt < :cutoff)")
    List<ShortUrl> findUrlsNeedingHealthCheck(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    /** Admin: all URLs with search. */
    @Query("""
        SELECT s FROM ShortUrl s
        WHERE (:search IS NULL OR LOWER(s.shortCode) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(s.originalUrl) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<ShortUrl> adminSearchAll(@Param("search") String search, Pageable pageable);

    long countByUserId(UUID userId);

    long countByUserIdAndIsActive(UUID userId, boolean isActive);

    @Query("SELECT SUM(s.clickCount) FROM ShortUrl s WHERE s.user.id = :userId")
    Long sumClickCountByUserId(@Param("userId") UUID userId);
}
