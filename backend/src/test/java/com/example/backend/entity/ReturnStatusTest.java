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

import static com.example.backend.entity.ReturnStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B07: the return status enum, its transition table, and V60. V60 is plain SQL applied by hand (Flyway is not active), so the
 * SQL tests only read the file: they cannot prove it runs (that needs PostgreSQL) but they stop it drifting from the enum
 * and from the migration policy in CLAUDE.md.
 */
class ReturnStatusTest {

    private static final List<String> MANDATORY_HEADER = List.of(
            "LEGACY DATA IMPACT", "NULLABILITY", "CONSTRAINT ORDER", "DATA-LOSS / ROLLBACK RISK",
            "EMPTY DATABASE", "EXISTING DATABASE");

    @Test
    void theSixStatuses_areExactlyTheSpecifiedOnes() {
        assertEquals(Set.of("PENDING", "INFO_REQUESTED", "APPROVED", "REJECTED", "RETURNED", "REFUNDED"),
                EnumSet.allOf(ReturnStatus.class).stream().map(Enum::name).collect(Collectors.toSet()));
    }

    @Test
    void transitions_everyOrderedPairIsChecked() {
        int checked = 0;
        int allowed = 0;
        for (ReturnStatus from : values()) {
            for (ReturnStatus to : values()) {
                checked++;
                if (from.canTransitionTo(to)) {
                    allowed++;
                }
            }
        }
        assertEquals(36, checked);
        assertEquals(7, allowed, "PENDING x3, INFO_REQUESTED x2, APPROVED x1, RETURNED x1");
    }

    @Test
    void transitions_matchTheSpec() {
        assertEquals(EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED), PENDING.allowedNext());
        assertEquals(EnumSet.of(APPROVED, REJECTED), INFO_REQUESTED.allowedNext());
        assertEquals(EnumSet.of(RETURNED), APPROVED.allowedNext());
        assertEquals(EnumSet.of(REFUNDED), RETURNED.allowedNext());
        assertTrue(REJECTED.allowedNext().isEmpty());
        assertTrue(REFUNDED.allowedNext().isEmpty());
    }

    @Test
    void sameStatusAndNull_areNeverAllowed() {
        for (ReturnStatus status : values()) {
            assertFalse(status.canTransitionTo(status), status + " -> " + status);
            assertFalse(status.canTransitionTo(null));
        }
    }

    @Test
    void shortcuts_areNotAllowed() {
        assertFalse(PENDING.canTransitionTo(RETURNED), "goods must be approved first");
        assertFalse(APPROVED.canTransitionTo(REFUNDED), "goods must be received before the refund");
        assertFalse(REJECTED.canTransitionTo(APPROVED), "a rejected return cannot be reopened");
        assertFalse(RETURNED.canTransitionTo(APPROVED), "no going back");
    }

    @Test
    void terminalStatuses_areRejectedAndRefunded() {
        for (ReturnStatus status : values()) {
            assertEquals(status == REJECTED || status == REFUNDED, status.isTerminal(), status.name());
        }
    }

    @Test
    void openStatuses_areThePendingInfoRequestedAndApproved() {
        assertEquals(EnumSet.of(PENDING, INFO_REQUESTED, APPROVED), EnumSet.copyOf(ReturnStatus.openStatuses()));
        for (ReturnStatus status : values()) {
            assertEquals(ReturnStatus.openStatuses().contains(status), status.isOpen(), status.name());
        }
    }

    @Test
    void adminNoteIsRequiredOnlyForRejectedAndInfoRequested() {
        for (ReturnStatus status : values()) {
            assertEquals(status == REJECTED || status == INFO_REQUESTED, status.requiresAdminNote(), status.name());
        }
    }

    // ---- V60 ----

    private static String readV60() throws Exception {
        try (InputStream in = ReturnStatusTest.class.getResourceAsStream("/db/migration/V60__returns_full.sql")) {
            assertNotNull(in, "V60__returns_full.sql must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String statements(String sql) {
        return sql.lines().filter(line -> !line.stripLeading().startsWith("--")).collect(Collectors.joining("\n"));
    }

    @Test
    void v60_carriesTheMandatoryHeaderBlock() throws Exception {
        String sql = readV60();
        for (String key : MANDATORY_HEADER) {
            assertTrue(sql.contains(key), "V60 must contain the header item " + key);
        }
        assertTrue(sql.contains("Flyway is not active"), "V60 must say it is applied by hand");
    }

    @Test
    void v60_check_matchesTheReturnStatusEnum_noDrift() throws Exception {
        String sql = statements(readV60());
        Matcher check = Pattern.compile("ADD CONSTRAINT chk_returns_status\\s+CHECK \\(status IN \\(([^)]*)\\)\\)").matcher(sql);
        assertTrue(check.find(), "V60 must add chk_returns_status");

        List<String> inSql = new ArrayList<>();
        Matcher value = Pattern.compile("'([A-Z_]+)'").matcher(check.group(1));
        while (value.find()) {
            inSql.add(value.group(1));
        }
        Set<String> inJava = EnumSet.allOf(ReturnStatus.class).stream().map(Enum::name).collect(Collectors.toSet());
        assertEquals(inJava, new HashSet<>(inSql), "the CHECK and ReturnStatus must list the same values");
        assertEquals(inJava.size(), inSql.size(), "no duplicates in the CHECK");
    }

    @Test
    void v60_dropsEveryStatusCheck_lookedUpInPgConstraint_notGuessed() throws Exception {
        String sql = statements(readV60());
        assertTrue(sql.contains("pg_constraint"), "the old CHECK (and a Hibernate-generated one) is found in pg_constraint");
        assertTrue(sql.contains("DROP CONSTRAINT %I"), "every status CHECK found is dropped");
    }

    @Test
    void v60_keepsTheLegacyImageColumn_andNeverDropsData() throws Exception {
        String sql = statements(readV60());
        assertFalse(Pattern.compile("(?i)DROP\\s+COLUMN").matcher(sql).find(), "no column is dropped (data-loss risk)");
        assertFalse(Pattern.compile("(?i)DROP\\s+TABLE").matcher(sql).find(), "no table is dropped");
        assertFalse(Pattern.compile("(?i)\\bDELETE\\s+FROM\\b").matcher(sql).find(), "no row is deleted");
        assertFalse(Pattern.compile("(?i)CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?refunds").matcher(sql).find(),
                "refunds is owned by B06 (V51); V60 only adds return_id");
    }

    @Test
    void v60_isIdempotent_everyCreateAndAlterIsGuarded() throws Exception {
        String sql = statements(readV60());
        for (String create : List.of("CREATE TABLE IF NOT EXISTS return_items", "CREATE TABLE IF NOT EXISTS return_images",
                "ADD COLUMN IF NOT EXISTS return_id")) {
            assertTrue(sql.contains(create), "V60 must contain: " + create);
        }
        assertFalse(Pattern.compile("CREATE\\s+(UNIQUE\\s+)?INDEX\\s+(?!IF NOT EXISTS)").matcher(sql).find(),
                "every index is created with IF NOT EXISTS");
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM return_images"), "the image back-fill is guarded");
    }
}
