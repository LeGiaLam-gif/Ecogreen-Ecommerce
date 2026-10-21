package com.example.backend.service;

import com.example.backend.api.ApiPaging;
import com.example.backend.entity.AnalyticsEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.AnalyticsEventRepository;
import com.example.backend.repository.OrderReportRepository;
import com.example.backend.repository.OrderReportRepository.OrderOwnerRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records business events into {@code analytics_events} and lists them for the admin (B09). It only ever INSERTS into its
 * own table; it reads other modules' tables but never writes them.
 *
 * <p>{@link #track} NEVER throws. The write runs in its own {@code REQUIRES_NEW} transaction created INSIDE the try block
 * (a {@code @Transactional} method would let a commit failure escape its own catch), so a failing analytics write can
 * neither fail nor roll back the business flow that triggered it.
 */
@Service
public class AnalyticsEventService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventService.class);

    static final int MAX_SESSION_ID = 100;
    static final int MAX_STRING_VALUE = 200;
    static final int MAX_PAYLOAD_KEYS = 20;
    static final int MAX_KEY_LENGTH = 40;
    /** Default window of the events list when {@code from} is omitted. */
    static final int DEFAULT_WINDOW_DAYS = 30;
    /** Widest window the events list accepts, so a request cannot scan the whole table. */
    static final int MAX_WINDOW_DAYS = 366;

    /** A payload key containing any of these (case-insensitive) is dropped: no personal, credential or payment data. */
    static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "email", "mail", "token", "password", "passwd", "secret", "address", "phone", "card", "cvv",
            "authorization", "cookie", "signature", "bank");

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt", "id", "id", "eventType", "eventType");

    private final AnalyticsEventRepository repository;
    private final OrderReportRepository orderReportRepository;
    private final TransactionTemplate requiresNew;

    public AnalyticsEventService(AnalyticsEventRepository repository, OrderReportRepository orderReportRepository,
                                 PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.orderReportRepository = orderReportRepository;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNew = template;
    }

    /**
     * Records one event. Never throws: an unknown {@code eventType} is ignored (logged), and any failure while writing is
     * logged without the payload. The payload is sanitised first (see {@link #sanitize}).
     */
    public void track(String eventType, Long userId, String sessionId, Map<String, Object> payload) {
        try {
            if (!AnalyticsEventTypes.isKnown(eventType)) {
                log.warn("Analytics event ignored: unknown event type");
                return;
            }
            AnalyticsEvent event = new AnalyticsEvent(eventType, userId, truncate(sessionId, MAX_SESSION_ID),
                    AnalyticsPayloadJson.write(sanitize(payload)));
            requiresNew.executeWithoutResult(status -> repository.save(event));
        } catch (RuntimeException e) {
            log.warn("Could not record analytics event {}: {}", eventType, e.getClass().getSimpleName());
        }
    }

    /**
     * Records an event about an order whose owner is not in the published event: reads the owner and the total (read only,
     * own transaction), adds {@code orderId} and {@code orderTotal} to the payload and calls {@link #track}. Never throws;
     * if the lookup fails the event is still recorded without a user.
     */
    public void trackForOrder(String eventType, Long orderId, Map<String, Object> payload) {
        try {
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.put("orderId", orderId);
            if (payload != null) {
                merged.putAll(payload);
            }
            Long userId = null;
            try {
                OrderOwnerRow owner = orderId == null ? null
                        : requiresNew.execute(status -> orderReportRepository.findOwnerRow(orderId).orElse(null));
                if (owner != null) {
                    userId = owner.getUserId();
                    merged.put("orderTotal", owner.getTotalPrice());
                }
            } catch (RuntimeException e) {
                log.warn("Could not read order {} for analytics: {}", orderId, e.getClass().getSimpleName());
            }
            track(eventType, userId, null, merged);
        } catch (RuntimeException e) {
            log.warn("Could not record analytics event {}: {}", eventType, e.getClass().getSimpleName());
        }
    }

    /**
     * Keeps only flat, non-sensitive entries: the key is {@code [A-Za-z][A-Za-z0-9_]*} (at most 40 characters) and does not
     * contain a sensitive word; the value is a String (cut to 200 characters), Boolean, Number or Enum. Nulls, nested
     * values and everything else are dropped. At most 20 entries are kept.
     */
    static Map<String, Object> sanitize(Map<String, Object> payload) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (payload == null) {
            return clean;
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (clean.size() >= MAX_PAYLOAD_KEYS) {
                break;
            }
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]{0," + (MAX_KEY_LENGTH - 1) + "}") || isSensitive(key)) {
                continue;
            }
            if (value instanceof String s) {
                clean.put(key, truncate(s, MAX_STRING_VALUE));
            } else if (value instanceof Boolean || value instanceof Number || value instanceof Enum<?>) {
                clean.put(key, value);
            }
        }
        return clean;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    // ---------------------------------------------------------------
    // Admin list
    // ---------------------------------------------------------------

    /** Page size is clamped by {@link ApiPaging} (default 20, at most 100); {@code sort} is whitelisted, default newest first. */
    public static Pageable pageable(Integer page, Integer size, String sort) {
        return ApiPaging.of(page, size, sort, SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    /**
     * Events in {@code [from, to)} (ISO-8601 with offset). {@code to} defaults to now and {@code from} to {@code to} minus
     * 30 days. A blank {@code type} means every type; an unknown one is a 400 (VALIDATION_ERROR).
     *
     * @throws BadRequestException for an unknown type, an unparsable bound, {@code from} after {@code to}, or a window
     *                             wider than 366 days
     */
    @Transactional(readOnly = true)
    public Page<AnalyticsEvent> listEvents(String type, String from, String to, Pageable pageable) {
        String wanted = type == null || type.isBlank() ? null : type.trim();
        if (wanted != null && !AnalyticsEventTypes.isKnown(wanted)) {
            throw new BadRequestException("Loại sự kiện không hợp lệ.");
        }
        LocalDateTime end = to == null || to.isBlank() ? LocalDateTime.now() : ReportTime.parseDateTime(to, "to");
        LocalDateTime start = from == null || from.isBlank()
                ? end.minusDays(DEFAULT_WINDOW_DAYS) : ReportTime.parseDateTime(from, "from");
        if (start.isAfter(end)) {
            throw new BadRequestException("Tham số from phải không sau to.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_WINDOW_DAYS) {
            throw new BadRequestException("Khoảng thời gian tối đa là " + MAX_WINDOW_DAYS + " ngày.");
        }
        return wanted == null
                ? repository.findInRange(start, end, pageable)
                : repository.findByTypeInRange(wanted, start, end, pageable);
    }
}
