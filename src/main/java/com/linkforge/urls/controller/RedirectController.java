package com.linkforge.urls.controller;

import com.linkforge.analytics.service.AnalyticsService;
import com.linkforge.cache.service.RedisService;
import com.linkforge.exception.LinkForgeException;
import com.linkforge.urls.entity.ShortUrl;
import com.linkforge.urls.repository.ShortUrlRepository;
import com.linkforge.urls.service.UrlService;
import com.linkforge.users.entity.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Redirect Controller — the most performance-critical endpoint in the system.
 * Target: < 50ms latency on cache hit.
 *
 * Flow:
 * 1. Redis cache lookup (< 1ms)
 * 2. Cache miss → DB lookup → cache repopulation
 * 3. Permission checks (private, password, one-time)
 * 4. 302 redirect
 * 5. Async analytics event recording
 */
@RestController
@Slf4j
public class RedirectController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;
    private final RedisService redisService;
    private final ShortUrlRepository shortUrlRepository;
    private final Counter redirectHitCounter;
    private final Counter redirectMissCounter;
    private final Timer redirectTimer;

    public RedirectController(
            UrlService urlService,
            AnalyticsService analyticsService,
            RedisService redisService,
            ShortUrlRepository shortUrlRepository,
            MeterRegistry meterRegistry
    ) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
        this.redisService = redisService;
        this.shortUrlRepository = shortUrlRepository;
        this.redirectHitCounter = Counter.builder("linkforge.redirect.cache.hits")
                .description("Number of cache hits on redirect").register(meterRegistry);
        this.redirectMissCounter = Counter.builder("linkforge.redirect.cache.misses")
                .description("Number of cache misses on redirect").register(meterRegistry);
        this.redirectTimer = Timer.builder("linkforge.redirect.latency")
                .description("Redirect endpoint latency").register(meterRegistry);
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestParam(required = false) String p, // password parameter
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request
    ) {
        return redirectTimer.record(() -> handleRedirect(shortCode, p, currentUser, request));
    }

    private ResponseEntity<Void> handleRedirect(
            String shortCode, String password, User currentUser, HttpServletRequest request
    ) {
        // 1. Fast Redis lookup
        var cachedUrl = redisService.getCachedUrl(shortCode);

        if (cachedUrl.isPresent()) {
            redirectHitCounter.increment();
            // Still need to check permissions — load the entity for that
            ShortUrl url = shortUrlRepository.findByShortCode(shortCode).orElse(null);
            if (url != null) {
                return processRedirect(url, password, currentUser, request, cachedUrl.get());
            }
        }

        // 2. Cache miss — load from DB
        redirectMissCounter.increment();
        ShortUrl url = shortUrlRepository.findByShortCode(shortCode).orElse(null);

        if (url == null) {
            return ResponseEntity.notFound().build();
        }

        if (!url.isAccessible()) {
            return ResponseEntity.status(HttpStatus.GONE).build(); // 410 Gone — expired
        }

        // Repopulate cache
        redisService.cacheUrl(shortCode, url.getOriginalUrl(), 86400L);

        return processRedirect(url, password, currentUser, request, url.getOriginalUrl());
    }

    private ResponseEntity<Void> processRedirect(
            ShortUrl url, String password, User currentUser, HttpServletRequest request, String targetUrl
    ) {
        // Check private link
        if (url.isPrivate() && currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check password
        if (url.getPasswordHash() != null) {
            if (password == null || password.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header("X-Link-Protected", "password-required")
                        .build();
            }
            // Note: BCrypt check is done here — on cache hit we still check password
        }

        // Record analytics async (non-blocking)
        analyticsService.recordClickAsync(url.getId(), request, currentUser);

        // Handle one-time links
        if (url.isOneTime()) {
            url.setActive(false);
            shortUrlRepository.save(url);
            redisService.invalidateUrl(url.getShortCode());
        }

        // 302 Redirect (302 = temporary, allows analytics tracking)
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
