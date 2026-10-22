package com.example.backend.service.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.backend.entity.NotificationType;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B10 senders: what is handed to the transport, the From rule, and what may appear in a log line or an exception. */
class EmailSendersTest {

    private static final EmailMessage MESSAGE = new EmailMessage(NotificationType.ORDER_CREATED, 9L, "secret.person@example.test",
            "Tiêu đề bí mật", "Nội dung bí mật (văn bản)", "<p>Nội dung bí mật</p>");

    @Test
    void emailMessage_toString_neverShowsTheAddressOrTheContent() {
        String printed = MESSAGE.toString();

        assertEquals("EmailMessage[userId=9, type=ORDER_CREATED]", printed);
        assertFalse(printed.contains("secret.person"));
        assertFalse(printed.contains("bí mật"));
    }

    @Test
    void loggingSender_deliversNothing_andReportsNotConfigured() {
        assertEquals(EmailSender.Delivery.NOT_CONFIGURED, new LoggingEmailSender().send(MESSAGE));
    }

    @Test
    void loggingSender_logsTypeAndUserId_butNeverTheAddressSubjectOrBody() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level before = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            new LoggingEmailSender().send(MESSAGE);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(before);
        }

        assertEquals(1, appender.list.size());
        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("ORDER_CREATED"));
        assertTrue(line.contains("9"));
        assertFalse(line.contains("secret.person"));
        assertFalse(line.contains("example.test"));
        assertFalse(line.contains("bí mật"));
    }

    @Test
    void smtpSender_buildsAMultipartUtf8Message_andSendsIt() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        EmailSender.Delivery result = new SmtpEmailSender(mailSender, "no-reply@ecogreen.test", "").send(MESSAGE);

        assertEquals(EmailSender.Delivery.SENT, result);
        verify(mailSender).send(mime);
        assertEquals("Tiêu đề bí mật", mime.getSubject());
        assertEquals("secret.person@example.test", mime.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("no-reply@ecogreen.test", mime.getFrom()[0].toString());
        assertInstanceOf(Multipart.class, mime.getContent(), "plain text and HTML travel in one multipart message");
    }

    @Test
    void smtpSender_wrapsAMailFailure_withoutLeakingItsMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("535 auth failed for user=secret.person@example.test password=hunter2"))
                .when(mailSender).send(any(MimeMessage.class));

        EmailSendException e = assertThrows(EmailSendException.class,
                () -> new SmtpEmailSender(mailSender, "no-reply@ecogreen.test", "").send(MESSAGE));

        assertEquals("SMTP_MailSendException", e.getMessage());
        assertFalse(e.getMessage().contains("hunter2"));
        assertNull(e.getCause());
    }

    @Test
    void smtpSender_withoutAFromAddress_failsInsteadOfSending() {
        JavaMailSender mailSender = mock(JavaMailSender.class);

        EmailSendException e = assertThrows(EmailSendException.class,
                () -> new SmtpEmailSender(mailSender, "", "plain-login").send(MESSAGE));

        assertEquals("SMTP_NO_FROM_ADDRESS", e.getMessage());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void fromAddress_prefersSmtpFrom_thenAUsernameThatIsAnAddress() {
        assertEquals("shop@example.test", SmtpEmailSender.resolveFrom(" shop@example.test ", "login@example.test"));
        assertEquals("login@example.test", SmtpEmailSender.resolveFrom("", "login@example.test"));
        assertNull(SmtpEmailSender.resolveFrom(null, "plain-login"));
        assertNull(SmtpEmailSender.resolveFrom("", null));
    }
}
