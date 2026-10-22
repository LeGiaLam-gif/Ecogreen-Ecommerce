package com.example.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no SMTP host is configured (development, tests). Delivers nothing and logs ONLY the notification type and the user
 * id: never the address, the subject, the body or any secret.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public Delivery send(EmailMessage message) {
        log.info("Email not sent (SMTP_HOST is empty): notification {} for user {}", message.type(), message.userId());
        return Delivery.NOT_CONFIGURED;
    }
}
