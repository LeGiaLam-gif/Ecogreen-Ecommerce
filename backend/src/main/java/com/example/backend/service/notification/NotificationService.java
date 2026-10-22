package com.example.backend.service.notification;

import com.example.backend.config.NotificationAsyncConfig;
import com.example.backend.entity.Notification;
import com.example.backend.entity.NotificationChannel;
import com.example.backend.entity.NotificationStatus;
import com.example.backend.entity.NotificationType;
import com.example.backend.repository.NotificationRepository;
import com.example.backend.repository.NotificationSourceRepository;
import com.example.backend.repository.NotificationSourceRepository.ContactRow;
import com.example.backend.service.notification.NotificationTemplateRenderer.Rendered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a {@code notifications} row and sends the email (B10). Nothing in here may slow down or fail the business flow:
 *
 * <ul>
 *   <li>{@link #send} runs on {@code notificationExecutor} (see {@link NotificationAsyncConfig}), never on the request thread;</li>
 *   <li>{@link #deliver} catches every {@link RuntimeException}; the log carries only the type, the user id and the exception
 *       class, never the address, the content or an SMTP secret;</li>
 *   <li>every write is its own transaction ({@code REQUIRES_NEW}), created inside the try block, so no failure reaches a caller.</li>
 * </ul>
 *
 * The signature of {@link #send(Long, NotificationType, Map)} is stable: when B13 brings a message broker, a consumer can call it
 * (or {@link #deliver}) without any change here.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final String REASON_USER_DISABLED = "USER_DISABLED";
    static final String REASON_NO_EMAIL = "NO_EMAIL";
    static final String REASON_SEND_ERROR = "SEND_ERROR";
    static final String REASON_NOT_CONFIGURED = "EMAIL_NOT_CONFIGURED";
    static final int MAX_DEDUPE_KEY = 100;

    private final NotificationRepository repository;
    private final NotificationSourceRepository sources;
    private final EmailSender emailSender;
    private final NotificationTemplateRenderer renderer;
    private final TransactionTemplate requiresNew;
    private final TransactionTemplate readOnlyNew;

    public NotificationService(NotificationRepository repository, NotificationSourceRepository sources,
                               EmailSender emailSender, NotificationTemplateRenderer renderer,
                               PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.sources = sources;
        this.emailSender = emailSender;
        this.renderer = renderer;
        TransactionTemplate write = new TransactionTemplate(transactionManager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNew = write;
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
        this.readOnlyNew = read;
    }

    /** Sends {@code type} to {@code userId} in the background. No de-duplication (use the overload with a key for events). */
    @Async(NotificationAsyncConfig.EXECUTOR)
    public void send(Long userId, NotificationType type, Map<String, Object> templateContext) {
        deliver(userId, type, templateContext, null);
    }

    /** Same, but a second call with the same {@code (userId, type, dedupeKey)} sends nothing. */
    @Async(NotificationAsyncConfig.EXECUTOR)
    public void send(Long userId, NotificationType type, Map<String, Object> templateContext, String dedupeKey) {
        deliver(userId, type, templateContext, dedupeKey);
    }

    /**
     * The synchronous work behind {@link #send}: de-duplicate, read the customer, write the row, send, record the outcome.
     * Never throws. The event listener (already on the notification executor) calls this directly to avoid a second hop.
     */
    public void deliver(Long userId, NotificationType type, Map<String, Object> templateContext, String dedupeKey) {
        try {
            if (userId == null || type == null) {
                log.warn("Notification skipped: missing user or type");
                return;
            }
            String key = dedupeKey != null && dedupeKey.length() > MAX_DEDUPE_KEY ? dedupeKey.substring(0, MAX_DEDUPE_KEY) : dedupeKey;
            if (key != null && repository.existsByUserIdAndTypeAndDedupeKey(userId, type, key)) {
                log.info("Notification {} for user {} skipped: already handled", type, userId);
                return;
            }
            ContactRow contact = readOnlyNew.execute(status -> sources.findContact(userId).orElse(null));
            if (contact == null) {
                log.warn("Notification {} skipped: user {} not found", type, userId);
                return;
            }
            Map<String, Object> context = new LinkedHashMap<>();
            if (templateContext != null) {
                context.putAll(templateContext);
            }
            context.put("customerName", contact.getUsername());
            Rendered rendered = renderer.render(type, context);

            String blocked = null;
            if (!Boolean.TRUE.equals(contact.getActive())) {
                blocked = REASON_USER_DISABLED;
            } else if (contact.getEmail() == null || contact.getEmail().isBlank() || !contact.getEmail().contains("@")) {
                blocked = REASON_NO_EMAIL;
            }
            Notification row = new Notification(userId, type, NotificationChannel.EMAIL, rendered.title(), rendered.text(),
                    blocked == null ? NotificationStatus.PENDING : NotificationStatus.FAILED, blocked, key);
            Notification saved;
            try {
                saved = requiresNew.execute(status -> repository.save(row));
            } catch (DataIntegrityViolationException duplicate) {
                log.info("Notification {} for user {} skipped: already handled", type, userId);
                return;
            }
            if (blocked != null || saved == null || saved.getId() == null) {
                return;
            }

            Outcome outcome = attempt(new EmailMessage(type, userId, contact.getEmail().trim(), rendered.title(), rendered.text(),
                    rendered.html()));
            requiresNew.executeWithoutResult(status ->
                    repository.updateStatus(saved.getId(), outcome.status(), outcome.sentAt(), outcome.reason()));
        } catch (RuntimeException e) {
            log.warn("Notification {} for user {} failed: {}", type, userId, e.getClass().getSimpleName());
        }
    }

    private record Outcome(NotificationStatus status, LocalDateTime sentAt, String reason) {
    }

    private Outcome attempt(EmailMessage message) {
        try {
            if (emailSender.send(message) == EmailSender.Delivery.SENT) {
                return new Outcome(NotificationStatus.SENT, LocalDateTime.now(), null);
            }
            return new Outcome(NotificationStatus.FAILED, null, REASON_NOT_CONFIGURED);
        } catch (RuntimeException e) {
            log.warn("Email for notification {} (user {}) failed: {}", message.type(), message.userId(), e.getClass().getSimpleName());
            return new Outcome(NotificationStatus.FAILED, null, REASON_SEND_ERROR);
        }
    }
}
