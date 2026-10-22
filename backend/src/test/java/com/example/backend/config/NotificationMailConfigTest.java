package com.example.backend.config;

import com.example.backend.service.notification.EmailSender;
import com.example.backend.service.notification.LoggingEmailSender;
import com.example.backend.service.notification.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/** B10: with no SMTP host the logging sender is used; a real host selects SMTP. */
class NotificationMailConfigTest {

    @Test
    void noSmtpConfigured_usesLoggingSender() {
        JavaMailSender bootBuiltSender = mock(JavaMailSender.class);   // Boot also builds one for an EMPTY host

        assertInstanceOf(LoggingEmailSender.class, NotificationMailConfig.select("", bootBuiltSender, "", ""));
        assertInstanceOf(LoggingEmailSender.class, NotificationMailConfig.select("   ", bootBuiltSender, "", ""));
        assertInstanceOf(LoggingEmailSender.class, NotificationMailConfig.select(null, bootBuiltSender, "", ""));
        assertInstanceOf(LoggingEmailSender.class, NotificationMailConfig.select("smtp.example.com", null, "", ""));
    }

    @Test
    void smtpHostConfigured_usesSmtpSender() {
        EmailSender sender = NotificationMailConfig.select("smtp.example.com", mock(JavaMailSender.class),
                "no-reply@example.com", "");

        assertInstanceOf(SmtpEmailSender.class, sender);
    }
}
