package com.linkforge.auth.service;

import com.linkforge.auth.dto.*;
import com.linkforge.auth.entity.*;
import com.linkforge.auth.repository.*;
import com.linkforge.exception.LinkForgeException;
import com.linkforge.exception.ResourceNotFoundException;
import com.linkforge.notification.service.EmailService;
import com.linkforge.security.JwtService;
import com.linkforge.users.entity.User;
import com.linkforge.users.repository.UserRepository;
import com.linkforge.util.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final AuditLogger auditLogger;
    private final BruteForceProtectionService bruteForceService;

    @Value("${application.jwt.access-token-expiry-ms:900000}")
    private long accessTokenExpiryMs;

    @Value("${application.jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    @Value("${application.security.password-reset-token-expiry-minutes:60}")
    private int passwordResetExpiryMinutes;

    @Value("${application.security.email-verify-token-expiry-hours:24}")
    private int emailVerifyExpiryHours;

    // ===== Registration =====

    public AuthResponse register(RegisterRequest request, String ip) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new LinkForgeException("Email address is already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .role(User.Role.USER)
                .emailVerified(false)
                .enabled(true)
                .build();

        user = userRepository.save(user);

        // Send verification email async
        sendEmailVerification(user);

        auditLogger.logRegistration(user.getId().toString(), user.getEmail(), ip);
        log.info("New user registered: {}", user.getId());

        return buildAuthResponse(user, null); // no refresh token on registration — require email verify first
    }

    // ===== Login =====

    public AuthResponse login(LoginRequest request, String ip, String deviceInfo) {
        String email = request.getEmail().toLowerCase().trim();

        // Check brute force lockout before attempting auth
        bruteForceService.checkLockout(email);

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );

            User user = (User) auth.getPrincipal();

            // Require email verification
            if (!user.isEmailVerified()) {
                throw new LinkForgeException(
                    "Email not verified. Please check your inbox and verify your email before logging in.",
                    HttpStatus.FORBIDDEN
                );
            }

            // Reset failed attempts on successful login
            bruteForceService.resetFailedAttempts(user.getId());
            userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

            // Revoke all existing refresh tokens (single session policy)
            refreshTokenRepository.revokeAllByUserId(user.getId(), LocalDateTime.now());

            // Create new refresh token
            RefreshToken refreshToken = createRefreshToken(user, deviceInfo, ip);

            auditLogger.logLogin(user.getId().toString(), email, ip, true);
            return buildAuthResponse(user, refreshToken.getToken());

        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            bruteForceService.recordFailedAttempt(email, ip);
            auditLogger.logLogin(null, email, ip, false);
            throw new LinkForgeException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        } catch (LockedException e) {
            auditLogger.logSuspiciousActivity("LOCKED_ACCOUNT_LOGIN", email, ip);
            throw new LinkForgeException("Account is temporarily locked due to multiple failed login attempts. Please try again later.", HttpStatus.LOCKED);
        }
    }

    // ===== Refresh Token =====

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new LinkForgeException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (!stored.isValid()) {
            // Possible token reuse attack — revoke entire family
            if (stored.isRevoked()) {
                log.warn("Refresh token reuse detected for user: {}", stored.getUser().getId());
                refreshTokenRepository.revokeAllByUserId(stored.getUser().getId(), LocalDateTime.now());
            }
            throw new LinkForgeException("Refresh token is expired or revoked. Please log in again.", HttpStatus.UNAUTHORIZED);
        }

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        RefreshToken newToken = createRefreshToken(stored.getUser(), stored.getDeviceInfo(), stored.getIpAddress());
        newToken.setParentTokenId(stored.getId());
        refreshTokenRepository.save(newToken);

        return buildAuthResponse(stored.getUser(), newToken.getToken());
    }

    // ===== Logout =====

    public void logout(String accessToken, String userId) {
        jwtService.blacklistToken(accessToken);
        refreshTokenRepository.revokeAllByUserId(UUID.fromString(userId), LocalDateTime.now());
        auditLogger.logLogout(userId, "");
        log.info("User logged out: {}", userId);
    }

    // ===== Email Verification =====

    public void sendEmailVerification(User user) {
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verifyToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(emailVerifyExpiryHours))
                .build();
        emailVerificationTokenRepository.save(verifyToken);
        emailService.sendVerificationEmail(user, token);
    }

    public void verifyEmail(String token) {
        EmailVerificationToken verifyToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new LinkForgeException("Invalid or expired verification token", HttpStatus.BAD_REQUEST));

        if (!verifyToken.isValid()) {
            throw new LinkForgeException("Verification token has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
        }

        verifyToken.setVerified(true);
        verifyToken.setVerifiedAt(LocalDateTime.now());
        emailVerificationTokenRepository.save(verifyToken);
        userRepository.markEmailVerified(verifyToken.getUser().getId());
        log.info("Email verified for user: {}", verifyToken.getUser().getId());
    }

    // ===== Password Reset =====

    public void forgotPassword(String email, String ip) {
        // Always return success to prevent email enumeration
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes))
                    .build();
            passwordResetTokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(user, token);
            auditLogger.logPasswordReset(email, ip);
        });
    }

    public void resetPassword(PasswordDtos.ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new LinkForgeException("Invalid or expired reset token", HttpStatus.BAD_REQUEST));

        if (!resetToken.isValid()) {
            throw new LinkForgeException("Password reset token has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        // Revoke all active sessions
        refreshTokenRepository.revokeAllByUserId(user.getId(), LocalDateTime.now());
        log.info("Password reset for user: {}", user.getId());
    }

    public void changePassword(PasswordDtos.ChangePasswordRequest request, User user, String ip) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditLogger.logSuspiciousActivity("WRONG_CURRENT_PASSWORD", user.getId().toString(), ip);
            throw new LinkForgeException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId(), LocalDateTime.now());
        auditLogger.logPasswordChange(user.getId().toString(), ip);
    }

    // ===== Private Helpers =====

    private RefreshToken createRefreshToken(User user, String deviceInfo, String ip) {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiryMs / 1000))
                .deviceInfo(deviceInfo)
                .ipAddress(ip)
                .build();
        return refreshTokenRepository.save(token);
    }

    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(user);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}
