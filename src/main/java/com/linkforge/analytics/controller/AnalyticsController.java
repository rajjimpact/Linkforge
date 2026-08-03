package com.linkforge.analytics.controller;

import com.linkforge.analytics.service.AnalyticsService;
import com.linkforge.users.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Click analytics, device stats, geographic breakdown, and dashboard")
@SecurityRequirement(name = "Bearer Authentication")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Get analytics summary for a URL")
    @GetMapping("/{urlId}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable UUID urlId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(analyticsService.getSummary(urlId, user.getId()));
    }

    @Operation(summary = "Get daily clicks for a URL")
    @GetMapping("/{urlId}/clicks")
    public ResponseEntity<List<Map<String, Object>>> getDailyClicks(
            @PathVariable UUID urlId,
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(analyticsService.getDailyClicks(urlId, days));
    }

    @Operation(summary = "Get clicks by country")
    @GetMapping("/{urlId}/countries")
    public ResponseEntity<List<Map<String, Object>>> getCountries(@PathVariable UUID urlId) {
        return ResponseEntity.ok(analyticsService.getClicksByCountry(urlId));
    }

    @Operation(summary = "Get clicks by device type")
    @GetMapping("/{urlId}/devices")
    public ResponseEntity<List<Map<String, Object>>> getDevices(@PathVariable UUID urlId) {
        return ResponseEntity.ok(analyticsService.getClicksByDevice(urlId));
    }

    @Operation(summary = "Get clicks by browser")
    @GetMapping("/{urlId}/browsers")
    public ResponseEntity<List<Map<String, Object>>> getBrowsers(@PathVariable UUID urlId) {
        return ResponseEntity.ok(analyticsService.getClicksByBrowser(urlId));
    }

    @Operation(summary = "Get top referrers")
    @GetMapping("/{urlId}/referrers")
    public ResponseEntity<List<Map<String, Object>>> getReferrers(@PathVariable UUID urlId) {
        return ResponseEntity.ok(analyticsService.getTopReferrers(urlId));
    }

    @Operation(summary = "Get global dashboard analytics for authenticated user")
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(analyticsService.getDashboard(user.getId(), days));
    }
}
