package com.example.backend.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs V10, V11, V12 (twice) against a REAL PostgreSQL inside a throw-away schema. It needs a database, so it only runs
 * when ECOGREEN_TEST_PG_URL is set (optional ECOGREEN_TEST_PG_USER / ECOGREEN_TEST_PG_PASSWORD); otherwise it is skipped
 * and must be reported as NOT RUN. It creates and drops only its own random schema.
 */
class CatalogMigrationPostgresTest {

    private static String script(String name) throws Exception {
        try (InputStream in = CatalogMigrationPostgresTest.class.getResourceAsStream("/db/migration/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long count(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String text(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
    void catalogMigrations_backfillKeepLegacyData_andAreIdempotent() throws Exception {
        String schema = "b02_" + UUID.randomUUID().toString().replace("-", "");
        String user = System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres");
        String password = System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", "");
        try (Connection c = DriverManager.getConnection(System.getenv("ECOGREEN_TEST_PG_URL"), user, password);
             Statement st = c.createStatement()) {
            try {
                st.execute("CREATE SCHEMA " + schema);
                st.execute("SET search_path TO " + schema);
                st.execute("CREATE TABLE categories (id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, description TEXT)");
                st.execute("CREATE TABLE products (id BIGSERIAL PRIMARY KEY, category_id BIGINT NOT NULL REFERENCES categories(id), "
                        + "name VARCHAR(150) NOT NULL, price DECIMAL(12,2) NOT NULL, stock_quantity INT NOT NULL DEFAULT 0, "
                        + "image VARCHAR(255), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE','INACTIVE')))");
                // a second, Hibernate-style status CHECK that must also be dropped
                st.execute("ALTER TABLE products ADD CONSTRAINT products_status_check CHECK (status IN ('ACTIVE','INACTIVE'))");
                st.execute("INSERT INTO categories(name) VALUES ('Nhà bếp'), ('Quà tặng')");
                st.execute("INSERT INTO products(category_id,name,price,stock_quantity,image,status) VALUES "
                        + "(1,'Đĩa giấy',10000,5,'plate.jpg','ACTIVE'),(1,'Cốc tre',20000,0,'https://x.test/a.jpg','INACTIVE'),"
                        + "(2,'Túi giấy',5000,9,NULL,'ACTIVE'),(2,'Hộp quà',7000,1,'   ','ACTIVE')");

                for (int pass = 1; pass <= 2; pass++) { // second pass proves idempotency
                    st.execute(script("V10__products_extend.sql"));
                    st.execute(script("V11__product_images.sql"));
                    st.execute(script("V12__categories_tree.sql"));
                }

                assertEquals(4, count(st, "SELECT count(*) FROM products WHERE slug = 'product-' || id"));
                assertEquals(2, count(st, "SELECT count(*) FROM categories WHERE slug = 'category-' || id AND parent_id IS NULL"));
                assertEquals(2, count(st, "SELECT count(*) FROM product_images"));            // blank/NULL legacy images are not copied
                assertEquals("plate.jpg", text(st, "SELECT image FROM products WHERE id = 1")); // legacy column untouched
                assertEquals(1, count(st, "SELECT count(*) FROM pg_constraint WHERE conrelid = 'products'::regclass "
                        + "AND contype = 'c' AND pg_get_constraintdef(oid) ILIKE '%status%'"));
                st.execute("UPDATE products SET status = 'DRAFT' WHERE id = 3");
                st.execute("UPDATE products SET status = 'ARCHIVED' WHERE id = 2");
                assertThrows(SQLException.class, () -> st.execute("UPDATE products SET status = 'BOGUS' WHERE id = 1"));
                assertThrows(SQLException.class, () -> st.execute("UPDATE products SET compare_price = 1 WHERE id = 1"));
                assertThrows(SQLException.class,
                        () -> st.execute("INSERT INTO products(category_id,name,price,slug) VALUES (1,'x',1,'product-1')"));
                assertThrows(SQLException.class, () -> st.execute("DELETE FROM categories WHERE id = 1")); // has products
            } finally {
                st.execute("SET search_path TO public");
                st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
