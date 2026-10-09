package com.example.backend.repository;

import com.example.backend.dto.ProductSearchCriteria;
import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.service.ProductService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-PostgreSQL checks of the catalogue search: filter semantics and the SQL statement count of a list page.
 * Needs ECOGREEN_TEST_PG_URL (optional ECOGREEN_TEST_PG_USER / ECOGREEN_TEST_PG_PASSWORD); otherwise skipped = NOT RUN.
 * WARNING: it boots the application against that database with ddl-auto=update and inserts/removes its own rows
 * (names prefixed with a random tag). Point it only at a scratch database, never at data you care about.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties")
@EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
class CatalogSearchPostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECOGREEN_TEST_PG_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductImageRepository productImageRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private final String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final List<Product> created = new ArrayList<>();
    private Category category;

    @AfterEach
    void cleanUp() {
        for (Product p : created) {
            productImageRepository.deleteAll(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(p.getId()));
            productRepository.deleteById(p.getId());
        }
        if (category != null) categoryRepository.deleteById(category.getId());
    }

    private Product product(String name, String price, int stock, Product.Status status) {
        if (category == null) {
            category = new Category();
            category.setName(tag + "-category");
            category.setSlug(tag + "-category");
            category = categoryRepository.save(category);
        }
        Product p = new Product();
        p.setName(tag + " " + name);
        p.setSlug(tag + "-" + created.size());
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        p.setCategory(category);
        p.setStatus(status);
        p = productRepository.save(p);
        created.add(p);
        return p;
    }

    private ProductSearchCriteria tagged(BigDecimal min, BigDecimal max, Boolean inStock, String extraKeyword) {
        return new ProductSearchCriteria(extraKeyword == null ? tag : tag + " " + extraKeyword, null, min, max, inStock, null);
    }

    @Test
    void search_priceRange_returnsOnlyMatching() {
        product("cheap", "50000", 1, Product.Status.ACTIVE);
        Product mid = product("mid", "150000", 1, Product.Status.ACTIVE);
        product("dear", "900000", 1, Product.Status.ACTIVE);

        Page<Product> page = productService.search(
                tagged(new BigDecimal("100000"), new BigDecimal("200000"), null, null), ProductService.toPageable(0, 12, null));

        assertEquals(List.of(mid.getId()), page.getContent().stream().map(Product::getId).toList());
    }

    @Test
    void search_keywordWithPercentAndUnderscore_isLiteral() {
        Product literal = product("sale 50%_off", "10000", 1, Product.Status.ACTIVE);
        product("sale 500 xoff", "10000", 1, Product.Status.ACTIVE);

        Page<Product> page = productService.search(tagged(null, null, null, "50%_off"), ProductService.toPageable(0, 12, null));

        assertEquals(List.of(literal.getId()), page.getContent().stream().map(Product::getId).toList());
    }

    @Test
    void publicList_neverReturnsDraftInactiveArchived_andInStockFilterWorks() {
        Product active = product("active", "10000", 3, Product.Status.ACTIVE);
        product("empty", "10000", 0, Product.Status.ACTIVE);
        product("draft", "10000", 3, Product.Status.DRAFT);
        product("inactive", "10000", 3, Product.Status.INACTIVE);
        product("archived", "10000", 3, Product.Status.ARCHIVED);

        Page<Product> all = productService.search(tagged(null, null, null, null), ProductService.toPageable(0, 50, null));
        Page<Product> inStock = productService.search(tagged(null, null, true, null), ProductService.toPageable(0, 50, null));

        assertEquals(2, all.getTotalElements());
        assertEquals(List.of(active.getId()), inStock.getContent().stream().map(Product::getId).toList());
    }

    @Test
    void listProducts_queryCount_doesNotGrowWithRows() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (int i = 0; i < 12; i++) {
            Product p = product("row" + i, "10000", 1, Product.Status.ACTIVE);
            ProductImage image = new ProductImage();
            image.setProduct(p);
            image.setImageUrl("img" + i + ".jpg");
            productImageRepository.save(image);
        }

        long few = statementsForPage(stats, 3);
        long many = statementsForPage(stats, 12);

        // page query + count query + ONE images IN query, whatever the number of rows on the page
        assertEquals(few, many);
        assertTrue(many <= 3, "statements for a 12-item list: " + many);
    }

    private long statementsForPage(Statistics stats, int size) {
        stats.clear();
        Page<Product> page = productService.search(tagged(null, null, null, null), ProductService.toPageable(0, size, null));
        Map<Long, List<ProductImage>> images = productService.loadImages(page.getContent());
        page.getContent().forEach(p -> {
            p.getCategory().getName();           // would be an N+1 if the category were not fetched with the page
            images.get(p.getId());
        });
        return stats.getPrepareStatementCount();
    }
}
