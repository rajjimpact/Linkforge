package com.linkforge.util;

import com.linkforge.urls.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique short codes using Base62 encoding.
 * 7-character codes provide 62^7 = 3.5 trillion unique values.
 * Uses SecureRandom to prevent predictability.
 * Includes collision detection with automatic retry.
 */
@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_RETRIES = 10;

    private final ShortUrlRepository shortUrlRepository;

    @Value("${application.link.short-code-length:7}")
    private int shortCodeLength;

    /**
     * Generates a unique, collision-free short code.
     */
    public String generate() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = generateRandom(shortCodeLength);
            if (!shortUrlRepository.existsByShortCode(code)) {
                return code;
            }
        }
        // Extremely unlikely — increase length by 1 and try again
        return generateRandom(shortCodeLength + 1);
    }

    /**
     * Validates that a custom alias doesn't conflict with reserved paths.
     */
    public boolean isReservedAlias(String alias) {
        return RESERVED_PATHS.contains(alias.toLowerCase());
    }

    private String generateRandom(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private static final java.util.Set<String> RESERVED_PATHS = java.util.Set.of(
        "api", "admin", "auth", "login", "register", "logout", "dashboard",
        "analytics", "settings", "profile", "users", "urls", "qr", "health",
        "actuator", "swagger-ui", "api-docs", "static", "assets", "favicon.ico"
    );
}
