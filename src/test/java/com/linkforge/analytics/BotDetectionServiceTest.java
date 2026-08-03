package com.linkforge.analytics;

import com.linkforge.analytics.service.BotDetectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bot Detection Unit Tests")
class BotDetectionServiceTest {

    private final BotDetectionService botDetectionService = new BotDetectionService();

    @ParameterizedTest(name = "Bot UA: {0}")
    @ValueSource(strings = {
        "Googlebot/2.1 (+http://www.google.com/bot.html)",
        "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
        "curl/7.68.0",
        "python-requests/2.28.0",
        "Go-http-client/1.1",
        ""
    })
    @DisplayName("Known bot user agents are detected")
    void knownBotUa_isDetected(String ua) {
        BotDetectionService.BotResult result = botDetectionService.analyze(ua, "1.2.3.4", null);
        assertThat(result.isBot()).isTrue();
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    @DisplayName("Real browser user agent is not flagged as bot")
    void realBrowserUa_isNotBot() {
        String chromeUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        BotDetectionService.BotResult result = botDetectionService.analyze(chromeUa, "203.0.113.1", "https://google.com");
        assertThat(result.isBot()).isFalse();
    }

    @Test
    @DisplayName("Null user agent is treated as bot")
    void nullUa_treatedAsBot() {
        BotDetectionService.BotResult result = botDetectionService.analyze(null, "1.2.3.4", null);
        assertThat(result.isBot()).isTrue();
    }
}
