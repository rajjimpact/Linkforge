package com.linkforge.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Structured audit logger for security events.
 * Uses SLF4J MDC for correlation IDs.
 * NEVER logs passwords, JWT tokens, or sensitive PII.
 */
@Component
@Slf4j
public class AuditLogger {

    private static final org.slf4j.Logger AUDIT_LOG =
            org.slf4j.LoggerFactory.getLogger("AUDIT");

    public void logLogin(String userId, String email, String ip, boolean success) {
        String event = success ? "LOGIN_SUCCESS" : "LOGIN_FAILED";
        AUDIT_LOG.info("[{}] userId={} email={} ip={}", event, userId, maskEmail(email), ip);
    }

    public void logRegistration(String userId, String email, String ip) {
        AUDIT_LOG.info("[REGISTER] userId={} email={} ip={}", userId, maskEmail(email), ip);
    }

    public void logLogout(String userId, String ip) {
        AUDIT_LOG.info("[LOGOUT] userId={} ip={}", userId, ip);
    }

    public void logPasswordChange(String userId, String ip) {
        AUDIT_LOG.info("[PASSWORD_CHANGE] userId={} ip={}", userId, ip);
    }

    public void logPasswordReset(String email, String ip) {
        AUDIT_LOG.info("[PASSWORD_RESET] email={} ip={}", maskEmail(email), ip);
    }

    public void logUrlCreated(String userId, String shortCode, String ip) {
        AUDIT_LOG.info("[URL_CREATED] userId={} shortCode={} ip={}", userId, shortCode, ip);
    }

    public void logUrlDeleted(String userId, String shortCode, String ip) {
        AUDIT_LOG.info("[URL_DELETED] userId={} shortCode={} ip={}", userId, shortCode, ip);
    }

    public void logSuspiciousActivity(String type, String detail, String ip) {
        AUDIT_LOG.warn("[SUSPICIOUS:{}] detail={} ip={}", type, detail, ip);
    }

    public void logAdminAction(String adminId, String action, String targetId) {
        AUDIT_LOG.info("[ADMIN:{}] adminId={} targetId={}", action, adminId, targetId);
    }

    public void logAccountLocked(String userId, String ip) {
        AUDIT_LOG.warn("[ACCOUNT_LOCKED] userId={} ip={}", userId, ip);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int atIndex = email.indexOf('@');
        int visibleChars = Math.min(3, atIndex);
        return email.substring(0, visibleChars) + "***" + email.substring(atIndex);
    }

    public static String correlationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
