package com.linkforge.urls.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateUrlRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String originalUrl;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(min = 3, max = 50, message = "Custom alias must be 3-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Custom alias can only contain letters, numbers, hyphens, and underscores")
    private String customAlias;

    /** Password for protected links (will be BCrypt hashed before storage). */
    @Size(min = 4, max = 100, message = "Link password must be 4-100 characters")
    private String linkPassword;

    private boolean isPrivate = false;
    private boolean isOneTime = false;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledEnd;

    /** Shorthand expiry presets: 1h, 1d, 1w, 1m */
    private String expiryPreset;
}
