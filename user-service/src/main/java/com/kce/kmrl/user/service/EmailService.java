package com.kce.kmrl.user.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Retry(name = "emailService")
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendResetPasswordEmailFallback")
    public void sendResetPasswordEmail(String toEmail, String resetUrl) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("Password Reset Request - MetroMind KMRL");

        String htmlContent = """
            <div style="font-family: Arial, sans-serif; padding: 24px; color: #0F172A; max-width: 500px; border: 1px solid #E2E8F0; border-radius: 12px;">
                <h2 style="color: #009688; margin-bottom: 8px;">MetroMind KMRL</h2>
                <p style="font-size: 15px; color: #334155;">You requested a password reset for your account.</p>
                <p style="font-size: 14px; color: #64748B;">Please click the button below to set a new password. This link is valid for <strong>15 minutes</strong>.</p>
                <div style="margin: 24px 0;">
                    <a href="%s" style="background-color: #009688; color: white; padding: 12px 20px; text-decoration: none; border-radius: 8px; font-weight: bold; display: inline-block;">
                        Reset Password
                    </a>
                </div>
                <p style="font-size: 12px; color: #94A3B8;">If you did not request this, please ignore this email.</p>
            </div>
            """.formatted(resetUrl);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private void sendResetPasswordEmailFallback(String toEmail, String resetUrl, Throwable t) {
        log.error("Reset-password email to {} could not be sent (mail service unavailable/circuit open): {}: {}",
                toEmail, t.getClass().getName(), t.getMessage(), t);
    }

    @Retry(name = "emailService")
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendRegistrationApprovedEmailFallback")
    public void sendRegistrationApprovedEmail(String toEmail, String username, String loginUrl) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("Your MetroMind KMRL Registration Has Been Approved");

        String htmlContent = """
            <div style="font-family: Arial, sans-serif; padding: 24px; color: #0F172A; max-width: 500px; border: 1px solid #E2E8F0; border-radius: 12px;">
                <h2 style="color: #10B981; margin-bottom: 8px;">MetroMind KMRL</h2>
                <p style="font-size: 15px; color: #334155;">Hi %s,</p>
                <p style="font-size: 14px; color: #64748B;">Good news - your registration request has been <strong>approved</strong> by an administrator. You can now sign in with the username and password you registered with.</p>
                <div style="margin: 24px 0;">
                    <a href="%s" style="background-color: #009688; color: white; padding: 12px 20px; text-decoration: none; border-radius: 8px; font-weight: bold; display: inline-block;">
                        Sign In
                    </a>
                </div>
                <p style="font-size: 12px; color: #94A3B8;">If you didn't request this account, please contact your KMRL administrator.</p>
            </div>
            """.formatted(username, loginUrl);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private void sendRegistrationApprovedEmailFallback(String toEmail, String username, String loginUrl, Throwable t) {
        log.error("Registration-approved email to {} could not be sent (mail service unavailable/circuit open): {}: {}",
                toEmail, t.getClass().getName(), t.getMessage(), t);
    }

    @Retry(name = "emailService")
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendRegistrationRejectedEmailFallback")
    public void sendRegistrationRejectedEmail(String toEmail, String username, String reason) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("Your MetroMind KMRL Registration Was Not Approved");

        String safeReason = reason != null
            ? reason.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            : null;
        String reasonBlock = (safeReason != null && !safeReason.isBlank())
            ? "<p style=\"font-size: 14px; color: #334155;\"><strong>Reason:</strong> " + safeReason + "</p>"
            : "";

        String htmlContent = """
            <div style="font-family: Arial, sans-serif; padding: 24px; color: #0F172A; max-width: 500px; border: 1px solid #E2E8F0; border-radius: 12px;">
                <h2 style="color: #DC2626; margin-bottom: 8px;">MetroMind KMRL</h2>
                <p style="font-size: 15px; color: #334155;">Hi %s,</p>
                <p style="font-size: 14px; color: #64748B;">Your registration request was reviewed by an administrator and was <strong>not approved</strong>.</p>
                %s
                <p style="font-size: 12px; color: #94A3B8;">If you believe this was a mistake, please contact your KMRL administrator, or submit a new registration request with corrected details.</p>
            </div>
            """.formatted(username, reasonBlock);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private void sendRegistrationRejectedEmailFallback(String toEmail, String username, String reason, Throwable t) {
        log.error("Registration-rejected email to {} could not be sent (mail service unavailable/circuit open): {}: {}",
                toEmail, t.getClass().getName(), t.getMessage(), t);
    }
}