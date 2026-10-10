package com.example.backend.service.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Needs a REAL PostgreSQL, so it only runs when ECOGREEN_TEST_PG_URL is set (plus optional ECOGREEN_TEST_PG_USER /
 * ECOGREEN_TEST_PG_PASSWORD); otherwise it is skipped and must be reported as NOT RUN. It works in a random throw-away
 * schema it creates and drops.
 *
 * <p>legacyAdapter_concurrentReserveLastItem_exactlyOneSucceeds: the statement behind
 * ProductRepository.decreaseStockIfAvailable ("UPDATE ... SET stock = stock - q WHERE id = ? AND stock >= q") is run by
 * several connections at once against a product with exactly one unit left. It checks the SQL pattern the repository
 * relies on (it does not execute the JPQL itself): exactly one connection updates a row, the others update zero rows, and
 * the stock ends at 0, never negative.
 */
class LegacyStockAtomicUpdatePgTest {

    private static final int CONTENDERS = 8;

    @Test
    @EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
    void legacyAdapter_concurrentReserveLastItem_exactlyOneSucceeds() throws Exception {
        String schema = "b05_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("ECOGREEN_TEST_PG_URL");
        String user = System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres");
        String password = System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", "");

        try (Connection setup = DriverManager.getConnection(url, user, password); Statement st = setup.createStatement()) {
            try {
                st.execute("CREATE SCHEMA " + schema);
                st.execute("CREATE TABLE " + schema + ".products (id BIGINT PRIMARY KEY, "
                        + "stock_quantity INT NOT NULL CHECK (stock_quantity >= 0))");
                st.execute("INSERT INTO " + schema + ".products VALUES (1, 1)");

                ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
                CyclicBarrier start = new CyclicBarrier(CONTENDERS);
                int winners = 0;
                try {
                    java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
                    for (int i = 0; i < CONTENDERS; i++) {
                        results.add(pool.submit(() -> {
                            try (Connection c = DriverManager.getConnection(url, user, password)) {
                                c.setAutoCommit(false);
                                try (PreparedStatement ps = c.prepareStatement("UPDATE " + schema + ".products "
                                        + "SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?")) {
                                    ps.setInt(1, 1);
                                    ps.setLong(2, 1L);
                                    ps.setInt(3, 1);
                                    start.await();
                                    int updated = ps.executeUpdate();
                                    c.commit();
                                    return updated;
                                }
                            }
                        }));
                    }
                    for (Future<Integer> result : results) {
                        winners += result.get();
                    }
                } finally {
                    pool.shutdownNow();
                }

                assertEquals(1, winners, "exactly one of the concurrent reservations may take the last unit");
                try (ResultSet rs = st.executeQuery("SELECT stock_quantity FROM " + schema + ".products WHERE id = 1")) {
                    rs.next();
                    assertEquals(0, rs.getInt(1), "stock ends at 0, never negative");
                }
            } finally {
                st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
