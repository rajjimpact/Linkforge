package com.linkforge.notification.service;

import com.linkforge.users.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Email service — sends all platform emails using Thymeleaf HTML templates.
 * All sends are async (emailExecutor) to never block the request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${application.base-url}")
    private String baseUrl;

    @Value("${application.frontend-url}")
    private String frontendUrl;

    @Async("emailExecutor")
    public void sendVerificationEmail(User user, String token) {
        Context ctx = new Context();
        ctx.setVariables(Map.of(
            "firstName", user.getFirstName(),
            "verifyUrl", baseUrl + "/api/v1/auth/verify-email?token=" + token,
            "expiryHours", 24,
            "appName", "LinkForge"
        ));
        sendEmail(user.getEmail(), "Verify your LinkForge email", "email/verify-email", ctx);
    }

    @Async("emailExecutor")
    public void sendPasswordResetEmail(User user, String token) {
        Context ctx = new Context();
        ctx.setVariables(Map.of(
            "firstName", user.getFirstName(),
            "resetUrl", frontendUrl + "/reset-password?token=" + token,
            "expiryMinutes", 60,
            "appName", "LinkForge"
        ));
        sendEmail(user.getEmail(), "Reset your LinkForge password", "email/reset-password", ctx);
    }

    @Async("emailExecutor")
    public void sendLinkExpiringEmail(User user, String shortCode, LocalDateTime expiresAt) {
        Context ctx = new Context();
        ctx.setVariables(Map.of(
            "firstName", user.getFirstName(),
            "shortCode", shortCode,
            "shortUrl", baseUrl + "/" + shortCode,
            "expiresAt", expiresAt.toString(),
            "appName", "LinkForge"
        ));
        sendEmail(user.getEmail(), "Your LinkForge link is expiring soon", "email/link-expiring", ctx);
    }

    @Async("emailExecutor")
    public void sendWelcomeEmail(User user) {
        Context ctx = new Context();
        ctx.setVariables(Map.of(
            "firstName", user.getFirstName(),
            "dashboardUrl", frontendUrl + "/dashboard",
            "appName", "LinkForge"
        ));
        sendEmail(user.getEmail(), "Welcome to LinkForge!", "email/welcome", ctx);
    }

    @Async("emailExecutor")
    public void sendWeeklyAnalyticsEmail(User user, Map<String, Object> stats) {
        Context ctx = new Context();
        ctx.setVariable("firstName", user.getFirstName());
        ctx.setVariable("appName", "LinkForge");
        ctx.setVariable("dashboardUrl", frontendUrl + "/analytics");
        ctx.setVariables(stats);
        sendEmail(user.getEmail(), "Your LinkForge Weekly Analytics", "email/weekly-analytics", ctx);
    }

    private void sendEmail(String to, String subject, String template, Context ctx) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "LinkForge");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(templateEngine.process(template, ctx), true);
            mailSender.send(message);
            log.info("Email sent: {} to {}", subject, maskEmail(to));
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", maskEmail(to), e.getMessage());
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int atIndex = email.indexOf('@');
        return email.substring(0, Math.min(3, atIndex)) + "***" + email.substring(atIndex);
    }
}
