package com.linkforge.security;

import com.linkforge.cache.service.RedisService;
import com.linkforge.users.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT Service — generates, validates, and parses signed JWTs.
 * Uses HMAC-SHA256 (HS256). Access tokens: 15 min. Refresh tokens: stored in DB.
 * Supports token blacklisting via Redis for logout/session revocation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    @Value("${application.jwt.secret}")
    private String jwtSecret;

    @Value("${application.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${application.jwt.issuer}")
    private String issuer;

    private final RedisService redisService;

    // ===== Token Generation =====

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("emailVerified", user.isEmailVerified());
        return buildToken(claims, user.getId().toString(), accessTokenExpiryMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiryMs) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer(issuer)
                .id(jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ===== Token Validation =====

    public boolean isTokenValid(String token, User user) {
        try {
            String subject = extractSubject(token);
            String jti = extractJti(token);

            if (redisService.isTokenBlacklisted(jti)) {
                log.debug("JWT {} is blacklisted", jti);
                return false;
            }

            return subject.equals(user.getId().toString()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ===== Token Blacklisting (Logout) =====

    public void blacklistToken(String token) {
        try {
            String jti = extractJti(token);
            long expiry = extractExpiration(token).getTime() - System.currentTimeMillis();
            if (expiry > 0) {
                redisService.blacklistToken(jti, expiry);
            }
        } catch (JwtException e) {
            log.warn("Could not blacklist token: {}", e.getMessage());
        }
    }

    // ===== Claim Extraction =====

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
