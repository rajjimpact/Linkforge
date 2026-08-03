package com.linkforge.analytics.service;

import com.linkforge.analytics.entity.ClickEvent;
import com.linkforge.analytics.repository.ClickEventRepository;
import com.linkforge.cache.service.RedisService;
import com.linkforge.urls.entity.ShortUrl;
import com.linkforge.urls.repository.ShortUrlRepository;
import com.linkforge.users.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Analytics service — records click events asynchronously.
 * All processing happens on the analyticsExecutor thread pool.
 * Never blocks the redirect path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final RedisService redisService;
    private final GeoLocationService geoLocationService;
    private final UserAgentParserService userAgentParserService;
    private final BotDetectionService botDetectionService;

    @Async("analyticsExecutor")
    @Transactional
    public void recordClickAsync(UUID urlId, HttpServletRequest request, User currentUser) {
        try {
            String ip = extractIp(request);
            String ipHash = hashIp(ip);
            String userAgent = request.getHeader("User-Agent");
            String referer = request.getHeader("Referer");
            String language = request.getHeader("Accept-Language");

            // Bot detection
            BotDetectionService.BotResult botResult = botDetectionService.analyze(userAgent, ip, referer);

            // Geo location
            GeoLocationService.GeoLocation geo = geoLocationService.lookup(ip);

            // User agent parsing
            UserAgentParserService.ParsedUserAgent parsed = userAgentParserService.parse(userAgent);

            // Unique click detection via Redis
            boolean isUnique = redisService.isUniqueClick(urlId.toString(), ipHash);

            // Build click event
            ClickEvent event = ClickEvent.builder()
                    .shortUrl(shortUrlRepository.getReferenceById(urlId))
                    .ipHash(ipHash)
                    .userAgent(truncate(userAgent, 512))
                    .referer(truncate(referer, 2048))
                    .country(geo.country())
                    .countryCode(geo.countryCode())
                    .city(geo.city())
                    .region(geo.region())
                    .latitude(geo.latitude())
                    .longitude(geo.longitude())
                    .device(parsed.deviceType())
                    .browser(parsed.browser())
                    .browserVersion(parsed.browserVersion())
                    .os(parsed.os())
                    .osVersion(parsed.osVersion())
                    .language(parseLanguage(language))
                    .isBot(botResult.isBot())
                    .botConfidence(botResult.confidence())
                    .isUnique(isUnique)
                    .source(ClickEvent.ClickSource.WEB)
                    .build();

            clickEventRepository.save(event);

            // Update click counter atomically
            shortUrlRepository.incrementClickCount(urlId);
            if (isUnique && !botResult.isBot()) {
                shortUrlRepository.incrementUniqueClickCount(urlId);
            }

            // Update Redis counter
            redisService.incrementClickCounter(urlId.toString());

        } catch (Exception e) {
            log.error("Failed to record click event for URL {}: {}", urlId, e.getMessage());
            // Don't rethrow — analytics failures must never affect redirect
        }
    }

    // ===== Analytics Queries =====

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(UUID urlId, UUID userId) {
        long totalClicks = clickEventRepository.countByShortUrlId(urlId);
        long botClicks = clickEventRepository.countByShortUrlIdAndIsBot(urlId, true);
        long humanClicks = totalClicks - botClicks;

        return Map.of(
            "totalClicks", totalClicks,
            "humanClicks", humanClicks,
            "botClicks", botClicks,
            "uniqueClicks", clickEventRepository.countByShortUrlIdAndIsBot(urlId, false)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailyClicks(UUID urlId, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return clickEventRepository.findDailyClicksByUrlId(urlId, from)
                .stream()
                .map(row -> Map.of("date", row[0], "clicks", row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getClicksByCountry(UUID urlId) {
        return clickEventRepository.findClicksByCountry(urlId)
                .stream()
                .map(row -> Map.of("country", row[0] != null ? row[0] : "Unknown", "clicks", row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getClicksByDevice(UUID urlId) {
        return clickEventRepository.findClicksByDevice(urlId)
                .stream()
                .map(row -> Map.of("device", row[0] != null ? row[0] : "Unknown", "clicks", row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getClicksByBrowser(UUID urlId) {
        return clickEventRepository.findClicksByBrowser(urlId)
                .stream()
                .map(row -> Map.of("browser", row[0] != null ? row[0] : "Unknown", "clicks", row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopReferrers(UUID urlId) {
        return clickEventRepository.findTopReferrers(urlId)
                .stream()
                .map(row -> Map.of("referrer", row[0], "clicks", row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(UUID userId, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<Object[]> dailyClicks = clickEventRepository.findDailyClicksByUserId(userId, from);
        long totalClicks = clickEventRepository.countByShortUrlUserIdAndTimestampAfter(userId, from);

        return Map.of(
            "totalClicks", totalClicks,
            "dailyClicks", dailyClicks.stream()
                    .map(row -> Map.of("date", row[0], "clicks", row[1]))
                    .toList()
        );
    }

    // ===== Helpers =====

    private String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    private String extractIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        return xForwardedFor != null ? xForwardedFor.split(",")[0].trim() : request.getRemoteAddr();
    }

    private String parseLanguage(String acceptLanguage) {
        if (acceptLanguage == null) return null;
        return acceptLanguage.split(",")[0].split(";")[0].trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
