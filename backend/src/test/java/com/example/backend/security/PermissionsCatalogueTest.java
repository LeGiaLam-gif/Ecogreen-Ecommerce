package com.example.backend.security;

import com.example.backend.entity.Role;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B01-P3: the Java catalogue is the source of truth; V6__seed_permissions.sql must not drift from it. */
class PermissionsCatalogueTest {

    private static final Pattern CODE_FORMAT = Pattern.compile("^[a-z]+:[a-z_]+$");

    private static String v6() throws Exception {
        try (InputStream in = PermissionsCatalogueTest.class.getResourceAsStream("/db/migration/V6__seed_permissions.sql")) {
            assertNotNull(in, "V6__seed_permissions.sql must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> v6Catalogue(String sql) {
        List<String> codes = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^\\s*\\('([a-z]+:[a-z_]+)',").matcher(sql);
        while (m.find()) codes.add(m.group(1));
        return codes;
    }

    private static Set<String> v6ManagerExclusions(String sql) {
        Matcher m = Pattern.compile("NOT IN \\(([^)]*)\\)").matcher(sql);
        assertTrue(m.find(), "V6 must exclude permissions from MANAGER");
        Set<String> excluded = new HashSet<>();
        Matcher codes = Pattern.compile("'([a-z]+:[a-z_]+)'").matcher(m.group(1));
        while (codes.find()) excluded.add(codes.group(1));
        return excluded;
    }

    @Test
    void catalogue_hasTwentySevenUniqueWellFormedCodes() {
        assertEquals(27, Permissions.ALL.size());
        assertEquals(27, new HashSet<>(Permissions.ALL).size());
        Permissions.ALL.forEach(code -> assertTrue(CODE_FORMAT.matcher(code).matches(), code));
    }

    @Test
    void adminRole_hasAllPermissions() throws Exception {
        assertEquals(Permissions.ALL, Permissions.forRole(Role.ADMIN));
        // V6: the ADMIN block has no exclusion list
        String sql = v6();
        int admin = sql.indexOf("WHERE r.name = 'ADMIN'");
        int manager = sql.indexOf("WHERE r.name = 'MANAGER'");
        assertTrue(admin > 0 && manager > admin);
        assertFalse(sql.substring(admin, manager).contains("NOT IN"));
    }

    @Test
    void managerRole_lacksSystemConfigure_auditView_userDisable() throws Exception {
        List<String> manager = Permissions.forRole(Role.MANAGER);
        assertEquals(24, manager.size());
        assertFalse(manager.contains(Permissions.SYSTEM_CONFIGURE));
        assertFalse(manager.contains(Permissions.AUDIT_VIEW));
        assertFalse(manager.contains(Permissions.USER_DISABLE));
        assertTrue(Permissions.ALL.containsAll(manager));
        assertEquals(Set.of("system:configure", "audit:view", "user:disable"), v6ManagerExclusions(v6()));
    }

    @Test
    void customerRole_hasNoPermissions() throws Exception {
        assertTrue(Permissions.forRole(Role.CUSTOMER).isEmpty());
        assertTrue(Permissions.forRole("anything-else").isEmpty());
        assertFalse(v6().contains("r.name = 'CUSTOMER'"), "V6 must not map any permission to CUSTOMER");
    }

    @Test
    void v6Seed_matchesJavaCatalogue_noDrift() throws Exception {
        assertEquals(Permissions.ALL, v6Catalogue(v6()));
    }

    @Test
    void permissionLiterals_areOnlyDefinedInThePermissionsCatalogue() throws Exception {
        Path root = Path.of("src/main/java");
        if (!Files.isDirectory(root)) return; // not run from the backend module directory
        Pattern literal = Pattern.compile("\"(" + String.join("|", "product", "category", "inventory", "order", "payment",
                "return", "user", "analytics", "audit", "system") + "):[a-z_]+\"");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("Permissions.java")) continue;
                if (literal.matcher(Files.readString(file)).find()) offenders.add(file.toString());
            }
        }
        assertTrue(offenders.isEmpty(), "Permission string literals outside Permissions.java: " + offenders);
    }
}
