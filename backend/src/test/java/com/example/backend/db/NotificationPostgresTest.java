package com.example.backend.db;

import com.example.backend.entity.NotificationType;
import com.example.backend.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * B10 against a REAL PostgreSQL with the REAL Spring context: the unique de-duplication constraint, the status update and the
 * "no SMTP configured" path, which no mock can prove. Needs ECOGREEN_TEST_PG_URL (optional ECOGREEN_TEST_PG_USER /
 * ECOGREEN_TEST_PG_PASSWORD) and at least one row in {@code users}; otherwise skipped = NOT RUN.
 * WARNING: it boots the application against that database with ddl-auto=update and inserts / removes its own rows (a random
 * dedupe key prefix). Point it only at a scratch database. It sends NO real email unless SMTP_HOST is set in the environment:
 * keep it empty. NOT covered (needs an SMTP server): a real delivery.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties")
@EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
class NotificationPostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECOGREEN_TEST_PG_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.mail.host", () -> "");
    }

    @Autowired private NotificationService service;
    @Autowired private JdbcTemplate jdbc;

    private final String prefix = "b10" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM notifications WHERE dedupe_key LIKE ?", prefix + "%");
    }

    @Test
    void deliver_sameKeyTwice_leavesOneRow_andRecordsTheOutcome() {
        List<Long> users = jdbc.queryForList("SELECT id FROM users ORDER BY id LIMIT 1", Long.class);
        Assumptions.assumeFalse(users.isEmpty(), "needs one user in the scratch database");
        Long userId = users.get(0);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("orderId", 1L);
        ctx.put("orderTotal", new BigDecimal("125000.00"));
        String key = prefix + ":order:1:CREATED";

        service.deliver(userId, NotificationType.ORDER_CREATED, ctx, key);
        service.deliver(userId, NotificationType.ORDER_CREATED, ctx, key);

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE dedupe_key = ?", Integer.class, key));
        String status = jdbc.queryForObject("SELECT status FROM notifications WHERE dedupe_key = ?", String.class, key);
        assertNotEquals("PENDING", status, "the row must leave PENDING (SENT with SMTP, FAILED / EMAIL_NOT_CONFIGURED without)");
    }

    @Test
    void deliver_unknownUser_writesNothing_andDoesNotThrow() {
        service.deliver(-1L, NotificationType.ORDER_CREATED, Map.of("orderId", 1L), prefix + ":none");

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE dedupe_key = ?", Integer.class, prefix + ":none"));
    }
}
