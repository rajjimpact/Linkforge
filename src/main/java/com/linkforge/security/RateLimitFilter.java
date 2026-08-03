package com.linkforge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.cache.service.RedisService;
import com.linkforge.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Redis-backed rate limiting filter.
 * Applies per-IP rate limits using a sliding window counter.
 * Different limits for redirect endpoint vs. API endpoints.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @Value("${application.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${application.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${application.rate-limit.redirect-requests-per-minute:300}")
    private int redirectRequestsPerMinute;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String path = request.getServletPath();

        // Determine which limit to apply
        int limit = isRedirectPath(path) ? redirectRequestsPerMinute : requestsPerMinute;
        String rateLimitKey = "ratelimit:ip:" + clientIp + ":" + (isRedirectPath(path) ? "redirect" : "api");

        long currentCount = redisService.incrementRateLimit(rateLimitKey, 60); // 60-second window

        // Set rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));

        if (currentCount > limit) {
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");

            ErrorResponse error = ErrorResponse.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error("Too Many Requests")
                    .message("Rate limit exceeded. Maximum " + limit + " requests per minute.")
                    .path(path)
                    .timestamp(LocalDateTime.now())
                    .build();

            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private boolean isRedirectPath(String path) {
        // Short redirect URLs don't start with /api/
        return !path.startsWith("/api/") && !path.startsWith("/actuator/")
               && !path.startsWith("/swagger-ui") && !path.startsWith("/api-docs");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/health") || path.startsWith("/swagger-ui");
    }
}
