package com.linkforge.urls.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UrlResponse {
    private UUID id;
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private String title;
    private boolean isActive;
    private boolean isPrivate;
    private boolean isOneTime;
    private boolean hasPassword;
    private boolean hasQrCode;
    private String qrCodeUrl;
    private long clickCount;
    private long uniqueClickCount;
    private boolean isSafe;
    private String healthStatus;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledEnd;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
