package com.linkforge.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Google Safe Browsing API v4 integration.
 * Checks URLs for phishing, malware, and unwanted software.
 * Feature-flagged — disabled when API key is not configured.
 */
@Service
@Slf4j
public class SafeBrowsingService {

    private final WebClient webClient;

    @Value("${application.safe-browsing.enabled:false}")
    private boolean enabled;

    @Value("${application.safe-browsing.api-key:}")
    private String apiKey;

    @Value("${application.safe-browsing.api-url}")
    private String apiUrl;

    public SafeBrowsingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Checks a URL against Google Safe Browsing API.
     * Returns true if the URL is safe (or if Safe Browsing is disabled).
     */
    @Async("taskExecutor")
    public CompletableFuture<Boolean> isSafe(String url) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.debug("Safe Browsing disabled — skipping check for: {}", url);
            return CompletableFuture.completedFuture(true);
        }

        try {
            Map<String, Object> requestBody = Map.of(
                "client", Map.of("clientId", "linkforge", "clientVersion", "1.0.0"),
                "threatInfo", Map.of(
                    "threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                    "platformTypes", List.of("ANY_PLATFORM"),
                    "threatEntryTypes", List.of("URL"),
                    "threatEntries", List.of(Map.of("url", url))
                )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            // Empty response = no threats found
            boolean isSafe = response == null || !response.containsKey("matches");
            if (!isSafe) {
                log.warn("URL flagged by Safe Browsing: {}", url);
            }
            return CompletableFuture.completedFuture(isSafe);

        } catch (Exception e) {
            log.error("Safe Browsing API error for URL {}: {}", url, e.getMessage());
            // On API error, fail-open (allow the URL)
            return CompletableFuture.completedFuture(true);
        }
    }
}
