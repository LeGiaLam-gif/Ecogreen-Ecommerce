package com.example.backend.service.inventory;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B03 tests that need a REAL PostgreSQL. They only run when ECOGREEN_TEST_PG_URL is set (plus optional
 * ECOGREEN_TEST_PG_USER / ECOGREEN_TEST_PG_PASSWORD); otherwise they are skipped and MUST be reported as NOT RUN. Each test
 * works in a random throw-away schema that it creates and drops, so it never touches existing tables.
 *
 * <p>What they prove and what they do not: the REAL migration scripts V30 and V31 are executed against a minimal copy of the
 * legacy tables, and the locking protocol of {@code InventoryService} (SELECT ... FOR UPDATE, check on the locked row,
 * update, one ledger row, commit) is replayed with plain JDBC against that real schema. They do NOT run the Java service
 * itself (that would need a Spring/JPA context); the service's rules are covered by InventoryServiceTest with mocks.
 */
class InventoryPostgresTest {

    private static final String ENV = "ECOGREEN_TEST_PG_URL";

    private static String url() { return System.getenv(ENV); }

    private static String user() { return System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres"); }

    private static String password() { return System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", ""); }

    // ------------------------------------------------------------------ fixture

    /** A throw-away schema with the legacy tables the migrations read, and the two migrations applied. */
    private static final class Schema implements AutoCloseable {
        final String name = "b03_" + UUID.randomUUID().toString().replace("-", "");

        Schema(boolean applyMigrations) throws Exception {
            try (Connection c = DriverManager.getConnection(url(), user(), password()); Statement st = c.createStatement()) {
                st.execute("CREATE SCHEMA " + name);
                st.execute("SET search_path TO " + name);
                st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
                st.execute("CREATE TABLE products (id BIGINT PRIMARY KEY, stock_quantity INT NOT NULL CHECK (stock_quantity >= 0))");
                st.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL)");
                st.execute("CREATE TABLE order_items (id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES orders(id), "
                        + "product_id BIGINT NOT NULL REFERENCES products(id), quantity INT NOT NULL CHECK (quantity > 0))");
                st.execute("CREATE TABLE order_status_history (id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL, "
                        + "old_status VARCHAR(20), new_status VARCHAR(20) NOT NULL)");
                if (applyMigrations) {
                    runScript(c, "/db/migration/V30__inventory.sql");
                    runScript(c, "/db/migration/V31__inventory_transactions.sql");
                }
            }
        }

        Connection connect() throws SQLException {
            Connection c = DriverManager.getConnection(url(), user(), password());
            try (Statement st = c.createStatement()) {
                st.execute("SET search_path TO " + name);
            }
            return c;
        }

        void exec(String... statements) throws SQLException {
            try (Connection c = connect(); Statement st = c.createStatement()) {
                for (String s : statements) {
                    st.execute(s);
                }
            }
        }

        long scalar(String sql) throws SQLException {
            try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        }

        @Override
        public void close() throws Exception {
            try (Connection c = DriverManager.getConnection(url(), user(), password()); Statement st = c.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS " + name + " CASCADE");
            }
        }
    }

    private static void runScript(Connection c, String resource) throws IOException, SQLException {
        try (InputStream in = InventoryPostgresTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("migration not found on the classpath: " + resource);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
        }
    }

    // ------------------------------------------------------------------ the protocol under test (mirrors InventoryService.reserve)

    /** SELECT ... FOR UPDATE, rule check on the locked values, update, one ledger row, commit. False = not enough stock. */
    private static boolean reserve(Connection c, long productId, int quantity, long orderId) throws SQLException {
        c.setAutoCommit(false);
        try {
            int onHand;
            int reserved;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT quantity_on_hand, reserved_quantity FROM inventory WHERE product_id = ? FOR UPDATE")) {
                ps.setLong(1, productId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    onHand = rs.getInt(1);
                    reserved = rs.getInt(2);
                }
            }
            if (onHand - reserved < quantity) {
                c.rollback();
                return false;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE inventory SET reserved_quantity = reserved_quantity + ? WHERE product_id = ?")) {
                ps.setInt(1, quantity);
                ps.setLong(2, productId);
                ps.executeUpdate();
            }
            insertLedger(c, productId, "RESERVE", quantity, onHand, reserved + quantity, orderId);
            c.commit();
            return true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        }
    }

