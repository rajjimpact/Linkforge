package com.linkforge.admin.controller;

import com.linkforge.urls.dto.UrlResponse;
import com.linkforge.urls.repository.ShortUrlRepository;
import com.linkforge.urls.service.UrlService;
import com.linkforge.users.entity.User;
import com.linkforge.users.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Panel", description = "Administrative operations — requires ADMIN role")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final UserRepository userRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final UrlService urlService;

    // ===== Users =====

    @Operation(summary = "List all users (paginated, searchable)")
    @GetMapping("/users")
    public ResponseEntity<Page<User>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(userRepository.searchUsers(search, pageable));
    }

    @Operation(summary = "Get user by ID")
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Disable/enable a user account")
    @PutMapping("/users/{id}/disable")
    public ResponseEntity<Map<String, String>> disableUser(
            @PathVariable UUID id,
            @RequestParam boolean disabled,
            @AuthenticationPrincipal User admin
    ) {
        userRepository.findById(id).ifPresent(user -> {
            user.setEnabled(!disabled);
            userRepository.save(user);
        });
        return ResponseEntity.ok(Map.of("message", disabled ? "User disabled" : "User enabled"));
    }

    @Operation(summary = "Delete a user account")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ===== URLs =====

    @Operation(summary = "List all URLs across all users")
    @GetMapping("/urls")
    public ResponseEntity<Page<UrlResponse>> listAllUrls(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(
            shortUrlRepository.adminSearchAll(search, pageable).map(urlService::toResponse)
        );
    }

    @Operation(summary = "Delete a URL by ID (admin override)")
    @DeleteMapping("/urls/{id}")
    public ResponseEntity<Void> deleteUrl(@PathVariable UUID id) {
        shortUrlRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ===== Stats =====

    @Operation(summary = "Platform statistics")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalUsers = userRepository.count();
        long totalUrls = shortUrlRepository.count();
        long adminUsers = userRepository.countByRole(User.Role.ADMIN);

        return ResponseEntity.ok(Map.of(
            "totalUsers", totalUsers,
            "totalUrls", totalUrls,
            "adminUsers", adminUsers,
            "regularUsers", totalUsers - adminUsers
        ));
    }
}
