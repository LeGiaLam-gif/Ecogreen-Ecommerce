package com.example.backend.dto;

import com.example.backend.exception.BadRequestException;
import com.example.backend.service.ReturnItemDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the {@code items} field of the multipart create request: {@code [{"orderItemId":1,"quantity":2}, ...]}.
 * A deliberately small, strict reader: only that shape is accepted (both fields, whole numbers, any order, nothing else), so
 * a malformed or hostile value is a 400 and no JSON library or class name can leak into an error.
 */
public final class ReturnItemsJson {

    private static final int MAX_ITEMS = 100;
    private static final Pattern OBJECT = Pattern.compile("\\{[^{}\\[\\]]*}");
    private static final Pattern ORDER_ITEM_ID = Pattern.compile("\"orderItemId\"\\s*:\\s*(\\d{1,18})(?!\\d)");
    private static final Pattern QUANTITY = Pattern.compile("\"quantity\"\\s*:\\s*(\\d{1,9})(?!\\d)");

    private ReturnItemsJson() {
    }

    /** @throws BadRequestException (VALIDATION_ERROR on /api/v1) when the text is not exactly that shape */
    public static List<ReturnItemDraft> parse(String json) {
        if (json == null || json.isBlank()) {
            throw new BadRequestException("Vui lòng chọn ít nhất một sản phẩm cần trả.");
        }
        String text = json.strip();
        if (text.length() < 2 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            throw invalid();
        }
        List<ReturnItemDraft> drafts = new ArrayList<>();
        int position = 1;
        int end = text.length() - 1;
        Matcher object = OBJECT.matcher(text);
        while (true) {
            // Only whitespace (and one comma between objects) may sit between the objects.
            if (!object.find(position)) {
                break;
            }
            String gap = text.substring(position, object.start());
            if (!(drafts.isEmpty() ? gap.isBlank() : gap.strip().equals(","))) {
                throw invalid();
            }
            drafts.add(parseObject(object.group()));
            if (drafts.size() > MAX_ITEMS) {
                throw invalid();
            }
            position = object.end();
        }
        if (!text.substring(position, end).isBlank()) {
            throw invalid();
        }
        if (drafts.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ít nhất một sản phẩm cần trả.");
        }
        return drafts;
    }

    private static ReturnItemDraft parseObject(String object) {
        Matcher id = ORDER_ITEM_ID.matcher(object);
        Matcher quantity = QUANTITY.matcher(object);
        if (!id.find() || !quantity.find()) {
            throw invalid();
        }
        String rest = object.substring(1, object.length() - 1)
                .replace(id.group(), "").replace(quantity.group(), "");
        if (!rest.replace(",", "").isBlank() || rest.chars().filter(c -> c == ',').count() != 1) {
            throw invalid();
        }
        try {
            return new ReturnItemDraft(Long.parseLong(id.group(1)), Integer.parseInt(quantity.group(1)));
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Danh sách sản phẩm cần trả không hợp lệ.");
    }
}
