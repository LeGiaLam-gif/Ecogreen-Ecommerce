package com.example.backend.service.notification;

/** Sends one email. The only transport behind the EMAIL channel; {@link SmtpEmailSender} or {@link LoggingEmailSender}. */
public interface EmailSender {

    /** SENT: the mail server accepted the message. NOT_CONFIGURED: nothing was delivered because no SMTP host is set. */
    enum Delivery { SENT, NOT_CONFIGURED }

    /** @throws EmailSendException when the message could not be handed to the mail server */
    Delivery send(EmailMessage message);
}
