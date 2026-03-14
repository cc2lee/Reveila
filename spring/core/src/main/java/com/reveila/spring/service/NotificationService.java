package com.reveila.spring.service;

import com.reveila.spring.model.jpa.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for handling notifications and alerts.
 */
@Service
public class NotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${notification.alert.email:admin@reveila.com}")
    private String alertEmail;

    @Value("${spring.mail.username:reveila.dev@gmail.com}")
    private String fromEmail;

    /**
     * Sends a sovereignty alert to the configured email address.
     * 
     * @param log The audit log that triggered the alert.
     */
    public void sendSovereigntyAlert(AuditLog log) {
        System.out.println("--------------------------------------------------");
        System.out.println("📧 [NOTIFICATION] Dispatching real-time alert...");
        
        if (mailSender == null) {
            System.err.println("❌ ERROR: JavaMailSender not initialized. Check SMTP settings.");
            logConsoleFallback(log);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(alertEmail);
            message.setSubject("🚨 REVEILA ALERT: Sovereignty Policy Violation");
            
            String body = String.format(
                "Sovereignty Policy Violation Detected!\n\n" +
                "Agent ID: %s\n" +
                "Session ID: %s\n" +
                "Policy Triggered: %s\n" +
                "Risk Score: %s\n\n" +
                "Proposed Action: %s\n" +
                "Inner Monologue: %s\n\n" +
                "Timestamp: %s\n",
                log.getAgentId(),
                log.getSessionId(),
                log.getPolicyTriggered(),
                log.getRiskScore(),
                log.getProposedAction(),
                log.getInnerMonologue(),
                log.getTimestamp()
            );
            
            message.setText(body);
            mailSender.send(message);
            
            System.out.println("✅ [SUCCESS] Alert sent to: " + alertEmail);
        } catch (Exception e) {
            System.err.println("❌ [FAILED] Could not send email: " + e.getMessage());
            logConsoleFallback(log);
        }
        System.out.println("--------------------------------------------------");
    }

    private void logConsoleFallback(AuditLog log) {
        System.out.println("⚠️  [FALLBACK] Console Alert Trace:");
        System.out.println("   - Policy: " + log.getPolicyTriggered());
        System.out.println("   - Risk Score: " + log.getRiskScore());
    }
}
