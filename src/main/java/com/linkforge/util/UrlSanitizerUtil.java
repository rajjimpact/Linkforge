package com.linkforge.util;

import com.linkforge.exception.UrlValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * URL sanitization and validation utility.
 * Enforces security rules before any URL is stored.
 */
@Component
@Slf4j
public class UrlSanitizerUtil {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_SCHEMES = Set.of(
        "javascript", "data", "file", "ftp", "ldap", "gopher", "telnet", "vbscript"
    );

    /**
     * Validates and sanitizes a URL.
     * @throws UrlValidationException if the URL is invalid or unsafe
     */
    public String sanitize(String url) {
        if (url == null || url.isBlank()) {
            throw new UrlValidationException("URL cannot be empty");
        }

        String trimmed = url.trim();

        // Block dangerous schemes (before parsing)
        String lower = trimmed.toLowerCase();
        for (String blocked : BLOCKED_SCHEMES) {
            if (lower.startsWith(blocked + ":")) {
                throw new UrlValidationException("URL scheme '" + blocked + "' is not allowed");
            }
        }

        // Add https:// if no scheme provided
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new UrlValidationException("Invalid URL format: " + e.getMessage());
        }

        // Validate scheme
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new UrlValidationException("Only http and https URLs are allowed");
        }

        // Validate host
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UrlValidationException("URL must have a valid hostname");
        }

        // Block localhost and private IPs in production
        if (isPrivateOrLocalhost(host)) {
            log.warn("Attempted to shorten private/localhost URL: {}", host);
            throw new UrlValidationException("URLs pointing to private or localhost addresses are not allowed");
        }

        // Normalize: remove trailing slash from root URL
        String normalized = trimmed;
        if (uri.getPath() != null && uri.getPath().equals("/") && uri.getQuery() == null) {
            normalized = scheme + "://" + host;
            if (uri.getPort() != -1) {
                normalized += ":" + uri.getPort();
            }
        }

        return normalized;
    }

    private boolean isPrivateOrLocalhost(String host) {
        String lower = host.toLowerCase();
        return lower.equals("localhost") ||
               lower.startsWith("127.") ||
               lower.startsWith("10.") ||
               lower.startsWith("192.168.") ||
               lower.startsWith("172.16.") ||
               lower.equals("::1") ||
               lower.endsWith(".local") ||
               lower.equals("0.0.0.0");
    }
}
