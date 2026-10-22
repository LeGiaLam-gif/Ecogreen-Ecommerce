package com.example.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B10 executor: bounded, and a full queue must never push the work back onto the business thread. */
class NotificationAsyncConfigTest {

    @Test
    void executor_rejectionPolicy_isNotCallerRuns() {
        ThreadPoolTaskExecutor executor = new NotificationAsyncConfig().notificationExecutor();
        executor.initialize();
        try {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            RejectedExecutionHandler handler = pool.getRejectedExecutionHandler();

            assertFalse(handler instanceof ThreadPoolExecutor.CallerRunsPolicy, "CallerRunsPolicy would block the request thread");
            assertFalse(handler instanceof ThreadPoolExecutor.AbortPolicy, "AbortPolicy would throw into the business flow");
            assertEquals(NotificationAsyncConfig.CORE_POOL_SIZE, pool.getCorePoolSize());
            assertEquals(NotificationAsyncConfig.MAX_POOL_SIZE, pool.getMaximumPoolSize());
            assertEquals(NotificationAsyncConfig.QUEUE_CAPACITY, pool.getQueue().remainingCapacity(), "the queue is bounded");
            assertEquals("notificationExecutor", NotificationAsyncConfig.EXECUTOR);
            assertTrue(executor.getThreadNamePrefix().startsWith("notification-"));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void aFullQueue_discardsTheTask_withoutThrowingAndWithoutRunningItOnTheCaller() throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                NotificationAsyncConfig.discardAndLog());
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ranOnCaller = new AtomicBoolean(false);
        Thread caller = Thread.currentThread();
        Runnable blocker = () -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            pool.execute(blocker);   // runs on the only thread
            pool.execute(blocker);   // fills the only queue slot

            assertDoesNotThrow(() -> pool.execute(() -> {
                if (Thread.currentThread() == caller) {
                    ranOnCaller.set(true);
                }
            }));

            assertFalse(ranOnCaller.get());
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    @Test
    void enableAsync_isDeclaredInExactlyOnePlace() throws IOException {
        Pattern annotation = Pattern.compile("(?m)^\\s*@EnableAsync\\b");
        List<Path> declaring;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            declaring = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return annotation.matcher(Files.readString(p)).find();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        }

        assertEquals(1, declaring.size(), "@EnableAsync must be switched on in one place only: " + declaring);
        assertTrue(declaring.get(0).endsWith("NotificationAsyncConfig.java"));
    }
}
