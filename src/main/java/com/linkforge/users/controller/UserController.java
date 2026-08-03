package com.linkforge.users.controller;

import com.linkforge.users.entity.User;
import com.linkforge.users.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User profile, avatar, and account operations")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserRepository userRepository;

    @Value("${application.storage.avatar-dir:./uploads/avatars}")
    private String avatarDir;

    @Value("${application.base-url}")
    private String baseUrl;

    @Operation(summary = "Get current user profile")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "email", user.getEmail(),
            "firstName", user.getFirstName() != null ? user.getFirstName() : "",
            "lastName", user.getLastName() != null ? user.getLastName() : "",
            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
            "role", user.getRole().name(),
            "emailVerified", user.isEmailVerified(),
            "createdAt", user.getCreatedAt()
        ));
    }

    @Operation(summary = "Update profile information")
    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> updates
    ) {
        if (updates.containsKey("firstName")) user.setFirstName(updates.get("firstName"));
        if (updates.containsKey("lastName")) user.setLastName(updates.get("lastName"));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @Operation(summary = "Upload profile avatar")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only image files are allowed"));
        }
        if (file.getSize() > 5 * 1024 * 1024) { // 5MB
            return ResponseEntity.badRequest().body(Map.of("message", "File size must be under 5MB"));
        }

        // Save file
        Path dir = Paths.get(avatarDir);
        Files.createDirectories(dir);
        String fileName = user.getId() + "_" + UUID.randomUUID() + getExtension(file.getOriginalFilename());
        Path filePath = dir.resolve(fileName);
        Files.write(filePath, file.getBytes());

        // Update user
        String avatarUrl = baseUrl + "/uploads/avatars/" + fileName;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }

    @Operation(summary = "Delete account permanently")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user) {
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}
