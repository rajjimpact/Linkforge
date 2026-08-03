package com.linkforge.cache.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Central Redis service providing type-safe operations for all caching needs.
 *
 * Key Schema:
 *   url:{shortCode}              → original URL string
 *   user:{userId}:profile        → serialized user JSON
 *   analytics:{urlId}:clicks     → click counter
 *   ratelimit:ip:{ip}:{type}     → sliding window request counter
 *   ratelimit:apikey:{keyId}     → API key request counter
 *   jwt:blacklist:{jti}          → blacklisted token marker
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    // ===== Key Constants =====
    private static final String URL_KEY = "url:";
    private static final String USER_PROFILE_KEY = "user:%s:profile";
    private static final String ANALYTICS_CLICKS_KEY = "analytics:%s:clicks";
    private static final String RATE_LIMIT_KEY = "ratelimit:ip:";
    private static final String API_RATE_LIMIT_KEY = "ratelimit:apikey:";
    private static final String JWT_BLACKLIST_KEY = "jwt:blacklist:";
    private static final String UNIQUE_CLICK_KEY = "unique:%s:%s";
    private static final String DASHBOARD_KEY = "dashboard:%s";

    // ===== URL Cache =====

    public void cacheUrl(String shortCode, String originalUrl, long ttlSeconds) {
        redisTemplate.opsForValue().set(URL_KEY + shortCode, originalUrl, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Cached URL: {} → {}", shortCode, originalUrl);
    }

    public Optional<String> getCachedUrl(String shortCode) {
        String url = redisTemplate.opsForValue().get(URL_KEY + shortCode);
        return Optional.ofNullable(url);
    }

    public void invalidateUrl(String shortCode) {
        redisTemplate.delete(URL_KEY + shortCode);
        log.debug("Invalidated URL cache for: {}", shortCode);
    }

    // ===== User Profile Cache =====

    public void cacheUserProfile(String userId, Object profile) {
        String key = String.format(USER_PROFILE_KEY, userId);
        objectRedisTemplate.opsForValue().set(key, profile, 15, TimeUnit.MINUTES);
    }

    public Optional<Object> getCachedUserProfile(String userId) {
        String key = String.format(USER_PROFILE_KEY, userId);
        return Optional.ofNullable(objectRedisTemplate.opsForValue().get(key));
    }

    public void invalidateUserProfile(String userId) {
        redisTemplate.delete(String.format(USER_PROFILE_KEY, userId));
    }

    // ===== Analytics Click Counter =====

    public long incrementClickCounter(String urlId) {
        String key = String.format(ANALYTICS_CLICKS_KEY, urlId);
        Long count = redisTemplate.opsForValue().increment(key);
        return count != null ? count : 0L;
    }

    public long getClickCounter(String urlId) {
        String key = String.format(ANALYTICS_CLICKS_KEY, urlId);
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    // ===== Unique Click Detection =====

    public boolean isUniqueClick(String urlId, String ipHash) {
        String key = String.format(UNIQUE_CLICK_KEY, urlId, ipHash);
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            // Mark this IP as seen for 24 hours
            redisTemplate.opsForValue().set(key, "1", 86400, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    // ===== Rate Limiting =====

    /**
     * Increments a sliding window rate limit counter.
     * @param key     The rate limit key
     * @param windowSeconds  The window duration in seconds
     * @return Current count in the window
     */
    public long incrementRateLimit(String key, int windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // First request in window — set expiry
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null ? count : 0L;
    }

    public long incrementApiKeyRateLimit(String apiKeyId, int windowSeconds) {
        String key = API_RATE_LIMIT_KEY + apiKeyId;
        return incrementRateLimit(key, windowSeconds);
    }

    public long getRateLimitCount(String key) {
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    // ===== JWT Blacklist =====

    public void blacklistToken(String jti, long expiryMs) {
        String key = JWT_BLACKLIST_KEY + jti;
        redisTemplate.opsForValue().set(key, "revoked", expiryMs, TimeUnit.MILLISECONDS);
        log.debug("Blacklisted JWT: {}", jti);
    }

    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(JWT_BLACKLIST_KEY + jti));
    }

    // ===== Dashboard Cache =====

    public void cacheDashboard(String userId, Object dashboard) {
        String key = String.format(DASHBOARD_KEY, userId);
        objectRedisTemplate.opsForValue().set(key, dashboard, 5, TimeUnit.MINUTES);
    }

    public Optional<Object> getCachedDashboard(String userId) {
        String key = String.format(DASHBOARD_KEY, userId);
        return Optional.ofNullable(objectRedisTemplate.opsForValue().get(key));
    }

    public void invalidateDashboard(String userId) {
        redisTemplate.delete(String.format(DASHBOARD_KEY, userId));
    }

    // ===== Health Check =====

    public boolean isHealthy() {
        try {
            redisTemplate.opsForValue().set("health:check", "ok", 10, TimeUnit.SECONDS);
            return "ok".equals(redisTemplate.opsForValue().get("health:check"));
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return false;
        }
    }
}
