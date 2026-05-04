package com.peb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending email notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendReleaseSuccessEmail(String recipient, String objectId, String revision) {
        String subject = "Release Successful - " + objectId;
        String text = String.format(
            "The release of object %s revision %s has been completed successfully.",
            objectId, revision
        );
        sendEmail(recipient, subject, text);
    }

    @Async
    public void sendReleaseFailureEmail(String recipient, String objectId, String error) {
        String subject = "Release Failed - " + objectId;
        String text = String.format(
            "The release of object %s has failed.\nError: %s",
            objectId, error
        );
        sendEmail(recipient, subject, text);
    }

    @Async
    public void sendRevisionSuccessEmail(String recipient, String objectId, String revision) {
        String subject = "Revision Created - " + objectId;
        String text = String.format(
            "A new revision %s for object %s has been created successfully.",
            revision, objectId
        );
        sendEmail(recipient, subject, text);
    }

    @Async
    public void sendRevisionFailureEmail(String recipient, String objectId, String error) {
        String subject = "Revision Creation Failed - " + objectId;
        String text = String.format(
            "Failed to create revision for object %s.\nError: %s",
            objectId, error
        );
        sendEmail(recipient, subject, text);
    }

    @Async
    public void sendSaveAsSuccessEmail(String recipient, String sourceId, String newId) {
        String subject = "Save As Successful";
        String text = String.format(
            "Object %s has been successfully copied to %s.",
            sourceId, newId
        );
        sendEmail(recipient, subject, text);
    }

    @Async
    public void sendSaveAsFailureEmail(String recipient, String sourceId, String error) {
        String subject = "Save As Failed";
        String text = String.format(
            "Failed to copy object %s.\nError: %s",
            sourceId, error
        );
        sendEmail(recipient, subject, text);
    }

    private void sendEmail(String recipient, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient + "@company.com"); // Configure domain
            message.setSubject(subject);
            message.setText(text);
            message.setFrom("peb-system@company.com");
            
            mailSender.send(message);
            log.info("Email sent to {} with subject: {}", recipient, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", recipient, e.getMessage());
        }
    }
}