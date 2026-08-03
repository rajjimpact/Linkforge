package com.linkforge.analytics.entity;

import com.linkforge.urls.entity.ShortUrl;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records each click/scan event for analytics.
 * IP addresses are hashed (SHA-256) for privacy compliance.
 * Partitioning candidate for very large deployments (partition by month).
 */
@Entity
@Table(
    name = "click_events",
    indexes = {
        @Index(name = "idx_click_event_url_id", columnList = "short_url_id"),
        @Index(name = "idx_click_event_timestamp", columnList = "timestamp"),
        @Index(name = "idx_click_event_country", columnList = "country"),
        @Index(name = "idx_click_event_device", columnList = "device"),
        @Index(name = "idx_click_event_is_bot", columnList = "is_bot"),
        @Index(name = "idx_click_event_url_timestamp", columnList = "short_url_id,timestamp")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    /** SHA-256 hash of IP address for privacy. */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    /** Raw user agent string (truncated to 512 chars). */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "referer", length = 2048)
    private String referer;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "device", length = 20)
    private DeviceType device;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "browser_version", length = 50)
    private String browserVersion;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "is_bot", nullable = false)
    @Builder.Default
    private boolean isBot = false;

    @Column(name = "bot_confidence", nullable = false)
    @Builder.Default
    private double botConfidence = 0.0;

    /** True if this IP has been seen for this URL before (unique click detection). */
    @Column(name = "is_unique", nullable = false)
    @Builder.Default
    private boolean isUnique = true;

    /** Source: WEB, QR, API */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 10)
    @Builder.Default
    private ClickSource source = ClickSource.WEB;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false, nullable = false)
    private LocalDateTime timestamp;

    public enum DeviceType {
        DESKTOP, MOBILE, TABLET, BOT, UNKNOWN
    }

    public enum ClickSource {
        WEB, QR, API
    }
}
