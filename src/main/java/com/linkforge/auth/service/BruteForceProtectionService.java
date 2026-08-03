package com.linkforge.auth.service;

import com.linkforge.users.repository.UserRepository;
import com.linkforge.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Brute force login protection service.
 * Tracks failed login attempts and locks accounts after threshold.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BruteForceProtectionService {

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    @Value("${application.security.max-login-attempts:5}")
    private int maxAttempts;

    @Value("${application.security.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    @Transactional
    public void recordFailedAttempt(String email, String ip) {
        userRepository.findByEmail(email.toLowerCase()).ifPresent(user -> {
            userRepository.incrementLoginAttempts(user.getId());
            int newAttempts = user.getLoginAttempts() + 1;

            if (newAttempts >= maxAttempts) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockoutDurationMinutes);
                userRepository.lockUser(user.getId(), lockUntil);
                auditLogger.logAccountLocked(user.getId().toString(), ip);
                log.warn("Account locked for user: {} after {} failed attempts", user.getId(), newAttempts);
            }
        });
    }

    @Transactional
    public void resetFailedAttempts(UUID userId) {
        userRepository.resetLoginAttempts(userId);
    }

    @Transactional(readOnly = true)
    public void checkLockout(String email) {
        userRepository.findByEmail(email.toLowerCase()).ifPresent(user -> {
            if (!user.isAccountNonLocked()) {
                throw new LockedException("Account is temporarily locked");
            }
        });
    }
}
