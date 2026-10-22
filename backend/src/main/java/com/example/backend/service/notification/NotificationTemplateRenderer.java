package com.example.backend.service.notification;

import com.example.backend.entity.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vietnamese templates for every {@link NotificationType} (B10). The subject is fixed per type. Every DYNAMIC value (name,
 * ids, amounts, notes) goes through {@link Dyn}: it is HTML-escaped in the email and left as plain text in the stored body, so
 * a customer name like {@code <script>} can never become markup. Only an order id, a return id, an order total, a refund flag
 * and a staff note are ever used: no address, phone, token or payment data.
 *
 * <p>Context keys: {@code customerName}, {@code orderId}, {@code orderTotal}, {@code returnId}, {@code refundRequired},
 * {@code note}. A missing key renders as a neutral placeholder; it never throws.
 */
@Component
public class NotificationTemplateRenderer {

    static final int MAX_NAME = 100;
    static final int MAX_NOTE = 300;

    /** Subject and plain-text body (stored, shown in the app) and HTML body (sent by email). */
    public record Rendered(String title, String text, String html) {
    }

    /** A dynamic value inside a paragraph: escaped for HTML, verbatim for text. */
    record Dyn(String value) {
    }

    public Rendered render(NotificationType type, Map<String, Object> ctx) {
        Map<String, Object> c = ctx == null ? Map.of() : ctx;
        String title = title(type);
        List<List<Object>> paragraphs = new ArrayList<>();
        paragraphs.add(greeting(c));
        paragraphs.addAll(content(type, c));
        paragraphs.add(List.of("Trân trọng,"));
        paragraphs.add(List.of("EcoGreen"));
        paragraphs.add(List.of("Email này được gửi tự động, vui lòng không trả lời."));

        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif;font-size:14px;line-height:1.5;color:#222\">");
        html.append("<h2 style=\"font-size:18px;margin:0 0 12px\">").append(HtmlUtils.htmlEscape(title)).append("</h2>");
        for (List<Object> paragraph : paragraphs) {
            StringBuilder rawLine = new StringBuilder();
            StringBuilder htmlLine = new StringBuilder();
            for (Object part : paragraph) {
                if (part instanceof Dyn d) {
                    rawLine.append(d.value());
                    htmlLine.append(HtmlUtils.htmlEscape(d.value()));
                } else {
                    rawLine.append(part);
                    htmlLine.append(HtmlUtils.htmlEscape(String.valueOf(part)));
                }
            }
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(rawLine);
            html.append("<p style=\"margin:0 0 10px\">").append(htmlLine).append("</p>");
        }
        html.append("</div>");
        return new Rendered(title, text.toString(), html.toString());
    }

    /** Fixed subject per type. */
    public String title(NotificationType type) {
        return switch (type) {
            case ORDER_CREATED -> "EcoGreen: Đặt hàng thành công";
            case PAYMENT_SUCCESS -> "EcoGreen: Thanh toán thành công";
            case ORDER_SHIPPED -> "EcoGreen: Đơn hàng đang được giao";
            case ORDER_DELIVERED -> "EcoGreen: Đơn hàng đã được giao";
            case ORDER_CANCELLED -> "EcoGreen: Đơn hàng đã bị hủy";
            case RETURN_CREATED -> "EcoGreen: Đã ghi nhận yêu cầu đổi trả";
            case RETURN_APPROVED -> "EcoGreen: Yêu cầu đổi trả được chấp nhận";
            case RETURN_REJECTED -> "EcoGreen: Yêu cầu đổi trả bị từ chối";
            case RETURN_INFO_REQUESTED -> "EcoGreen: Cần bổ sung thông tin đổi trả";
            case RETURN_REFUNDED -> "EcoGreen: Đã ghi nhận hoàn tiền đổi trả";
        };
    }

    private List<Object> greeting(Map<String, Object> c) {
        String name = clean(c.get("customerName"), MAX_NAME);
        return name.isEmpty() ? List.of("Xin chào,") : List.of("Xin chào ", new Dyn(name), ",");
    }

