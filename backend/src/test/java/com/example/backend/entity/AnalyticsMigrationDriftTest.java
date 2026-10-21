package com.example.backend.entity;

import com.example.backend.service.AnalyticsEventTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B09: V80 is plain SQL applied by hand (Flyway is not active), so this test only READS the file. It cannot prove the SQL
 * runs (that needs PostgreSQL: reported as not executed) but it keeps the SQL, the entity and the Java event-type
 * whitelist from drifting apart and enforces the migration policy of CLAUDE.md section 6.
 */
class AnalyticsMigrationDriftTest {

    private static final String TYPE_FORMAT_REGEX = "^[a-z][a-z0-9_]*$";

    private static String sql() throws Exception {
        try (InputStream in = AnalyticsMigrationDriftTest.class.getResourceAsStream("/db/migration/V80__analytics_events.sql")) {
            assertNotNull(in, "V80__analytics_events.sql must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String statements(String sql) {
        return sql.lines().filter(line -> !line.stripLeading().startsWith("--")).collect(Collectors.joining("\n"));
    }

    @Test
    void migration_hasTheMandatoryHeader_andTheRollbackAndNotExecutedNotes() throws Exception {
        String sql = sql();
        for (String label : List.of("LEGACY DATA IMPACT", "NULLABILITY", "CONSTRAINT ORDER", "DATA-LOSS / ROLLBACK RISK",
                "EMPTY DATABASE", "EXISTING DATABASE", "NOT EXECUTED")) {
            assertTrue(sql.contains(label), "header must contain " + label);
        }
        assertTrue(sql.contains("DROP TABLE IF EXISTS analytics_events;"), "manual rollback SQL must be documented");
    }

    @Test
    void migration_isIdempotent_andDestroysNothing() throws Exception {
        String stmts = statements(sql());

        assertTrue(stmts.contains("CREATE TABLE IF NOT EXISTS analytics_events"));
        assertTrue(stmts.contains("CREATE INDEX IF NOT EXISTS idx_analytics_events_type_created ON analytics_events (event_type, created_at)"));
        for (String constraint : List.of("fk_analytics_events_user", "chk_analytics_events_type_format", "chk_analytics_events_payload_object")) {
            assertTrue(stmts.contains("conname = '" + constraint + "'"), constraint + " must be guarded by a pg_constraint lookup");
            assertTrue(stmts.contains("ADD CONSTRAINT " + constraint), constraint + " must be added");
        }
        assertTrue(stmts.contains("pg_constraint"));
        assertFalse(stmts.toUpperCase().contains("DROP "), "a forward migration must not drop anything");
        assertFalse(stmts.toUpperCase().contains("DELETE FROM"), "a forward migration must not delete rows");
        assertTrue(stmts.contains("payload    JSONB"));
        assertTrue(stmts.contains("ON DELETE SET NULL"));
    }

    @Test
    void entityAndMigration_describeTheSameColumnsAndIndex() throws Exception {
        String stmts = statements(sql());
        Table table = AnalyticsEvent.class.getAnnotation(Table.class);
        assertEquals("analytics_events", table.name());
        assertEquals(1, table.indexes().length);
        Index index = table.indexes()[0];
        assertEquals("idx_analytics_events_type_created", index.name());
        assertEquals("event_type, created_at", index.columnList());
        assertTrue(stmts.contains(index.name() + " ON analytics_events (" + index.columnList() + ")"));

        Set<String> columns = new HashSet<>();
        for (Field field : AnalyticsEvent.class.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                columns.add(column.name());
            }
        }
        assertEquals(Set.of("event_type", "user_id", "session_id", "payload", "created_at"), columns);
        for (String column : columns) {
            assertTrue(stmts.contains(column), "V80 must define " + column);
        }
        assertEquals(50, AnalyticsEvent.class.getDeclaredField("eventType").getAnnotation(Column.class).length());
        assertTrue(stmts.contains("VARCHAR(50)"));
        assertEquals(100, AnalyticsEvent.class.getDeclaredField("sessionId").getAnnotation(Column.class).length());
        assertTrue(stmts.contains("VARCHAR(100)"));

        Field payload = AnalyticsEvent.class.getDeclaredField("payload");
        assertEquals(SqlTypes.JSON, payload.getAnnotation(JdbcTypeCode.class).value(), "the entity maps payload as JSON (jsonb)");
        assertEquals("jsonb", payload.getAnnotation(Column.class).columnDefinition());
    }

    @Test
    void javaEventTypeWhitelist_satisfiesTheDatabaseFormatCheck() throws Exception {
        assertTrue(statements(sql()).contains(TYPE_FORMAT_REGEX), "the CHECK regex in V80 changed: update this test and the whitelist");
        for (String type : AnalyticsEventTypes.ALL) {
            assertTrue(type.matches(TYPE_FORMAT_REGEX), type + " must satisfy chk_analytics_events_type_format");
            assertTrue(type.length() <= 50, type + " must fit VARCHAR(50)");
        }
        assertEquals(AnalyticsEventTypes.ALL.size(), new HashSet<>(AnalyticsEventTypes.ALL).size(), "no duplicates");
    }
}
