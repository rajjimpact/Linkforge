package com.linkforge.scheduler;

import com.linkforge.auth.repository.EmailVerificationTokenRepository;
import com.linkforge.auth.repository.PasswordResetTokenRepository;
import com.linkforge.auth.repository.RefreshTokenRepository;
import com.linkforge.urls.repository.ShortUrlRepository;
import com.linkforge.cache.service.RedisService;
import com.linkforge.analytics.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

/**
 * Background scheduled jobs.
 * All jobs use cron expressions from application.yml for easy environment-specific tuning.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobs {

    private final ShortUrlRepository shortUrlRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final ClickEventRepository clickEventRepository;
    private final RedisService redisService;
    private final WebClient.Builder webClientBuilder;

    // ===== 1. Expired URL Cleanup =====

    @Scheduled(cron = "${application.scheduler.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupExpiredUrls() {
        log.info("[SCHEDULER] Starting expired URL cleanup");
        var expired = shortUrlRepository.findExpiredUrls(LocalDateTime.now());
        int count = 0;
        for (var url : expired) {
            url.setActive(false);
            redisService.invalidateUrl(url.getShortCode());
            count++;
        }
        shortUrlRepository.saveAll(expired);
        log.info("[SCHEDULER] Deactivated {} expired URLs", count);
    }

    // ===== 2. Auth Token Cleanup =====

    @Scheduled(cron = "${application.scheduler.token-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("[SCHEDULER] Starting token cleanup");
        LocalDateTime cutoff = LocalDateTime.now();
        int refreshDeleted = refreshTokenRepository.deleteExpiredAndRevoked(cutoff);
        int prtDeleted = passwordResetTokenRepository.deleteExpiredAndUsed(cutoff);
        int evtDeleted = emailVerificationTokenRepository.deleteExpiredAndVerified(cutoff);
        log.info("[SCHEDULER] Cleaned up tokens: refresh={}, passwordReset={}, emailVerify={}",
                refreshDeleted, prtDeleted, evtDeleted);
    }

    // ===== 3. Cache Refresh (Top URLs) =====

    @Scheduled(cron = "${application.scheduler.cache-refresh-cron:0 0 * * * *}")
    @Transactional(readOnly = true)
    public void refreshTopUrlCache() {
        log.info("[SCHEDULER] Refreshing top URL cache");
        // Pre-warm cache with top 1000 most clicked URLs
        var topUrls = shortUrlRepository.findAll(PageRequest.of(0, 1000));
        topUrls.forEach(url -> {
            if (url.isActive() && !url.isExpired()) {
                redisService.cacheUrl(url.getShortCode(), url.getOriginalUrl(), 86400L);
            }
        });
        log.info("[SCHEDULER] Warmed cache for {} URLs", topUrls.getNumberOfElements());
    }

    // ===== 4. URL Health Check =====

    @Scheduled(cron = "${application.scheduler.url-health-check-cron:0 0 */4 * * *}")
    @Transactional
    public void checkUrlHealth() {
        log.info("[SCHEDULER] Starting URL health check");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(4);
        var urlsToCheck = shortUrlRepository.findUrlsNeedingHealthCheck(cutoff, PageRequest.of(0, 500));
        WebClient client = webClientBuilder.build();

        urlsToCheck.forEach(url -> {
            try {
                var status = client.head()
                        .uri(url.getOriginalUrl())
                        .retrieve()
                        .toBodilessEntity()
                        .map(resp -> {
                            int code = resp.getStatusCode().value();
                            if (code >= 200 && code < 300) return com.linkforge.urls.entity.ShortUrl.HealthStatus.REACHABLE;
                            if (code >= 300 && code < 400) return com.linkforge.urls.entity.ShortUrl.HealthStatus.REDIRECTED;
                            return com.linkforge.urls.entity.ShortUrl.HealthStatus.BROKEN;
                        })
                        .onErrorReturn(com.linkforge.urls.entity.ShortUrl.HealthStatus.BROKEN)
                        .block();

                url.setHealthStatus(status != null ? status : com.linkforge.urls.entity.ShortUrl.HealthStatus.BROKEN);
                url.setLastHealthCheckAt(LocalDateTime.now());
                shortUrlRepository.save(url);

            } catch (Exception e) {
                url.setHealthStatus(com.linkforge.urls.entity.ShortUrl.HealthStatus.TIMEOUT);
                url.setLastHealthCheckAt(LocalDateTime.now());
                shortUrlRepository.save(url);
            }
        });
        log.info("[SCHEDULER] Health checked {} URLs", urlsToCheck.size());
    }

    // ===== 5. Old Analytics Cleanup =====

    @Scheduled(cron = "0 0 1 1 * *") // 1st of each month at 1 AM
    @Transactional
    public void cleanupOldAnalytics() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(1);
        int deleted = clickEventRepository.deleteOlderThan(cutoff);
        log.info("[SCHEDULER] Deleted {} click events older than 1 year", deleted);
    }
}
