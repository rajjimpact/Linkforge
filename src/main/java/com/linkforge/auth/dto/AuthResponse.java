package com.linkforge.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn; // seconds
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private boolean emailVerified;
}
