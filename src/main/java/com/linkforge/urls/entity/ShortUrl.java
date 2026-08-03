package com.linkforge.urls.entity;

import com.linkforge.users.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core URL entity representing a shortened link.
 * Supports: custom aliases, expiry, password protection, one-time use,
 * private links, scheduled activation windows, and click analytics.
 */
@Entity
@Table(
    name = "short_urls",
    indexes = {
        @Index(name = "idx_short_url_short_code", columnList = "short_code", unique = true),
        @Index(name = "idx_short_url_user_id", columnList = "user_id"),
        @Index(name = "idx_short_url_expires_at", columnList = "expires_at"),
        @Index(name = "idx_short_url_is_active", columnList = "is_active"),
        @Index(name = "idx_short_url_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "short_code", unique = true, nullable = false, length = 50)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "title", length = 255)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** BCrypt-hashed password for password-protected links. Null = no protection. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** Only accessible to authenticated users. */
    @Column(name = "is_private", nullable = false)
    @Builder.Default
    private boolean isPrivate = false;

    /** Automatically deleted after first access. */
    @Column(name = "is_one_time", nullable = false)
    @Builder.Default
    private boolean isOneTime = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private long clickCount = 0;

    @Column(name = "unique_click_count", nullable = false)
    @Builder.Default
    private long uniqueClickCount = 0;

    /** Null = never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Link only active after this datetime. Null = immediately active. */
    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    /** Link deactivated after this datetime. Similar to expiresAt but from scheduler. */
    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    @Column(name = "has_qr_code", nullable = false)
    @Builder.Default
    private boolean hasQrCode = false;

    @Column(name = "qr_code_path", length = 500)
    private String qrCodePath;

    /** Flag set when URL has been checked against Safe Browsing. */
    @Column(name = "is_safe", nullable = false)
    @Builder.Default
    private boolean isSafe = true;

    @Column(name = "safe_browsing_checked_at")
    private LocalDateTime safeBrowsingCheckedAt;

    /** Last time the original URL was reachable. */
    @Column(name = "last_health_check_at")
    private LocalDateTime lastHealthCheckAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 20)
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ===== Utility =====

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isScheduledActive() {
        LocalDateTime now = LocalDateTime.now();
        if (scheduledStart != null && now.isBefore(scheduledStart)) return false;
        if (scheduledEnd != null && now.isAfter(scheduledEnd)) return false;
        return true;
    }

    public boolean isAccessible() {
        return isActive && !isExpired() && isScheduledActive() && isSafe;
    }

    public enum HealthStatus {
        UNKNOWN, REACHABLE, REDIRECTED, BROKEN, TIMEOUT
    }
}
