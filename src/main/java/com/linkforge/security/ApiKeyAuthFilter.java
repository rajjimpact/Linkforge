package com.linkforge.security;

import com.linkforge.cache.service.RedisService;
import com.linkforge.users.entity.ApiKey;
import com.linkforge.users.entity.User;
import com.linkforge.users.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * API Key Authentication Filter.
 * Handles Bearer API_KEY authentication for developer endpoints.
 * Keys are SHA-256 hashed before lookup to match stored hash.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final RedisService redisService;

    @Value("${application.rate-limit.api-key-requests-per-minute:100}")
    private int apiKeyRateLimit;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Only process API key auth for the public shorten endpoint
        if (!request.getServletPath().equals("/api/v1/shorten")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = authHeader.substring(7);

        // Don't process JWT tokens here (they start with eyJ)
        if (rawKey.startsWith("eyJ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String keyHash = sha256(rawKey);
        ApiKey apiKey = apiKeyRepository.findByKeyHash(keyHash).orElse(null);

        if (apiKey == null || !apiKey.isValid()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid or expired API key\"}");
            return;
        }

        // Rate limit check for this API key
        long count = redisService.incrementApiKeyRateLimit(apiKey.getId().toString(), 60);
        int limit = apiKey.getRateLimitPerMinute() != null ? apiKey.getRateLimitPerMinute() : apiKeyRateLimit;
        if (count > limit) {
            response.setStatus(429);
            response.getWriter().write("{\"error\":\"API key rate limit exceeded\"}");
            return;
        }

        // Set authenticated user context
        User user = apiKey.getUser();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Record usage async-ish
        apiKeyRepository.recordUsage(apiKey.getId(), LocalDateTime.now());

        filterChain.doFilter(request, response);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().equals("/api/v1/shorten");
    }
}
