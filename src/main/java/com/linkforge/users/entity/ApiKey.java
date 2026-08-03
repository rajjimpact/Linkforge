package com.linkforge.users.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Developer API Key entity.
 * Only the SHA-256 hash of the key is stored — raw key shown only on creation.
 * Rate limits are enforced per key via Redis.
 */
@Entity
@Table(
    name = "api_keys",
    indexes = {
        @Index(name = "idx_api_key_hash", columnList = "key_hash"),
        @Index(name = "idx_api_key_user_id", columnList = "user_id"),
        @Index(name = "idx_api_key_enabled", columnList = "enabled")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** SHA-256 hash of the actual API key. Never store raw key. */
    @Column(name = "key_hash", unique = true, nullable = false, length = 64)
    private String keyHash;

    /** Key prefix shown to user for identification (e.g., "lf_abc123..."). */
    @Column(name = "key_prefix", nullable = false, length = 12)
    private String keyPrefix;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Custom rate limit per minute. Null = use global default. */
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "total_requests", nullable = false)
    @Builder.Default
    private long totalRequests = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isValid() {
        return enabled && !isExpired();
    }
}
