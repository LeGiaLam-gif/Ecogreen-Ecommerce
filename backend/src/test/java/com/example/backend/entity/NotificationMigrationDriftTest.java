package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B10: V90 is plain SQL applied by hand (Flyway is not active), so this test only READS the file. It cannot prove the SQL runs
 * (that needs PostgreSQL: reported as not executed) but it keeps SQL, entity and enums from drifting apart and enforces the
 * migration policy of CLAUDE.md section 6.
 */
class NotificationMigrationDriftTest {

    private static final String TYPE_FORMAT_REGEX = "^[A-Z][A-Z0-9_]*$";

    private static String sql() throws Exception {
        try (InputStream in = NotificationMigrationDriftTest.class.getResourceAsStream("/db/migration/V90__notifications.sql")) {
            assertNotNull(in, "V90__notifications.sql must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String statements(String sql) {
        return sql.lines().filter(line -> !line.stripLeading().startsWith("--")).collect(Collectors.joining("\n"));
    }

    private static <E extends Enum<E>> String quotedList(E[] values) {
        return Arrays.stream(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
    }

    @Test
    void migration_hasTheMandatoryHeader_andTheRollbackAndNotExecutedNotes() throws Exception {
        String sql = sql();
        for (String label : List.of("LEGACY DATA IMPACT", "NULLABILITY", "CONSTRAINT ORDER", "DATA-LOSS / ROLLBACK RISK",
                "EMPTY DATABASE", "EXISTING DATABASE", "NOT EXECUTED", "DEDUPE")) {
            assertTrue(sql.contains(label), "header must contain " + label);
        }
        assertTrue(sql.contains("DROP TABLE IF EXISTS notifications;"), "manual rollback SQL must be documented");
    }

    @Test
    void migration_isIdempotent_andDestroysNothing() throws Exception {
        String stmts = statements(sql());

        assertTrue(stmts.contains("CREATE TABLE IF NOT EXISTS notifications"));
        assertTrue(stmts.contains("CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC)"));
        assertTrue(stmts.contains("CREATE INDEX IF NOT EXISTS idx_notifications_status_created ON notifications (status, created_at)"));
        for (String constraint : List.of("fk_notifications_user", "chk_notifications_channel", "chk_notifications_status",
                "chk_notifications_type_format", "uq_notifications_dedupe")) {
            assertTrue(stmts.contains("conname = '" + constraint + "'"), constraint + " must be guarded by a pg_constraint lookup");
            assertTrue(stmts.contains("ADD CONSTRAINT " + constraint), constraint + " must be added");
        }
        assertFalse(stmts.toUpperCase().contains("DROP "), "a forward migration must not drop anything");
        assertFalse(stmts.toUpperCase().contains("DELETE FROM"), "a forward migration must not delete rows");
        assertTrue(stmts.contains("ON DELETE CASCADE"));
        assertTrue(stmts.contains("UNIQUE (user_id, type, dedupe_key)"));
    }

    @Test
    void entityAndMigration_describeTheSameColumns_indexesAndUniqueConstraint() throws Exception {
        String stmts = statements(sql());
        Table table = Notification.class.getAnnotation(Table.class);
        assertEquals("notifications", table.name());

        Set<String> indexes = new HashSet<>();
        for (Index index : table.indexes()) {
            indexes.add(index.name());
            assertTrue(stmts.contains(index.name() + " ON notifications (" + index.columnList() + ")"), index.name());
        }
        assertEquals(Set.of("idx_notifications_user_created", "idx_notifications_status_created"), indexes);

        UniqueConstraint unique = table.uniqueConstraints()[0];
        assertEquals("uq_notifications_dedupe", unique.name());
        assertEquals(List.of("user_id", "type", "dedupe_key"), List.of(unique.columnNames()));

        Set<String> columns = new HashSet<>();
        for (Field field : Notification.class.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                columns.add(column.name());
            }
        }
        assertEquals(Set.of("user_id", "type", "channel", "title", "body", "status", "failure_reason", "dedupe_key", "sent_at", "created_at"), columns);
        for (String column : columns) {
            assertTrue(stmts.contains(column), "V90 must define " + column);
        }
        assertEquals(50, Notification.class.getDeclaredField("type").getAnnotation(Column.class).length());
        assertEquals(20, Notification.class.getDeclaredField("channel").getAnnotation(Column.class).length());
        assertEquals(20, Notification.class.getDeclaredField("status").getAnnotation(Column.class).length());
        assertEquals(255, Notification.class.getDeclaredField("title").getAnnotation(Column.class).length());
        assertEquals(50, Notification.class.getDeclaredField("failureReason").getAnnotation(Column.class).length());
        assertEquals(100, Notification.class.getDeclaredField("dedupeKey").getAnnotation(Column.class).length());
        assertTrue(stmts.contains("VARCHAR(255)") && stmts.contains("VARCHAR(100)") && stmts.contains("VARCHAR(50)") && stmts.contains("VARCHAR(20)"));
        assertEquals("text", Notification.class.getDeclaredField("body").getAnnotation(Column.class).columnDefinition());
        assertTrue(stmts.contains("TEXT"));
    }

    @Test
    void enumsAndChecks_listTheSameValues() throws Exception {
        String stmts = statements(sql());

        assertTrue(stmts.contains("CHECK (channel IN (" + quotedList(NotificationChannel.values()) + "))"),
                "chk_notifications_channel must list exactly NotificationChannel");
        assertTrue(stmts.contains("CHECK (status IN (" + quotedList(NotificationStatus.values()) + "))"),
                "chk_notifications_status must list exactly NotificationStatus");
        assertTrue(stmts.contains("DEFAULT 'PENDING'"));
        assertTrue(stmts.contains(TYPE_FORMAT_REGEX), "the type CHECK regex changed: update this test and the enum");
        for (NotificationType type : NotificationType.values()) {
            assertTrue(type.name().matches(TYPE_FORMAT_REGEX), type + " must satisfy chk_notifications_type_format");
            assertTrue(type.name().length() <= 50, type + " must fit VARCHAR(50)");
        }
    }
}
