package com.example.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs V4, V5, V6 against a REAL PostgreSQL inside a throw-away schema. It needs a database, so it only runs when
 * ECOGREEN_TEST_PG_URL (e.g. jdbc:postgresql://localhost:5432/scratch) is set - plus optional ECOGREEN_TEST_PG_USER /
 * ECOGREEN_TEST_PG_PASSWORD. Otherwise it is skipped (NOT RUN). Never point it at a database you care about: it only
 * creates and drops its own random schema, but it does execute the migration scripts there.
 */
class RoleRenameMigrationTest {

    private static String script(String name) throws Exception {
        try (InputStream in = RoleRenameMigrationTest.class.getResourceAsStream("/db/migration/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long count(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
    void roleRename_migrationKeepsUserRoleLinks() throws Exception {
        String schema = "p3_" + UUID.randomUUID().toString().replace("-", "");
        String user = System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres");
        String password = System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", "");
        try (Connection c = DriverManager.getConnection(System.getenv("ECOGREEN_TEST_PG_URL"), user, password);
             Statement st = c.createStatement()) {
            try {
                st.execute("CREATE SCHEMA " + schema);
                st.execute("SET search_path TO " + schema);
                st.execute("CREATE TABLE roles (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL UNIQUE)");
                st.execute("CREATE TABLE users (id BIGSERIAL PRIMARY KEY)");
                st.execute("CREATE TABLE user_roles (user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                        + "role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE, PRIMARY KEY (user_id, role_id))");
                // legacy state: roles USER + ADMIN; 3 users hold USER and user 1 also holds ADMIN (4 links)
                st.execute("INSERT INTO roles(name) VALUES ('USER'), ('ADMIN')");
                st.execute("INSERT INTO users DEFAULT VALUES");
                st.execute("INSERT INTO users DEFAULT VALUES");
                st.execute("INSERT INTO users DEFAULT VALUES");
                st.execute("INSERT INTO user_roles SELECT u.id, r.id FROM users u, roles r WHERE r.name = 'USER'");
                st.execute("INSERT INTO user_roles SELECT 1, r.id FROM roles r WHERE r.name = 'ADMIN'");
                long linksBefore = count(st, "SELECT count(*) FROM user_roles");

                for (int round = 0; round < 2; round++) { // second round proves idempotency
                    st.execute(script("V4__permissions.sql"));
                    st.execute(script("V5__roles_customer_manager.sql"));
                    st.execute(script("V6__seed_permissions.sql"));
                }

                assertEquals(0, count(st, "SELECT count(*) FROM roles WHERE name = 'USER'"));
                assertEquals(3, count(st, "SELECT count(*) FROM user_roles x JOIN roles r ON r.id = x.role_id WHERE r.name = 'CUSTOMER'"));
                assertEquals(linksBefore, count(st, "SELECT count(*) FROM user_roles"));
                assertEquals(1, count(st, "SELECT count(*) FROM roles WHERE name = 'MANAGER'"));
                assertEquals(27, count(st, "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.name = 'ADMIN'"));
                assertEquals(24, count(st, "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.name = 'MANAGER'"));
                assertEquals(0, count(st, "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.name = 'CUSTOMER'"));
            } finally {
                st.execute("SET search_path TO public");
                st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
