package com.example.backend.db;

import com.example.backend.entity.AnalyticsEvent;
import com.example.backend.repository.AnalyticsEventRepository;
import com.example.backend.service.AnalyticsEventService;
import com.example.backend.service.AnalyticsEventTypes;
import com.example.backend.service.AnalyticsPayloadJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B09 against a REAL PostgreSQL with the REAL Spring context: the jsonb round trip of the payload and the (event_type,
 * created_at) range query, which no mock can check. Needs ECOGREEN_TEST_PG_URL (optional ECOGREEN_TEST_PG_USER /
 * ECOGREEN_TEST_PG_PASSWORD); otherwise skipped = NOT RUN.
 * WARNING: it boots the application against that database with ddl-auto=update and inserts / removes its own rows (session
 * ids prefixed with a random tag). Point it only at a scratch database. If V80 was not applied, Hibernate creates the table
 * without the foreign key and the CHECKs. NOT covered here (needs order / payment / refund fixtures): the SQL of the
 * refund-summary and customer-frequency aggregations.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties")
@EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
class AnalyticsPostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECOGREEN_TEST_PG_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired private AnalyticsEventService service;
    @Autowired private AnalyticsEventRepository repository;
    @Autowired private JdbcTemplate jdbc;

    private final String tag = "b09" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM analytics_events WHERE session_id = ?", tag);
    }

    @Test
    void track_storesAJsonbObject_andItReadsBackThroughTheEntity() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", 5L);
        payload.put("total", new BigDecimal("125000.00"));
        payload.put("note", "Tiếng Việt \"quoted\"");

        service.track(AnalyticsEventTypes.ORDER_CREATED, null, tag, payload);

        assertEquals("object", jdbc.queryForObject(
                "SELECT jsonb_typeof(payload) FROM analytics_events WHERE session_id = ?", String.class, tag));
        assertEquals(5L, jdbc.queryForObject(
                "SELECT (payload->>'orderId')::bigint FROM analytics_events WHERE session_id = ?", Long.class, tag));

        LocalDateTime from = LocalDateTime.now().minusMinutes(5);
        Page<AnalyticsEvent> page = repository.findByTypeInRange("order_created", from, LocalDateTime.now().plusMinutes(5),
                PageRequest.of(0, 100));
        AnalyticsEvent mine = page.getContent().stream().filter(e -> tag.equals(e.getSessionId())).findFirst().orElseThrow();
        Map<String, Object> back = AnalyticsPayloadJson.read(mine.getPayload());
        assertEquals(5L, back.get("orderId"));
        assertEquals("Tiếng Việt \"quoted\"", back.get("note"));
        assertTrue(mine.getCreatedAt() != null);
    }

    @Test
    void track_withAnUnknownUser_neverThrows() {
        // user id 0 violates fk_analytics_events_user when V80 was applied; without the FK the row is simply stored
        service.track(AnalyticsEventTypes.ORDER_CREATED, 0L, tag, Map.of("orderId", 1L));
        // reaching this line is the assertion: a failed analytics write never surfaces
    }
}
