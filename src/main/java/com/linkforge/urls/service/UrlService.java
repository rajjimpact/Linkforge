package com.linkforge.urls.service;

import com.linkforge.cache.service.RedisService;
import com.linkforge.exception.DuplicateAliasException;
import com.linkforge.exception.LinkForgeException;
import com.linkforge.exception.ResourceNotFoundException;
import com.linkforge.urls.dto.CreateUrlRequest;
import com.linkforge.urls.dto.UrlResponse;
import com.linkforge.urls.entity.ShortUrl;
import com.linkforge.urls.repository.ShortUrlRepository;
import com.linkforge.users.entity.User;
import com.linkforge.util.AuditLogger;
import com.linkforge.util.SafeBrowsingService;
import com.linkforge.util.ShortCodeGenerator;
import com.linkforge.util.UrlSanitizerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlSanitizerUtil urlSanitizerUtil;
    private final SafeBrowsingService safeBrowsingService;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    @Value("${application.base-url}")
    private String baseUrl;

    @Value("${application.link.max-bulk-create:100}")
    private int maxBulkCreate;

    // ===== Create =====

    public UrlResponse create(CreateUrlRequest request, User user, String ip) {
        // Validate and sanitize URL
        String sanitizedUrl = urlSanitizerUtil.sanitize(request.getOriginalUrl());

        // Handle custom alias
        String shortCode;
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            String alias = request.getCustomAlias().toLowerCase().trim();
            if (shortCodeGenerator.isReservedAlias(alias)) {
                throw new LinkForgeException("This alias is reserved and cannot be used.", HttpStatus.BAD_REQUEST);
            }
            if (shortUrlRepository.existsByShortCode(alias)) {
                throw new DuplicateAliasException(alias);
            }
            shortCode = alias;
        } else {
            shortCode = shortCodeGenerator.generate();
        }

        // Handle expiry presets
        LocalDateTime expiresAt = resolveExpiry(request);

        // Build entity
        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(sanitizedUrl)
                .title(request.getTitle())
                .user(user)
                .isPrivate(request.isPrivate())
                .isOneTime(request.isOneTime())
                .isActive(true)
                .expiresAt(expiresAt)
                .scheduledStart(request.getScheduledStart())
                .scheduledEnd(request.getScheduledEnd())
                .isSafe(true) // optimistic — Safe Browsing runs async
                .build();

        if (request.getLinkPassword() != null && !request.getLinkPassword().isBlank()) {
            shortUrl.setPasswordHash(passwordEncoder.encode(request.getLinkPassword()));
        }

        shortUrl = shortUrlRepository.save(shortUrl);

        // Async Safe Browsing check
        final ShortUrl saved = shortUrl;
        safeBrowsingService.isSafe(sanitizedUrl).thenAccept(safe -> {
            if (!safe) {
                saved.setSafe(false);
                saved.setSafeBrowsingCheckedAt(LocalDateTime.now());
                shortUrlRepository.save(saved);
                redisService.invalidateUrl(shortCode);
                log.warn("URL flagged as unsafe by Safe Browsing: {}", shortCode);
            } else {
                saved.setSafeBrowsingCheckedAt(LocalDateTime.now());
                shortUrlRepository.save(saved);
            }
        });

        // Cache immediately for fast redirect
        redisService.cacheUrl(shortCode, sanitizedUrl, 86400L);

        auditLogger.logUrlCreated(user.getId().toString(), shortCode, ip);
        log.info("URL created: {} → {}", shortCode, sanitizedUrl);

        return toResponse(shortUrl);
    }

    // ===== Read =====

    @Transactional(readOnly = true)
    public UrlResponse getById(UUID id, User user) {
        ShortUrl url = findAndAuthorize(id, user);
        return toResponse(url);
    }

    @Transactional(readOnly = true)
    public Page<UrlResponse> listByUser(UUID userId, String search, Boolean isActive, Boolean hasExpiry, Pageable pageable) {
        return shortUrlRepository.searchByUser(userId, search, isActive, hasExpiry, pageable)
                .map(this::toResponse);
    }

    // ===== Update =====

    public UrlResponse update(UUID id, CreateUrlRequest request, User user) {
        ShortUrl url = findAndAuthorize(id, user);

        if (request.getOriginalUrl() != null && !request.getOriginalUrl().isBlank()) {
            String sanitized = urlSanitizerUtil.sanitize(request.getOriginalUrl());
            url.setOriginalUrl(sanitized);
            redisService.invalidateUrl(url.getShortCode()); // invalidate stale cache
            redisService.cacheUrl(url.getShortCode(), sanitized, 86400L);
        }

        if (request.getTitle() != null) url.setTitle(request.getTitle());
        if (request.getScheduledStart() != null) url.setScheduledStart(request.getScheduledStart());
        if (request.getScheduledEnd() != null) url.setScheduledEnd(request.getScheduledEnd());

        LocalDateTime newExpiry = resolveExpiry(request);
        if (newExpiry != null) url.setExpiresAt(newExpiry);

        return toResponse(shortUrlRepository.save(url));
    }

    // ===== Delete =====

    public void delete(UUID id, User user, String ip) {
        ShortUrl url = findAndAuthorize(id, user);
        redisService.invalidateUrl(url.getShortCode());
        shortUrlRepository.delete(url);
        auditLogger.logUrlDeleted(user.getId().toString(), url.getShortCode(), ip);
    }

    public void bulkDelete(List<UUID> ids, User user) {
        ids.forEach(id -> {
            shortUrlRepository.findById(id).ifPresent(url -> {
                if (url.getUser().getId().equals(user.getId())) {
                    redisService.invalidateUrl(url.getShortCode());
                    shortUrlRepository.delete(url);
                }
            });
        });
    }

    public List<UrlResponse> bulkCreate(List<CreateUrlRequest> requests, User user, String ip) {
        if (requests.size() > maxBulkCreate) {
            throw new LinkForgeException("Bulk create limit is " + maxBulkCreate + " URLs per request.", HttpStatus.BAD_REQUEST);
        }
        return requests.stream()
                .map(req -> create(req, user, ip))
                .collect(Collectors.toList());
    }

    // ===== Toggle =====

    public UrlResponse toggle(UUID id, User user) {
        ShortUrl url = findAndAuthorize(id, user);
        url.setActive(!url.isActive());
        if (!url.isActive()) {
            redisService.invalidateUrl(url.getShortCode());
        } else {
            redisService.cacheUrl(url.getShortCode(), url.getOriginalUrl(), 86400L);
        }
        return toResponse(shortUrlRepository.save(url));
    }

    // ===== Redirect resolution (called by RedirectController) =====

    @Transactional
    public String resolveForRedirect(String shortCode) {
        // 1. Try Redis cache (sub-millisecond)
        return redisService.getCachedUrl(shortCode).orElseGet(() -> {
            // 2. Cache miss — hit DB
            ShortUrl url = shortUrlRepository.findByShortCode(shortCode)
                    .orElse(null);

            if (url == null || !url.isAccessible()) {
                return null;
            }

            // Repopulate cache
            redisService.cacheUrl(shortCode, url.getOriginalUrl(), 86400L);
            return url.getOriginalUrl();
        });
    }

    @Transactional(readOnly = true)
    public ShortUrl findByShortCode(String shortCode) {
        return shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortCode));
    }

    // ===== Private Helpers =====

    private ShortUrl findAndAuthorize(UUID id, User user) {
        ShortUrl url = shortUrlRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("URL", id.toString()));

        // Admins can access any URL
        if (user.getRole() != User.Role.ADMIN && !url.getUser().getId().equals(user.getId())) {
            throw new LinkForgeException("You don't have permission to access this URL", HttpStatus.FORBIDDEN);
        }
        return url;
    }

    private LocalDateTime resolveExpiry(CreateUrlRequest request) {
        if (request.getExpiresAt() != null) return request.getExpiresAt();
        if (request.getExpiryPreset() != null) {
            return switch (request.getExpiryPreset().toLowerCase()) {
                case "1h"    -> LocalDateTime.now().plusHours(1);
                case "1d"    -> LocalDateTime.now().plusDays(1);
                case "1w"    -> LocalDateTime.now().plusWeeks(1);
                case "1m"    -> LocalDateTime.now().plusMonths(1);
                case "never" -> null;
                default -> null;
            };
        }
        return null; // never expire
    }

    public UrlResponse toResponse(ShortUrl url) {
        return UrlResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .title(url.getTitle())
                .isActive(url.isActive())
                .isPrivate(url.isPrivate())
                .isOneTime(url.isOneTime())
                .hasPassword(url.getPasswordHash() != null)
                .hasQrCode(url.isHasQrCode())
                .qrCodeUrl(url.isHasQrCode() ? baseUrl + "/api/v1/urls/" + url.getId() + "/qr" : null)
                .clickCount(url.getClickCount())
                .uniqueClickCount(url.getUniqueClickCount())
                .isSafe(url.isSafe())
                .healthStatus(url.getHealthStatus() != null ? url.getHealthStatus().name() : "UNKNOWN")
                .expiresAt(url.getExpiresAt())
                .scheduledStart(url.getScheduledStart())
                .scheduledEnd(url.getScheduledEnd())
                .createdAt(url.getCreatedAt())
                .updatedAt(url.getUpdatedAt())
                .build();
    }
}
