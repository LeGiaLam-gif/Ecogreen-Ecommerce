package com.example.backend.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The ONE source of truth for return statuses (B07). The database CHECK {@code chk_returns_status} in
 * V60__returns_full.sql lists the same six values, and the frontend labels live in {@code utils/returnStatus.js}.
 *
 * <pre>
 * PENDING        -> INFO_REQUESTED | APPROVED | REJECTED
 * INFO_REQUESTED -> APPROVED | REJECTED
 * APPROVED       -> RETURNED            (the goods reached the warehouse: restock + order RETURNED)
 * RETURNED       -> REFUNDED            (a PENDING refund is recorded + order REFUNDED)
 * REJECTED, REFUNDED are terminal
 * </pre>
 *
 * The table is the only place the transitions are defined; ReturnService enforces it on the server and the admin
 * response lists {@code allowedNextStatuses} so the browser never decides which button to show.
 */
public enum ReturnStatus {

    PENDING,
    INFO_REQUESTED,
    APPROVED,
    REJECTED,
    RETURNED,
    REFUNDED;

    private static final Map<ReturnStatus, Set<ReturnStatus>> TRANSITIONS = buildTransitions();

    private static final Set<ReturnStatus> OPEN_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(PENDING, INFO_REQUESTED, APPROVED));

    private static final Set<ReturnStatus> NOTE_REQUIRED = Collections.unmodifiableSet(
            EnumSet.of(REJECTED, INFO_REQUESTED));

    private static Map<ReturnStatus, Set<ReturnStatus>> buildTransitions() {
        Map<ReturnStatus, Set<ReturnStatus>> table = new EnumMap<>(ReturnStatus.class);
        table.put(PENDING, EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED));
        table.put(INFO_REQUESTED, EnumSet.of(APPROVED, REJECTED));
        table.put(APPROVED, EnumSet.of(RETURNED));
        table.put(RETURNED, EnumSet.of(REFUNDED));
        table.put(REJECTED, EnumSet.noneOf(ReturnStatus.class));   // terminal
        table.put(REFUNDED, EnumSet.noneOf(ReturnStatus.class));   // terminal

        Map<ReturnStatus, Set<ReturnStatus>> readOnly = new EnumMap<>(ReturnStatus.class);
        for (Map.Entry<ReturnStatus, Set<ReturnStatus>> entry : table.entrySet()) {
            readOnly.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(readOnly);
    }

    /** The statuses a return in this status may move to (read-only, never null, empty for terminal statuses). */
    public Set<ReturnStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }

    /** True only for a listed transition; a transition to the same status is never allowed. */
    public boolean canTransitionTo(ReturnStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** True while the return is still being handled (PENDING, INFO_REQUESTED, APPROVED): the order cannot get another one. */
    public boolean isOpen() {
        return OPEN_STATUSES.contains(this);
    }

    /** True when staff must write a note for the customer to move a return TO this status (REJECTED, INFO_REQUESTED). */
    public boolean requiresAdminNote() {
        return NOTE_REQUIRED.contains(this);
    }

    /** The open statuses (read-only); pass it as a query parameter instead of status literals. */
    public static Set<ReturnStatus> openStatuses() {
        return OPEN_STATUSES;
    }
}
