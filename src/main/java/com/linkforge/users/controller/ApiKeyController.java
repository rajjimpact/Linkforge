package com.linkforge.users.controller;

import com.linkforge.users.entity.ApiKey;
import com.linkforge.users.entity.User;
import com.linkforge.users.repository.ApiKeyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Manage developer API keys for programmatic access")
@SecurityRequirement(name = "Bearer Authentication")
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;

    @Operation(summary = "Generate a new API key (shown only once)")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createApiKey(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body
    ) {
        String name = body.getOrDefault("name", "My API Key");

        // Generate raw key
        String rawKey = "lf_" + generateSecureRandom(32);
        String keyPrefix = rawKey.substring(0, 12);
        String keyHash = sha256(rawKey);

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(name)
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .enabled(true)
                .build();

        apiKeyRepository.save(apiKey);

        // CRITICAL: Return raw key ONLY once — never stored in plain text
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "id", apiKey.getId(),
            "name", apiKey.getName(),
            "key", rawKey, // Shown only here
            "prefix", keyPrefix,
            "createdAt", apiKey.getCreatedAt(),
            "warning", "Save this key now — it will not be shown again!"
        ));
    }

    @Operation(summary = "List all API keys (keys are masked)")
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listApiKeys(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> keys = apiKeyRepository.findByUserId(user.getId())
                .stream()
                .map(k -> Map.<String, Object>of(
                    "id", k.getId(),
                    "name", k.getName(),
                    "prefix", k.getKeyPrefix() + "...",
                    "enabled", k.isEnabled(),
                    "lastUsedAt", k.getLastUsedAt() != null ? k.getLastUsedAt() : "Never",
                    "totalRequests", k.getTotalRequests(),
                    "createdAt", k.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(keys);
    }

    @Operation(summary = "Revoke an API key")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeApiKey(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        apiKeyRepository.findById(id).ifPresent(key -> {
            if (key.getUser().getId().equals(user.getId())) {
                key.setEnabled(false);
                apiKeyRepository.save(key);
            }
        });
        return ResponseEntity.noContent().build();
    }

    private String generateSecureRandom(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
