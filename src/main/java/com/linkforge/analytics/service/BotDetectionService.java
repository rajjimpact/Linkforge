package com.linkforge.analytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Bot detection service using behavioral heuristics.
 * Checks User-Agent patterns, known bot signatures, and suspicious behaviors.
 * Returns a confidence score (0.0 = definitely human, 1.0 = definitely bot).
 */
@Service
@Slf4j
public class BotDetectionService {

    private static final Set<String> BOT_UA_KEYWORDS = Set.of(
        "bot", "crawler", "spider", "scraper", "curl", "wget", "python-requests",
        "apache-httpclient", "java/", "okhttp", "go-http-client", "libwww",
        "googlebot", "bingbot", "slurp", "duckduckbot", "baiduspider",
        "yandexbot", "facebookexternalhit", "twitterbot", "linkedinbot",
        "rogerbot", "semrushbot", "ahrefs", "mj12bot", "dotbot", "screaming",
        "pingdom", "uptimerobot", "datadog", "postman"
    );

    private static final Set<String> BOT_IP_RANGES = Set.of(
        "66.249.", // Google
        "40.77.",  // Bing
        "207.46."  // Bing
    );

    public BotResult analyze(String userAgent, String ip, String referer) {
        double confidence = 0.0;

        if (userAgent == null || userAgent.isBlank()) {
            return new BotResult(true, 0.9); // No UA = very likely bot
        }

        String ua = userAgent.toLowerCase();

        // Check known bot UA patterns
        for (String keyword : BOT_UA_KEYWORDS) {
            if (ua.contains(keyword)) {
                confidence = 0.95;
                return new BotResult(true, confidence);
            }
        }

        // Check known bot IP ranges
        if (ip != null) {
            for (String range : BOT_IP_RANGES) {
                if (ip.startsWith(range)) {
                    confidence = Math.max(confidence, 0.8);
                }
            }
        }

        // Heuristics: very short UA or all lowercase
        if (ua.length() < 20) confidence = Math.max(confidence, 0.6);

        // No common browser markers
        boolean hasBrowserMarker = ua.contains("mozilla") || ua.contains("webkit") ||
                                   ua.contains("gecko") || ua.contains("applewebkit");
        if (!hasBrowserMarker) confidence = Math.max(confidence, 0.7);

        boolean isBot = confidence >= 0.6;
        return new BotResult(isBot, confidence);
    }

    public record BotResult(boolean isBot, double confidence) {}
}
