package com.example.backend.service.notification;

import com.example.backend.entity.NotificationType;
import com.example.backend.service.notification.NotificationTemplateRenderer.Rendered;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationTemplateRendererTest {

    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    private static Map<String, Object> ctx(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void templates_escapeHtmlInDynamicValues() {
        Rendered r = renderer.render(NotificationType.RETURN_REJECTED, ctx(
                "customerName", "<script>alert(1)</script>",
                "returnId", 3L,
                "note", "\"><img src=x onerror=alert(2)> & <b>bold</b>"));

        assertFalse(r.html().contains("<script>"), "raw script tag must not survive");
        assertFalse(r.html().contains("<img"), "raw img tag must not survive");
        assertFalse(r.html().contains("<b>bold"), "raw b tag must not survive");
        assertTrue(r.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(r.html().contains("&lt;img src=x onerror=alert(2)&gt; &amp; &lt;b&gt;bold&lt;/b&gt;"));
        // the stored text body is plain text: no HTML structure of ours, the value is kept verbatim
        assertTrue(r.text().contains("<script>alert(1)</script>"));
        assertFalse(r.text().contains("<p"));
    }

    @Test
    void templates_escapeEverywhereIncludingTheGreetingOfEveryType() {
        for (NotificationType type : NotificationType.values()) {
            Rendered r = renderer.render(type, ctx("customerName", "\"><svg/onload=1>", "orderId", 1L, "returnId", 2L,
                    "note", "<i>n</i>", "orderTotal", new BigDecimal("1000")));
            assertFalse(r.html().contains("<svg"), type + " leaked raw markup");
            assertFalse(r.html().contains("<i>"), type + " leaked raw markup");
        }
    }

    @Test
    void everyType_hasAFixedVietnameseSubject_andABody() {
        Set<String> titles = new HashSet<>();
        for (NotificationType type : NotificationType.values()) {
            Rendered r = renderer.render(type, ctx("customerName", "lan", "orderId", 5L, "returnId", 2L));
            assertTrue(r.title().startsWith("EcoGreen: "), type.name());
            assertEquals(r.title(), renderer.render(type, ctx("customerName", "other")).title(), "subject is fixed, never dynamic");
            assertTrue(r.text().startsWith("Xin chào lan,"), type.name());
            assertTrue(r.text().contains("EcoGreen"), type.name());
            assertTrue(r.html().startsWith("<div"), type.name());
            assertTrue(titles.add(r.title()), "each type has its own subject: " + r.title());
        }
    }

    @Test
    void ids_andAmounts_appearInTheRightTemplates() {
        assertTrue(renderer.render(NotificationType.ORDER_CREATED, ctx("orderId", 5L, "orderTotal", new BigDecimal("125000.00"))).text()
                .contains("Đơn hàng #5"));
        assertTrue(renderer.render(NotificationType.PAYMENT_SUCCESS, ctx("orderId", 5L, "orderTotal", new BigDecimal("125000.00"))).text()
                .contains("Tổng thanh toán: 125.000 ₫"));
        assertTrue(renderer.render(NotificationType.RETURN_CREATED, ctx("returnId", 11L, "orderId", 5L)).text()
                .contains("#11") );
        assertTrue(renderer.render(NotificationType.ORDER_CANCELLED, ctx("orderId", 5L, "refundRequired", true)).text()
                .contains("hoàn lại"));
        assertFalse(renderer.render(NotificationType.ORDER_CANCELLED, ctx("orderId", 5L, "refundRequired", false)).text()
                .contains("hoàn lại"));
    }

    @Test
    void notes_appearOnlyWhenPresent_andAreCutAndCleaned() {
        assertFalse(renderer.render(NotificationType.RETURN_REJECTED, ctx("returnId", 1L)).text().contains("Lý do"));
        assertFalse(renderer.render(NotificationType.RETURN_REJECTED, ctx("returnId", 1L, "note", "   ")).text().contains("Lý do"));

        String text = renderer.render(NotificationType.RETURN_INFO_REQUESTED, ctx("returnId", 1L, "note", "x".repeat(1000))).text();
        assertTrue(text.contains("x".repeat(NotificationTemplateRenderer.MAX_NOTE)));
        assertFalse(text.contains("x".repeat(NotificationTemplateRenderer.MAX_NOTE + 1)));

        String cleaned = renderer.render(NotificationType.RETURN_REJECTED, ctx("returnId", 1L, "note", "a\r\nBcc: evil@x.test\tb")).text();
        assertFalse(cleaned.contains("\r"));
        assertTrue(cleaned.contains("Lý do: a  Bcc: evil@x.test b"));
    }

    @Test
    void vnd_formatsWithDotsAndNoLocaleDependency() {
        assertEquals("125.000 ₫", NotificationTemplateRenderer.vnd(new BigDecimal("125000.00")));
        assertEquals("1.234.568 ₫", NotificationTemplateRenderer.vnd(new BigDecimal("1234567.50")));
        assertEquals("0 ₫", NotificationTemplateRenderer.vnd(0));
        assertEquals("999 ₫", NotificationTemplateRenderer.vnd(999L));
        assertNull(NotificationTemplateRenderer.vnd(null));
        assertNull(NotificationTemplateRenderer.vnd("125000"));
    }

    @Test
    void missingOrOddContext_neverThrows() {
        for (NotificationType type : NotificationType.values()) {
            assertDoesNotThrow(() -> renderer.render(type, null), type.name());
            assertDoesNotThrow(() -> renderer.render(type, Map.of()), type.name());
            assertDoesNotThrow(() -> renderer.render(type, ctx("orderId", "not a number", "orderTotal", "abc", "customerName", 42)), type.name());
        }
        assertTrue(renderer.render(NotificationType.ORDER_CREATED, null).text().startsWith("Xin chào,"));
    }

    @Test
    void aLongName_isCut() {
        String text = renderer.render(NotificationType.ORDER_CREATED, ctx("customerName", "n".repeat(500), "orderId", 1L)).text();

        assertTrue(text.contains("n".repeat(NotificationTemplateRenderer.MAX_NAME)));
        assertFalse(text.contains("n".repeat(NotificationTemplateRenderer.MAX_NAME + 1)));
    }
}
