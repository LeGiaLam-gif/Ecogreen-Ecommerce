package com.example.backend.config;

import com.example.backend.service.notification.EmailSender;
import com.example.backend.service.notification.LoggingEmailSender;
import com.example.backend.service.notification.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the {@link EmailSender} (B10): SMTP only when {@code spring.mail.host} (SMTP_HOST) has text AND a JavaMailSender
 * exists; otherwise the logging sender, so development and tests never need a mail server and never fail because of one.
 * (A plain {@code @ConditionalOnProperty} is not enough: Spring Boot also builds a JavaMailSender for an EMPTY host.)
 */
@Configuration
public class NotificationMailConfig {

    @Bean
    public EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
                                   @Value("${spring.mail.host:}") String host,
                                   @Value("${spring.mail.username:}") String username,
                                   @Value("${ecogreen.notification.mail-from:}") String from) {
        return select(host, mailSender.getIfAvailable(), from, username);
    }

    public static EmailSender select(String host, JavaMailSender mailSender, String from, String username) {
        if (host == null || host.isBlank() || mailSender == null) {
            return new LoggingEmailSender();
        }
        return new SmtpEmailSender(mailSender, from, username);
    }
}
