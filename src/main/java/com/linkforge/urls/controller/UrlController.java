package com.linkforge.urls.controller;

import com.linkforge.urls.dto.CreateUrlRequest;
import com.linkforge.urls.dto.UrlResponse;
import com.linkforge.urls.service.UrlService;
import com.linkforge.users.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
@Tag(name = "URL Management", description = "Create, manage, and track shortened URLs")
@SecurityRequirement(name = "Bearer Authentication")
public class UrlController {

    private final UrlService urlService;

    @Operation(summary = "Create a short URL")
    @PostMapping
    public ResponseEntity<UrlResponse> create(
            @Valid @RequestBody CreateUrlRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(urlService.create(request, user, extractIp(httpRequest)));
    }

    @Operation(summary = "List all URLs for the authenticated user (paginated, searchable)")
    @GetMapping
    public ResponseEntity<Page<UrlResponse>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean hasExpiry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);
        return ResponseEntity.ok(urlService.listByUser(user.getId(), search, isActive, hasExpiry, pageable));
    }

    @Operation(summary = "Get URL by ID")
    @GetMapping("/{id}")
    public ResponseEntity<UrlResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(urlService.getById(id, user));
    }

    @Operation(summary = "Update a URL")
    @PutMapping("/{id}")
    public ResponseEntity<UrlResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateUrlRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(urlService.update(id, request, user));
    }

    @Operation(summary = "Delete a URL")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest
    ) {
        urlService.delete(id, user, extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Toggle URL active/inactive status")
    @PutMapping("/{id}/toggle")
    public ResponseEntity<UrlResponse> toggle(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(urlService.toggle(id, user));
    }

    @Operation(summary = "Bulk create short URLs (max 100 per request)")
    @PostMapping("/bulk")
    public ResponseEntity<List<UrlResponse>> bulkCreate(
            @RequestBody List<@Valid CreateUrlRequest> requests,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(urlService.bulkCreate(requests, user, extractIp(httpRequest)));
    }

    @Operation(summary = "Bulk delete URLs by ID list")
    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Integer>> bulkDelete(
            @RequestBody List<UUID> ids,
            @AuthenticationPrincipal User user
    ) {
        urlService.bulkDelete(ids, user);
        return ResponseEntity.ok(Map.of("deleted", ids.size()));
    }

    private String extractIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        return (xForwardedFor != null) ? xForwardedFor.split(",")[0].trim() : request.getRemoteAddr();
    }
}
