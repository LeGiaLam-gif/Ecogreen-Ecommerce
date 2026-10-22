package com.example.backend.service.notification;

/**
 * An email could not be sent. The message is a short code on purpose and no cause is attached: mail-library messages can
 * contain the recipient address or server details, and nothing here may leak them into a log.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String code) {
        super(code, null, false, false);
    }
}