    private List<List<Object>> content(NotificationType type, Map<String, Object> c) {
        Dyn order = new Dyn(id(c.get("orderId")));
        Dyn ret = new Dyn(id(c.get("returnId")));
        List<List<Object>> out = new ArrayList<>();
        switch (type) {
            case ORDER_CREATED -> {
                out.add(List.of("Cảm ơn bạn đã đặt hàng. Đơn hàng #", order, " đã được ghi nhận."));
                addTotal(out, c);
                out.add(List.of("Chúng tôi sẽ thông báo cho bạn khi đơn hàng có cập nhật mới."));
            }
            case PAYMENT_SUCCESS -> {
                out.add(List.of("Chúng tôi đã nhận được thanh toán cho đơn hàng #", order, "."));
                addTotal(out, c);
                out.add(List.of("Đơn hàng của bạn sẽ được xử lý trong thời gian sớm nhất."));
            }
            case ORDER_SHIPPED -> out.add(List.of("Đơn hàng #", order, " đã được bàn giao cho đơn vị vận chuyển và đang trên đường đến bạn."));
            case ORDER_DELIVERED -> out.add(List.of("Đơn hàng #", order, " đã được giao thành công. Cảm ơn bạn đã mua sắm tại EcoGreen."));
            case ORDER_CANCELLED -> {
                out.add(List.of("Đơn hàng #", order, " đã bị hủy."));
                if (Boolean.TRUE.equals(c.get("refundRequired"))) {
                    out.add(List.of("Khoản thanh toán của bạn sẽ được hoàn lại theo quy trình hoàn tiền."));
                }
            }
            case RETURN_CREATED -> out.add(List.of("Chúng tôi đã ghi nhận yêu cầu đổi trả #", ret, " cho đơn hàng #", order, ". Chúng tôi sẽ xem xét và phản hồi sớm."));
            case RETURN_APPROVED -> out.add(List.of("Yêu cầu đổi trả #", ret, " của bạn đã được chấp nhận. Vui lòng gửi hàng về theo hướng dẫn."));
            case RETURN_REJECTED -> {
                out.add(List.of("Rất tiếc, yêu cầu đổi trả #", ret, " của bạn đã bị từ chối."));
                addNote(out, c, "Lý do: ");
            }
            case RETURN_INFO_REQUESTED -> {
                out.add(List.of("Chúng tôi cần thêm thông tin để xử lý yêu cầu đổi trả #", ret, "."));
                addNote(out, c, "Nội dung cần bổ sung: ");
            }
            case RETURN_REFUNDED -> out.add(List.of("Yêu cầu hoàn tiền cho đổi trả #", ret, " đã được ghi nhận và đang được xử lý."));
        }
        return out;
    }

    private void addTotal(List<List<Object>> out, Map<String, Object> c) {
        String money = vnd(c.get("orderTotal"));
        if (money != null) {
            out.add(List.of("Tổng thanh toán: ", new Dyn(money)));
        }
    }

    private void addNote(List<List<Object>> out, Map<String, Object> c, String label) {
        String note = clean(c.get("note"), MAX_NOTE);
        if (!note.isEmpty()) {
            out.add(List.of(label, new Dyn(note)));
        }
    }

    /** {@code 125000} becomes {@code 125.000 ₫}: fixed grouping, no rounding surprises, independent of the JVM locale. */
    static String vnd(Object amount) {
        BigDecimal value;
        if (amount instanceof BigDecimal b) {
            value = b;
        } else if (amount instanceof Number n) {
            value = new BigDecimal(n.toString());
        } else {
            return null;
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        return format.format(value.setScale(0, RoundingMode.HALF_UP)) + " ₫";
    }

    private static String id(Object value) {
        return value instanceof Number n ? String.valueOf(n.longValue()) : "?";
    }

    /** Text without control characters (so no line break can be smuggled in), trimmed and cut to {@code max}. */
    static String clean(Object value, int max) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value).replaceAll("\\p{Cntrl}", " ").trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
