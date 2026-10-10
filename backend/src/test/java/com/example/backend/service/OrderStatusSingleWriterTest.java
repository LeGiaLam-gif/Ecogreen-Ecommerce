package com.example.backend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B05 Definition of Done: "one method changes orders.status". These tests read the production source, so a second writer
 * (or a revenue query with a status literal) fails the build instead of slipping in during review.
 */
class OrderStatusSingleWriterTest {

    private static Path mainSources() {
        for (String candidate : new String[] {"src/main/java", "backend/src/main/java"}) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new AssertionError("production sources not found from " + Path.of("").toAbsolutePath());
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(mainSources())) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean isCommentLine(String line) {
        String t = line.stripLeading();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*");
    }

    @Test
    void orderStatus_isWrittenOnlyInsideOrderService_transitionStatus() throws IOException {
        Pattern write = Pattern.compile("\\.setStatus\\(\\s*OrderStatus\\b|\\border\\.setStatus\\(");
        List<String> writers = new ArrayList<>();
        for (Path file : javaFiles()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!isCommentLine(line) && write.matcher(line).find()) {
                    writers.add(file.getFileName() + ":" + (i + 1) + "  " + line.strip());
                }
            }
        }
        assertEquals(1, writers.size(), "exactly one place may set an order status, found: " + writers);
        assertTrue(writers.get(0).startsWith("OrderService.java:"), writers.get(0));
        assertTrue(writers.get(0).contains("order.setStatus(newStatus)"), writers.get(0));
    }

    @Test
    void noQueryUpdatesOrderStatusDirectly() throws IOException {
        Pattern bulkUpdate = Pattern.compile("(?i)update\\s+orders?\\b[^\"]*\\bset\\b[^\"]*\\bstatus\\b");
        for (Path file : javaFiles()) {
            String source = Files.readString(file);
            assertFalse(bulkUpdate.matcher(source).find(), file.getFileName() + " updates orders.status with a query");
        }
    }

    @Test
    void revenueQueries_useTheStatusParameter_notAPaidLiteral() throws IOException {
        for (String name : new String[] {"OrderRepository.java", "OrderItemRepository.java"}) {
            Path file = mainSources().resolve("com/example/backend/repository/" + name);
            String source = Files.readString(file);
            assertFalse(source.contains("status = 'PAID'"), name + " must not hard-code PAID");
            assertTrue(source.contains("status IN :statuses"), name + " must filter on the passed statuses");
        }
    }

    @Test
    void noJavaCodeStillReferencesTheRemovedLegacyStatuses() throws IOException {
        Pattern legacy = Pattern.compile("Order\\.Status|OrderStatus\\.(PENDING|CONFIRMED)\\b");
        for (Path file : javaFiles()) {
            for (String line : Files.readAllLines(file)) {
                assertFalse(!isCommentLine(line) && legacy.matcher(line).find(), file.getFileName() + ": " + line);
            }
        }
    }
}
