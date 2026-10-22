package com.example.backend.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Sends through Spring's {@link JavaMailSender} (SMTP settings come from {@code spring.mail.*}, i.e. the SMTP_* variables).
 * The plain-text and the HTML version go out as one multipart/alternative message. A missing From address is a configuration
 * error: the send fails (the caller records FAILED / SEND_ERROR) instead of inventing an address. Failures are rethrown as
 * {@link EmailSendException} with a short code, so no address, server detail or credential can reach a log.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    /**
     * @param from     {@code SMTP_FROM}; may be blank
     * @param username {@code SMTP_USERNAME}; used as From only when {@code from} is blank and it looks like an address
     */
    public SmtpEmailSender(JavaMailSender mailSender, String from, String username) {
        this.mailSender = mailSender;
        this.from = resolveFrom(from, username);
    }

    static String resolveFrom(String from, String username) {
        if (from != null && !from.isBlank()) {
            return from.trim();
        }
        if (username != null && username.contains("@")) {
            return username.trim();
        }
        return null;
    }

    @Override
    public Delivery send(EmailMessage message) {
        if (from == null) {
            throw new EmailSendException("SMTP_NO_FROM_ADDRESS");
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
            return Delivery.SENT;
        } catch (MessagingException | MailException e) {
            throw new EmailSendException("SMTP_" + e.getClass().getSimpleName());
        }
    }
}