    private static void insertLedger(Connection c, long productId, String type, int quantity, int onHandAfter,
                                     int reservedAfter, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO inventory_transactions (product_id, change_type, quantity, "
                + "on_hand_after, reserved_after, reference_type, reference_id) VALUES (?, ?, ?, ?, ?, 'ORDER', ?)")) {
            ps.setLong(1, productId);
            ps.setString(2, type);
            ps.setInt(3, quantity);
            ps.setInt(4, onHandAfter);
            ps.setInt(5, reservedAfter);
            ps.setLong(6, orderId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ migration

    @Test
    @EnabledIfEnvironmentVariable(named = ENV, matches = ".+")
    void migration_legacyPendingOrders_producesCorrectOnHandAndReserved() throws Exception {
        try (Schema s = new Schema(false)) {
            s.exec("INSERT INTO users VALUES (1)",
                    "INSERT INTO products VALUES (1, 10), (2, 0), (3, 5)",
                    "INSERT INTO orders VALUES (1, 'PENDING_PAYMENT'), (2, 'PENDING_PAYMENT'), (3, 'CANCELLED'), (4, 'PAID')",
                    "INSERT INTO order_items (order_id, product_id, quantity) VALUES (1,1,2), (2,1,3), (2,2,1), (3,3,4), (4,3,1)",
                    // order 3 was cancelled before B05: it only has the V21 backfill row (old_status NULL)
                    "INSERT INTO order_status_history (order_id, old_status, new_status) VALUES (3, NULL, 'CANCELLED')");

            try (Connection c = s.connect()) {
                runScript(c, "/db/migration/V30__inventory.sql");
                runScript(c, "/db/migration/V31__inventory_transactions.sql");
                // idempotent: running both again changes nothing
                runScript(c, "/db/migration/V30__inventory.sql");
                runScript(c, "/db/migration/V31__inventory_transactions.sql");
            }

            // product 1: stock 10 + reserved (2+3) = on_hand 15, reserved 5 | product 2: 0 + 1 | product 3: cancelled/paid need nothing
            assertEquals(15, s.scalar("SELECT quantity_on_hand FROM inventory WHERE product_id = 1"));
            assertEquals(5, s.scalar("SELECT reserved_quantity FROM inventory WHERE product_id = 1"));
            assertEquals(1, s.scalar("SELECT quantity_on_hand FROM inventory WHERE product_id = 2"));
            assertEquals(1, s.scalar("SELECT reserved_quantity FROM inventory WHERE product_id = 2"));
            assertEquals(5, s.scalar("SELECT quantity_on_hand FROM inventory WHERE product_id = 3"));
            assertEquals(0, s.scalar("SELECT reserved_quantity FROM inventory WHERE product_id = 3"));
            assertEquals(3, s.scalar("SELECT count(*) FROM inventory"));
            // the frozen legacy column is untouched
            assertEquals(10, s.scalar("SELECT stock_quantity FROM products WHERE id = 1"));

            // one opening row per product, not duplicated by the second run; reconciles through the *_after columns
            assertEquals(3, s.scalar("SELECT count(*) FROM inventory_transactions WHERE reason = 'Opening balance (V30)'"));
            assertEquals(0, s.scalar("SELECT count(*) FROM inventory i LEFT JOIN LATERAL (SELECT t.on_hand_after, t.reserved_after "
                    + "FROM inventory_transactions t WHERE t.product_id = i.product_id ORDER BY t.id DESC LIMIT 1) l ON TRUE "
                    + "WHERE l.on_hand_after IS DISTINCT FROM i.quantity_on_hand OR l.reserved_after IS DISTINCT FROM i.reserved_quantity"));

            // the cancelled-before-B05 diagnostic lists exactly order 3 and never changes stock
            assertEquals(1, s.scalar("SELECT count(*) FROM orders o JOIN order_items oi ON oi.order_id = o.id WHERE o.status = 'CANCELLED' "
                    + "AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = o.id AND h.old_status IS NOT NULL)"));

            // expected constraints and indexes exist (counts from the V30 / V31 header verification queries)
            assertEquals(5, s.scalar("SELECT count(*) FROM pg_constraint WHERE conrelid = 'inventory'::regclass"));
            assertEquals(7, s.scalar("SELECT count(*) FROM pg_constraint WHERE conrelid = 'inventory_transactions'::regclass"));
            assertEquals(3, s.scalar("SELECT count(*) FROM pg_indexes WHERE schemaname = '" + s.name + "' "
                    + "AND tablename = 'inventory_transactions'"));
            assertEquals(1, s.scalar("SELECT count(*) FROM pg_indexes WHERE schemaname = '" + s.name + "' "
                    + "AND indexname = 'uq_inventory_tx_idempotency' AND indexdef LIKE 'CREATE UNIQUE INDEX%' AND indexdef LIKE '%WHERE%'"));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = ENV, matches = ".+")
    void migration_whenTableWasCreatedByDdlAutoWithoutConstraints_v30AddsThem() throws Exception {
        try (Schema s = new Schema(false)) {
            s.exec("INSERT INTO products VALUES (1, 4)",
                    // what ddl-auto=update would create: no defaults, no CHECKs, no foreign key
                    "CREATE TABLE inventory (product_id BIGINT NOT NULL PRIMARY KEY, quantity_on_hand INT NOT NULL, "
                            + "reserved_quantity INT NOT NULL, updated_at TIMESTAMP NOT NULL)");
            try (Connection c = s.connect()) {
                runScript(c, "/db/migration/V30__inventory.sql");
            }
            assertEquals(4, s.scalar("SELECT quantity_on_hand FROM inventory WHERE product_id = 1"));
            assertEquals(5, s.scalar("SELECT count(*) FROM pg_constraint WHERE conrelid = 'inventory'::regclass"));
        }
    }

    // ------------------------------------------------------------------ constraints and idempotency backstop

    @Test
    @EnabledIfEnvironmentVariable(named = ENV, matches = ".+")
    void inventory_checksRejectNegativeAndOverReserved_andLedgerRejectsADuplicateReference() throws Exception {
        try (Schema s = new Schema(true)) {
            s.exec("INSERT INTO products VALUES (1, 0)", "INSERT INTO inventory (product_id, quantity_on_hand, reserved_quantity) VALUES (1, 5, 2)");

            assertThrows(SQLException.class, () -> s.exec("UPDATE inventory SET quantity_on_hand = -1 WHERE product_id = 1"));
            assertThrows(SQLException.class, () -> s.exec("UPDATE inventory SET reserved_quantity = 6 WHERE product_id = 1"));
            assertThrows(SQLException.class, () -> s.exec("UPDATE inventory SET reserved_quantity = -1 WHERE product_id = 1"));

            s.exec("INSERT INTO inventory_transactions (product_id, change_type, quantity, on_hand_after, reserved_after, reference_type, reference_id) "
                    + "VALUES (1, 'RESERVE', 1, 5, 3, 'ORDER', 9)");
            // same (product, type, reference) twice: the unique partial index is the backstop of the idempotency rule
            SQLException dup = assertThrows(SQLException.class, () -> s.exec(
                    "INSERT INTO inventory_transactions (product_id, change_type, quantity, on_hand_after, reserved_after, reference_type, reference_id) "
                            + "VALUES (1, 'RESERVE', 1, 5, 3, 'ORDER', 9)"));
            assertEquals("23505", dup.getSQLState());
            // ADJUST rows and rows without a reference are NOT limited by the index
            s.exec("INSERT INTO inventory_transactions (product_id, change_type, quantity, on_hand_after, reserved_after, reason) "
                            + "VALUES (1, 'ADJUST', -1, 4, 2, 'a'), (1, 'ADJUST', -1, 3, 2, 'a'), (1, 'RESTOCK', 1, 4, 2, 'Initial stock'), (1, 'RESTOCK', 1, 5, 2, 'Initial stock')");
            // RESTOCK of 0 and a half-set reference are rejected by the ledger CHECKs
            assertThrows(SQLException.class, () -> s.exec("INSERT INTO inventory_transactions (product_id, change_type, quantity, on_hand_after, reserved_after) VALUES (1, 'RESTOCK', 0, 5, 2)"));
            assertThrows(SQLException.class, () -> s.exec("INSERT INTO inventory_transactions (product_id, change_type, quantity, on_hand_after, reserved_after, reference_type) VALUES (1, 'RELEASE', 1, 5, 2, 'ORDER')"));
            // the ledger is an audit trail: a product with ledger rows cannot be hard-deleted (RESTRICT)
            assertThrows(SQLException.class, () -> s.exec("DELETE FROM products WHERE id = 1"));
        }
    }

    // ------------------------------------------------------------------ concurrency

    private static final int CONTENDERS = 8;

    @RepeatedTest(5)
    @EnabledIfEnvironmentVariable(named = ENV, matches = ".+")
    void reserve_concurrent_lastUnit_exactlyOneSucceeds() throws Exception {
        try (Schema s = new Schema(true)) {
            s.exec("INSERT INTO products VALUES (1, 0)", "INSERT INTO inventory (product_id, quantity_on_hand, reserved_quantity) VALUES (1, 1, 0)");

            ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
            CyclicBarrier start = new CyclicBarrier(CONTENDERS);
            int winners = 0;
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < CONTENDERS; i++) {
                    final long orderId = 1000 + i;
                    results.add(pool.submit(() -> {
                        try (Connection c = s.connect()) {
                            start.await();
                            return reserve(c, 1L, 1, orderId);
                        }
                    }));
                }
                for (Future<Boolean> f : results) {
                    if (f.get(30, TimeUnit.SECONDS)) {
                        winners++;
                    }
                }
            } finally {
                pool.shutdownNow();
            }

            assertEquals(1, winners, "exactly one of the concurrent reservations may take the last unit");
            assertEquals(1, s.scalar("SELECT reserved_quantity FROM inventory WHERE product_id = 1"));
            assertEquals(0, s.scalar("SELECT quantity_on_hand - reserved_quantity FROM inventory WHERE product_id = 1"), "never oversold");
            assertEquals(1, s.scalar("SELECT count(*) FROM inventory_transactions WHERE change_type = 'RESERVE'"));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = ENV, matches = ".+")
    void reserve_twoOrdersOppositeItemOrder_noDeadlock_whenLockedInAscendingProductIdOrder() throws Exception {
        try (Schema s = new Schema(true)) {
            seedTwoProducts(s);
            List<Throwable> failures = runTwoOpposedOrders(s, true, false);

            assertTrue(failures.isEmpty(), "ascending lock order must not deadlock: " + failures);
            assertEquals(2, s.scalar("SELECT reserved_quantity FROM inventory WHERE product_id = 1"));
            assertEquals(2, s.scalar("SELECT reserved_quantity FROM inventory WHERE product_id = 2"));
        }
    }

    /** Control: the same two orders locked in their OWN order do deadlock, so the test above could have caught a regression. */
    @Test
    @EnabledIfEnvironmentVariable(named = ENV, matches = ".+")
    void reserve_twoOrdersOppositeItemOrder_controlWithoutSorting_deadlocks() throws Exception {
        try (Schema s = new Schema(true)) {
            seedTwoProducts(s);
            List<Throwable> failures = runTwoOpposedOrders(s, false, true);

            assertFalse(failures.isEmpty(), "unsorted opposite orders are expected to deadlock (control)");
            assertTrue(failures.stream().anyMatch(t -> t instanceof SQLException && "40P01".equals(((SQLException) t).getSQLState())),
                    "the failure must be PostgreSQL's deadlock_detected (40P01): " + failures);
        }
    }

    private static void seedTwoProducts(Schema s) throws SQLException {
        s.exec("INSERT INTO products VALUES (1, 0), (2, 0)",
                "INSERT INTO inventory (product_id, quantity_on_hand, reserved_quantity) VALUES (1, 10, 0), (2, 10, 0)");
    }

    /** Order A lists products [1, 2], order B lists [2, 1]; returns what failed. */
    private static List<Throwable> runTwoOpposedOrders(Schema s, boolean sortAscending, boolean syncAfterFirstLock) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier bothHoldFirstLock = new CyclicBarrier(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> placeOrder(s, List.of(1L, 2L), 501, sortAscending, syncAfterFirstLock, bothHoldFirstLock)));
            futures.add(pool.submit(() -> placeOrder(s, List.of(2L, 1L), 502, sortAscending, syncAfterFirstLock, bothHoldFirstLock)));
            for (Future<?> f : futures) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    /** One order = several items in ONE transaction, each item locked and reserved like InventoryService.reserve does. */
    private static Void placeOrder(Schema s, List<Long> items, long orderId, boolean sortAscending, boolean syncAfterFirstLock,
                                   CyclicBarrier barrier) throws Exception {
        List<Long> ordered = new ArrayList<>(items);
        if (sortAscending) {
            Collections.sort(ordered); // the rule OrderService applies before calling the gateway
        }
        try (Connection c = s.connect()) {
            c.setAutoCommit(false);
            try {
                for (int i = 0; i < ordered.size(); i++) {
                    long productId = ordered.get(i);
                    int onHand;
                    int reserved;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT quantity_on_hand, reserved_quantity FROM inventory WHERE product_id = ? FOR UPDATE")) {
                        ps.setLong(1, productId);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            onHand = rs.getInt(1);
                            reserved = rs.getInt(2);
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE inventory SET reserved_quantity = reserved_quantity + 1 WHERE product_id = ?")) {
                        ps.setLong(1, productId);
                        ps.executeUpdate();
                    }
                    insertLedger(c, productId, "RESERVE", 1, onHand, reserved + 1, orderId);
                    if (i == 0) {
                        if (syncAfterFirstLock) {
                            barrier.await(20, TimeUnit.SECONDS); // both hold their first lock before asking for the second
                        } else {
                            Thread.sleep(300); // widen the overlap window
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
        return null;
    }
}
