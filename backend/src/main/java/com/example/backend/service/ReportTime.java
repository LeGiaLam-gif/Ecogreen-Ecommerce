package com.example.backend.service;

import com.example.backend.exception.BadRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Time conversion for the B09 reports (open decision D-9). {@code timestamp} columns hold LOCAL time of the JVM / database
 * session zone (today {@code Asia/Ho_Chi_Minh}, set in BackendApplication and in the datasource URL); the API speaks UTC
 * ISO-8601. Request values are converted to that local zone before they reach a query, and response values are converted
 * back to an {@link Instant}. The zone is the JVM default, so the code stays correct if D-9 is ever resolved by changing
 * the zone in both places; it would NOT be correct if the JVM and the database session disagreed.
 */
public final class ReportTime {

    private ReportTime() {
    }

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    /** Parses an ISO-8601 date-time WITH offset ({@code 2026-10-01T00:00:00Z}) into the database's local time. */
    public static LocalDateTime parseDateTime(String text, String field) {
        try {
            return OffsetDateTime.parse(text.trim()).atZoneSameInstant(zone()).toLocalDateTime();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("Tham số " + field + " phải là thời điểm ISO-8601 có múi giờ, ví dụ 2026-10-01T00:00:00Z.");
        }
    }

    /** Parses an ISO date ({@code 2026-10-01}); the day is a calendar day in the database's local zone. */
    public static LocalDate parseDate(String text, String field) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("Tham số " + field + " phải là ngày ISO-8601 (yyyy-MM-dd).");
        }
    }

    /** A stored local timestamp as the UTC instant it represents. Null stays null. */
    public static Instant toUtc(LocalDateTime local) {
        return local == null ? null : local.atZone(zone()).toInstant();
    }
}
