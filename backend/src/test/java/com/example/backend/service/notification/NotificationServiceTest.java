package com.example.backend.service.notification;

import com.example.backend.config.NotificationAsyncConfig;
import com.example.backend.entity.Notification;
import com.example.backend.entity.NotificationChannel;
import com.example.backend.entity.NotificationStatus;
import com.example.backend.entity.NotificationType;
import com.example.backend.repository.NotificationRepository;
import com.example.backend.repository.NotificationSourceRepository;
import com.example.backend.repository.NotificationSourceRepository.ContactRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B10 {@code NotificationService.deliver / send}: the row lifecycle, de-duplication and "never throws". */
class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationSourceRepository sources;
    private EmailSender sender;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        sources = mock(NotificationSourceRepository.class);
        sender = mock(EmailSender.class);
        service = new NotificationService(repository, sources, sender, new NotificationTemplateRenderer(),
                mock(PlatformTransactionManager.class));
        // a saved row gets an id, like the database would assign
        when(repository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", 77L);
            return n;
        });
        when(sender.send(any())).thenReturn(EmailSender.Delivery.SENT);
        when(sources.findContact(9L)).thenReturn(Optional.of(contact(9L, "lan", "lan@example.test", true)));
    }

    static ContactRow contact(Long id, String username, String email, Boolean active) {
        return new ContactRow() {
            public Long getId() { return id; }
            public String getUsername() { return username; }
            public String getEmail() { return email; }
            public Boolean getActive() { return active; }
        };
    }

    private static Map<String, Object> orderContext() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("orderId", 5L);
        ctx.put("orderTotal", new BigDecimal("125000.00"));
        return ctx;
    }

    private Notification savedRow() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void deliver_writesAPendingRow_sendsTheEmail_andMarksItSent() {
        service.deliver(9L, NotificationType.ORDER_CREATED, orderContext(), "order:5:CREATED");

        Notification row = savedRow();
        assertEquals(9L, row.getUserId());
        assertEquals(NotificationType.ORDER_CREATED, row.getType());
        assertEquals(NotificationChannel.EMAIL, row.getChannel());
        assertEquals(NotificationStatus.PENDING, row.getStatus(), "written as PENDING before the send");
        assertEquals("order:5:CREATED", row.getDedupeKey());
        assertEquals("EcoGreen: Đặt hàng thành công", row.getTitle());
        assertTrue(row.getBody().contains("Xin chào lan,"));
        assertTrue(row.getBody().contains("#5"));
        assertTrue(row.getBody().contains("125.000 ₫"));

        ArgumentCaptor<EmailMessage> mail = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(mail.capture());
        assertEquals("lan@example.test", mail.getValue().to());
        assertEquals("EcoGreen: Đặt hàng thành công", mail.getValue().subject());
        assertTrue(mail.getValue().htmlBody().contains("<p"));
        assertEquals(row.getBody(), mail.getValue().textBody(), "the plain-text part of the email is the stored body");

        ArgumentCaptor<LocalDateTime> sentAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).updateStatus(eq(77L), eq(NotificationStatus.SENT), sentAt.capture(), isNull());
        assertNotNull(sentAt.getValue());
    }

    @Test
    void send_isAsyncOnTheNotificationExecutor() throws Exception {
        Async plain = NotificationService.class
                .getMethod("send", Long.class, NotificationType.class, Map.class).getAnnotation(Async.class);
        Async keyed = NotificationService.class
                .getMethod("send", Long.class, NotificationType.class, Map.class, String.class).getAnnotation(Async.class);

        assertNotNull(plain, "send must not run on the request thread");
        assertNotNull(keyed, "send must not run on the request thread");
        assertEquals(NotificationAsyncConfig.EXECUTOR, plain.value());
        assertEquals(NotificationAsyncConfig.EXECUTOR, keyed.value());
    }

    // ── failures never reach the caller ──────────────────────────────────────

    @Test
    void send_whenSmtpFails_doesNotThrowAndMarksFailed() {
        when(sender.send(any())).thenThrow(new EmailSendException("SMTP_MailSendException"));

        assertDoesNotThrow(() -> service.send(9L, NotificationType.ORDER_CREATED, orderContext()));

        verify(repository).updateStatus(77L, NotificationStatus.FAILED, null, NotificationService.REASON_SEND_ERROR);
        verify(repository, never()).updateStatus(anyLong(), eq(NotificationStatus.SENT), any(), any());
    }

    @Test
    void deliver_whenTheSenderThrowsAnythingUnchecked_marksFailed() {
        when(sender.send(any())).thenThrow(new IllegalStateException("password=hunter2 to=lan@example.test"));

        assertDoesNotThrow(() -> service.deliver(9L, NotificationType.PAYMENT_SUCCESS, orderContext(), "order:5:PAID"));

        verify(repository).updateStatus(77L, NotificationStatus.FAILED, null, NotificationService.REASON_SEND_ERROR);
    }

    @Test
    void deliver_whenNoSmtpIsConfigured_marksFailedWithTheNotConfiguredCode() {
        when(sender.send(any())).thenReturn(EmailSender.Delivery.NOT_CONFIGURED);

        service.deliver(9L, NotificationType.ORDER_CREATED, orderContext(), "order:5:CREATED");

        verify(repository).updateStatus(77L, NotificationStatus.FAILED, null, NotificationService.REASON_NOT_CONFIGURED);
    }

    @Test
    void deliver_whenTheDatabaseFails_doesNotThrow() {
        when(repository.save(any(Notification.class))).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> service.deliver(9L, NotificationType.ORDER_CREATED, orderContext(), "k"));
        verify(sender, never()).send(any());
    }

    @Test
    void deliver_whenTheStatusUpdateFails_doesNotThrow() {
        when(repository.updateStatus(anyLong(), any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> service.deliver(9L, NotificationType.ORDER_CREATED, orderContext(), "k"));
    }

    @Test
    void deliver_withMissingArguments_orUnknownUser_doesNothingAndDoesNotThrow() {
        assertDoesNotThrow(() -> service.deliver(null, NotificationType.ORDER_CREATED, null, null));
        assertDoesNotThrow(() -> service.deliver(9L, null, null, null));
        assertDoesNotThrow(() -> service.deliver(404L, NotificationType.ORDER_CREATED, null, null));

        verify(repository, never()).save(any(Notification.class));
        verify(sender, never()).send(any());
    }

    // ── disabled user / no email ─────────────────────────────────────────────

    @Test
    void userDisabledOrNoEmail_marksFailedWithoutThrowing() {
        when(sources.findContact(10L)).thenReturn(Optional.of(contact(10L, "off", "off@example.test", false)));
        when(sources.findContact(11L)).thenReturn(Optional.of(contact(11L, "noemail", null, true)));
        when(sources.findContact(12L)).thenReturn(Optional.of(contact(12L, "blank", "   ", true)));
        when(sources.findContact(13L)).thenReturn(Optional.of(contact(13L, "bad", "not-an-address", true)));

        assertDoesNotThrow(() -> service.deliver(10L, NotificationType.ORDER_CREATED, orderContext(), "a"));
        assertDoesNotThrow(() -> service.deliver(11L, NotificationType.ORDER_CREATED, orderContext(), "b"));
        assertDoesNotThrow(() -> service.deliver(12L, NotificationType.ORDER_CREATED, orderContext(), "c"));
        assertDoesNotThrow(() -> service.deliver(13L, NotificationType.ORDER_CREATED, orderContext(), "d"));

        ArgumentCaptor<Notification> rows = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(4)).save(rows.capture());
        assertEquals(NotificationService.REASON_USER_DISABLED, rows.getAllValues().get(0).getFailureReason());
        assertEquals(NotificationService.REASON_NO_EMAIL, rows.getAllValues().get(1).getFailureReason());
        assertEquals(NotificationService.REASON_NO_EMAIL, rows.getAllValues().get(2).getFailureReason());
        assertEquals(NotificationService.REASON_NO_EMAIL, rows.getAllValues().get(3).getFailureReason(), "no @ is not an address");
        for (Notification row : rows.getAllValues()) {
            assertEquals(NotificationStatus.FAILED, row.getStatus());
            assertNull(row.getSentAt());
        }
        verify(sender, never()).send(any());
        verify(repository, never()).updateStatus(anyLong(), any(), any(), any());
    }

    // ── de-duplication ───────────────────────────────────────────────────────

    @Test
    void sameEventTwice_sendsOnce() {
        Set<String> seen = new HashSet<>();
        when(repository.existsByUserIdAndTypeAndDedupeKey(any(), any(), any()))
                .thenAnswer(inv -> seen.contains(inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2)));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            seen.add(n.getUserId() + "|" + n.getType() + "|" + n.getDedupeKey());
            ReflectionTestUtils.setField(n, "id", 77L);
            return n;
        });

        service.deliver(9L, NotificationType.PAYMENT_SUCCESS, orderContext(), "order:5:PAID");
        service.deliver(9L, NotificationType.PAYMENT_SUCCESS, orderContext(), "order:5:PAID");

        verify(repository, times(1)).save(any(Notification.class));
        verify(sender, times(1)).send(any());
    }

    @Test
    void sameEventTwice_whenTheUniqueConstraintFires_sendsNothingTheSecondTime() {
        when(repository.save(any(Notification.class))).thenThrow(new DataIntegrityViolationException("uq_notifications_dedupe"));

        assertDoesNotThrow(() -> service.deliver(9L, NotificationType.PAYMENT_SUCCESS, orderContext(), "order:5:PAID"));

        verify(sender, never()).send(any());
    }

    @Test
    void aDifferentKeyOrType_isNotADuplicate() {
        when(repository.existsByUserIdAndTypeAndDedupeKey(9L, NotificationType.PAYMENT_SUCCESS, "order:5:PAID")).thenReturn(true);

        service.deliver(9L, NotificationType.PAYMENT_SUCCESS, orderContext(), "order:5:PAID");
        verify(sender, never()).send(any());

        service.deliver(9L, NotificationType.ORDER_SHIPPED, orderContext(), "order:5:SHIPPED");
        service.deliver(9L, NotificationType.PAYMENT_SUCCESS, orderContext(), "order:6:PAID");
        verify(sender, times(2)).send(any());
    }

    @Test
    void withoutAKey_noDuplicateCheckIsMade() {
        service.deliver(9L, NotificationType.ORDER_CREATED, orderContext(), null);

        verify(repository, never()).existsByUserIdAndTypeAndDedupeKey(any(), any(), any());
        assertNull(savedRow().getDedupeKey());
    }

    @Test
    void aTooLongKey_isCutToTheColumnSize() {
        service.deliver(9L, NotificationType.ORDER_CREATED, orderContext(), "k".repeat(300));

        assertEquals(NotificationService.MAX_DEDUPE_KEY, savedRow().getDedupeKey().length());
    }
}
