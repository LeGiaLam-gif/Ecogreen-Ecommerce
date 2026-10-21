package com.example.backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsPayloadJsonTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void write_emptyOrNull_isNull() {
        assertNull(AnalyticsPayloadJson.write(null));
        assertNull(AnalyticsPayloadJson.write(Map.of()));
        assertNull(AnalyticsPayloadJson.write(map("nested", Map.of("a", 1))), "only unsupported values -> nothing to store");
    }

    @Test
    void write_scalars_exactText() {
        String json = AnalyticsPayloadJson.write(map("orderId", 5L, "total", new BigDecimal("125000.00"),
                "refundRequired", true, "status", com.example.backend.entity.OrderStatus.PAID, "note", "a\"b\\c\nd"));

        assertEquals("{\"orderId\":5,\"total\":125000.00,\"refundRequired\":true,\"status\":\"PAID\",\"note\":\"a\\\"b\\\\c\\nd\"}", json);
    }

    @Test
    void write_controlCharacters_areEscaped() {
        assertEquals("{\"k\":\"\\u0001\"}", AnalyticsPayloadJson.write(map("k", "\u0001")));
    }

    @Test
    void read_roundTripsWhatWriteProduced() {
        Map<String, Object> original = map("orderId", 5L, "total", new BigDecimal("125000.50"), "ok", false,
                "note", "Tiếng Việt \"quoted\" \\ \n tab\t");

        Map<String, Object> back = AnalyticsPayloadJson.read(AnalyticsPayloadJson.write(original));

        assertEquals(5L, back.get("orderId"));
        assertEquals(new BigDecimal("125000.50"), back.get("total"));
        assertEquals(false, back.get("ok"));
        assertEquals(original.get("note"), back.get("note"));
    }

    @Test
    void read_acceptsWhitespaceNullAndEmptyObject() {
        Map<String, Object> m = AnalyticsPayloadJson.read(" { \"a\" : 1 , \"b\" : null } ");
        assertEquals(1L, m.get("a"));
        assertTrue(m.containsKey("b"));
        assertNull(m.get("b"));
        assertTrue(AnalyticsPayloadJson.read("{}").isEmpty());
    }

    @Test
    void read_malformedOrNull_isEmpty_neverThrows() {
        for (String bad : new String[]{null, "", "  ", "[]", "{", "{\"a\"}", "{\"a\":}", "{\"a\":1,}", "{\"a\":1} x",
                "{\"a\":[1]}", "{\"a\":{\"b\":1}}", "{a:1}", "{\"a\":\"\\q\"}", "{\"a\":\"\\u12\"}"}) {
            assertTrue(AnalyticsPayloadJson.read(bad).isEmpty(), "should be empty: " + bad);
        }
    }
}
