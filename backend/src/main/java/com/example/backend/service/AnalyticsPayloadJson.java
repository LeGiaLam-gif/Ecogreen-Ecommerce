package com.example.backend.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes and reads the FLAT JSON object kept in {@code analytics_events.payload}: string, number, boolean and null values
 * only. A deliberately small reader/writer in the spirit of {@code ReturnItemsJson}: B09 needs no JSON library for a
 * payload it builds itself, and a malformed stored value reads as an empty map instead of failing the list endpoint.
 */
public final class AnalyticsPayloadJson {

    private AnalyticsPayloadJson() {
    }

    /** Serialises a map whose values are already sanitised (String, Boolean, Number, Enum); anything else is skipped. */
    public static String write(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String value = literal(entry.getValue());
            if (entry.getKey() == null || value == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, entry.getKey());
            sb.append(':').append(value);
        }
        return first ? null : sb.append('}').toString();
    }

    private static String literal(Object value) {
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? new BigDecimal(n.toString()).toPlainString() : null;
        }
        if (value instanceof Enum<?> e) {
            return quote(e.name());
        }
        if (value instanceof String s) {
            return quote(s);
        }
        return null;
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder();
        appendString(sb, text);
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Reads a flat object; null, blank or malformed text gives an empty map. Never throws. */
    public static Map<String, Object> read(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            Reader reader = new Reader(json);
            reader.readObject(result);
            return result;
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    private static final class Reader {
        private final String text;
        private int pos;

        Reader(String text) {
            this.text = text;
        }

        void readObject(Map<String, Object> into) {
            skipSpace();
            expect('{');
            skipSpace();
            if (peek() == '}') {
                pos++;
                end();
                return;
            }
            while (true) {
                skipSpace();
                String key = readString();
                skipSpace();
                expect(':');
                skipSpace();
                into.put(key, readValue());
                skipSpace();
                char c = next();
                if (c == '}') {
                    end();
                    return;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("bad object");
                }
            }
        }

        private void end() {
            skipSpace();
            if (pos != text.length()) {
                throw new IllegalArgumentException("trailing text");
            }
        }

        private Object readValue() {
            char c = peek();
            if (c == '"') {
                return readString();
            }
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            int start = pos;
            while (pos < text.length() && "+-.0123456789eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("bad value");
            }
            BigDecimal number = new BigDecimal(text.substring(start, pos));
            if (number.scale() <= 0 && number.precision() <= 18) {
                return number.longValueExact();
            }
            return number;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw new IllegalArgumentException("bad unicode escape");
                        }
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape");
                }
            }
        }

        private void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("unexpected end");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("expected " + c);
            }
        }
    }
}
