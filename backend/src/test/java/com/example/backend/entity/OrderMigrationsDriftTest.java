package com.example.backend.entity;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B05: V20-V22 are plain SQL applied by hand (Flyway is not active), so these tests only read the files. They cannot prove
 * the SQL runs - that needs PostgreSQL and is reported as not executed - but they stop the SQL from drifting away from the
 * Java enum and from the migration policy in CLAUDE.md.
 */
class OrderMigrationsDriftTest {

    private static final List<String> FILES = List.of(
            "V20__order_state_machine.sql", "V21__order_status_history.sql", "V22__orders_price_breakdown.sql");

    private static final List<String> MANDATORY_HEADER = List.of(
            "LEGACY DATA IMPACT", "NULLABILITY", "CONSTRAINT ORDER", "DATA-LOSS / ROLLBACK RISK",
            "EMPTY DATABASE", "EXISTING DATABASE");

    private static String read(String name) throws Exception {
        try (InputStream in = OrderMigrationsDriftTest.class.getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(in, name + " must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The statements only: comment lines (which also contain example SQL such as the rollback) are removed. */
    private static String statements(String sql) {
        return sql.lines().filter(line -> !line.stripLeading().startsWith("--")).collect(Collectors.joining("\n"));
    }

    @Test
    void migrations_carryTheMandatoryHeaderBlock() throws Exception {
        for (String file : FILES) {
            String sql = read(file);
            for (String key : MANDATORY_HEADER) {
                assertTrue(sql.contains(key), file + " must contain the header item " + key);
            }
        }
    }

    @Test
    void v20_check_matchesTheOrderStatusEnum_noDrift() throws Exception {
        String sql = statements(read("V20__order_state_machine.sql"));
        Matcher check = Pattern.compile("ADD CONSTRAINT chk_orders_status CHECK \\(status IN \\(([^)]*)\\)\\)").matcher(sql);
        assertTrue(check.find(), "V20 must add chk_orders_status");

        List<String> inSql = new ArrayList<>();
        Matcher value = Pattern.compile("'([A-Z_]+)'").matcher(check.group(1));
        while (value.find()) {
            inSql.add(value.group(1));
        }

        Set<String> inJava = new HashSet<>();
        for (OrderStatus status : EnumSet.allOf(OrderStatus.class)) {
            inJava.add(status.name());
        }
        assertEquals(10, inSql.size(), "the CHECK lists 10 values, each once");
        assertEquals(inJava, new HashSet<>(inSql));
    }

    @Test
    void v20_dropsStatusChecksByLookup_mapsLegacyValues_andSetsTheDefault() throws Exception {
        String sql = statements(read("V20__order_state_machine.sql"));
        assertTrue(sql.contains("pg_constraint"), "constraint names are looked up, not guessed");
        assertTrue(sql.contains("SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING'"));
        assertTrue(sql.contains("SET status = 'PROCESSING'") && sql.contains("WHERE status = 'CONFIRMED'"));
        assertFalse(sql.contains("'PAID'      -> 'PAID'"), "PAID is not remapped (decision D-4)");
        assertTrue(sql.contains("ALTER COLUMN status SET DEFAULT 'PENDING_PAYMENT'"));

        int drop = sql.indexOf("DROP CONSTRAINT");
        int map = sql.indexOf("UPDATE orders SET status");
        int add = sql.indexOf("ADD CONSTRAINT chk_orders_status");
        assertTrue(drop >= 0 && drop < map && map < add, "order must be: drop CHECK, map rows, add CHECK");
    }

    @Test
    void v20_header_statesTheDataLossRisk() throws Exception {
        String sql = read("V20__order_state_machine.sql");
        assertTrue(sql.contains("STORED DATA IS CHANGED"));
        assertTrue(sql.contains("DATA-LOSS / ROLLBACK RISK: HIGH"));
        assertTrue(sql.contains("UPDATE orders SET status = 'PENDING'   WHERE status = 'PENDING_PAYMENT'"),
                "the manual rollback SQL must be present");
    }

    @Test
    void v21_createsHistoryTable_andBackfillIsGuardedAgainstDuplicates() throws Exception {
        String sql = statements(read("V21__order_status_history.sql"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS order_status_history"));
        assertTrue(sql.contains("ON DELETE CASCADE") && sql.contains("ON DELETE SET NULL"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS"));
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM order_status_history"), "re-running must not duplicate rows");
        assertTrue(sql.contains("'Migrated from legacy'"));
        assertTrue(sql.indexOf("CREATE TABLE") < sql.indexOf("INSERT INTO order_status_history"),
                "the table is created before it is backfilled");
    }

    @Test
    void v22_backfillsBeforeNotNull_andCreatesBothIndexes() throws Exception {
        String sql = statements(read("V22__orders_price_breakdown.sql"));
        int addColumn = sql.indexOf("ADD COLUMN IF NOT EXISTS subtotal");
        int backfill = sql.indexOf("UPDATE orders SET subtotal = total_price");
        int notNull = sql.indexOf("ALTER COLUMN subtotal        SET NOT NULL");
        assertTrue(addColumn >= 0 && addColumn < backfill && backfill < notNull,
                "columns are added nullable, backfilled, and only then set NOT NULL");
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS discount_code   VARCHAR(50)"));
        assertFalse(sql.contains("REFERENCES discount"), "discount_code has no foreign key");
        assertTrue(sql.contains("idx_orders_user_created") && sql.contains("idx_orders_status_created"));
    }
}
