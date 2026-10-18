package com.example.backend.db;

import com.example.backend.dto.CartResponse;
import com.example.backend.dto.CheckoutRequest;
import com.example.backend.dto.ProductCreateRequest;
import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.repository.CategoryRepository;
import com.example.backend.repository.InventoryRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.CartService;
import com.example.backend.service.CheckoutService;
import com.example.backend.service.ProductService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B04 against a REAL PostgreSQL with the REAL Spring context: what no mock can prove - the cart row lock (two parallel
 * checkouts of one cart create ONE order), the atomic rollback of order + reservation + payment + cart clearing, the
 * ascending lock order (opposite carts do not deadlock) and the statement count of a cart read and of a checkout.
 *
 * <p>Needs ECOGREEN_TEST_PG_URL (optional ECOGREEN_TEST_PG_USER / ECOGREEN_TEST_PG_PASSWORD); otherwise skipped = NOT RUN.
 * WARNING: it boots the application against that database with ddl-auto=update and inserts / removes its own rows (names
 * prefixed with a random tag). Point it only at a scratch database. Apply V30, V31 (inventory) and V22 first for the full
 * constraint set; without them only the rules of the services are exercised.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties")
@EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
class CheckoutPostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECOGREEN_TEST_PG_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired private CheckoutService checkoutService;
    @Autowired private CartService cartService;
    @Autowired private ProductService productService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;

    private final String tag = "b04" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final List<Long> users = new ArrayList<>();
    private final List<Long> products = new ArrayList<>();
    private Category category;

    @AfterEach
    void cleanUp() {
        for (Long userId : users) {
            jdbc.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM orders WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM carts WHERE user_id = ?", userId);
        }
        for (Long productId : products) {
            jdbc.update("DELETE FROM order_items WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM inventory_transactions WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM inventory WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
        for (Long userId : users) {
            jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        if (category != null) {
            jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        }
    }

    // ---- builders ----

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private User newUser(String suffix) {
        return tx().execute(status -> {
            User user = new User();
            user.setUsername(tag + suffix);
            user.setEmail(tag + suffix + "@example.test");
            user.setPassword("$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttest"); // never used to log in
            user.setActive(true);
            user.setRoles(new HashSet<>(Set.of(roleRepository.findByName(Role.CUSTOMER).orElseThrow())));
            User saved = userRepository.save(user);
            users.add(saved.getId());
            return saved;
        });
    }

    private Long newProduct(String name, int stock, String price) {
        if (category == null) {
            category = new Category();
            category.setName(tag + "-category");
            category.setSlug(tag + "-category");
            category = categoryRepository.save(category);
        }
        Product product = productService.create(new ProductCreateRequest(tag + " " + name, "mô tả", new BigDecimal(price), null,
                stock, null, category.getId(), null, null, Product.Status.ACTIVE), null);
        products.add(product.getId());
        return product.getId();
    }

    /** Puts the products into the user's cart in the given order (line ids ascend in that order). */
    private Long fillCart(User user, int quantity, Long... productIds) {
        return tx().execute(status -> {
            Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
                Cart created = new Cart();
                created.setUser(user);
                return cartRepository.save(created);
            });
            for (Long productId : productIds) {
                CartItem item = new CartItem();
                item.setCart(cart);
                item.setProduct(productRepository.getReferenceById(productId));
                item.setQuantity(quantity);
                cartItemRepository.save(item);
            }
            return cart.getId();
        });
    }

    private static CheckoutRequest request() {
        return new CheckoutRequest(null, "Nguyễn Văn A", "0901234567", "1 Lê Lợi, Quận 1", null, "COD");
    }

    private int reserved(Long productId) {
        return inventoryRepository.findById(productId).orElseThrow().getReservedQuantity();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    // ---- the cart row lock ----

    @Test
    void checkout_concurrentSameCart_createsOneOrder() throws Exception {
        User user = newUser("c");
        Long product = newProduct("shared", 10, "100000");
        Long cartId = fillCart(user, 2, product);

        int threads = 4;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> attempt = () -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        checkoutService.checkout(user.getId(), request());
                        return true;
                    } catch (BusinessRuleViolationException emptyCart) {
                        return false; // the loser waited for the lock, then found the cart empty
                    }
                };
                results.add(pool.submit(attempt));
            }
            int placed = 0;
            for (Future<Boolean> result : results) {
                try {
                    if (result.get(60, TimeUnit.SECONDS)) placed++;
                } catch (ExecutionException e) {
                    throw new AssertionError("a parallel checkout failed with something other than 'empty cart'", e.getCause());
                }
            }
            assertEquals(1, placed, "exactly one of the parallel checkouts of the same cart succeeds");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, userRepository.countOrdersOf(user.getId()));
        assertEquals(2, reserved(product), "the stock was reserved once, not once per attempt");
        assertEquals(0, cartItemRepository.findByCartId(cartId).size());
        assertEquals(1, count("SELECT count(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.user_id = ?", user.getId()));
        assertEquals(1, count("SELECT count(*) FROM order_status_history h JOIN orders o ON o.id = h.order_id WHERE o.user_id = ?", user.getId()));
    }

    // ---- atomicity ----

    @Test
    void checkout_isOneTransaction_orderReservationPaymentAndCartClearingRollBackTogether() {
        User user = newUser("r");
        Long first = newProduct("first", 5, "100000");
        Long second = newProduct("second", 5, "50000");
        Long cartId = fillCart(user, 1, first, second);

        // run the whole checkout inside an outer transaction that is then rolled back: if every step belongs to the same
        // transaction (as required), nothing at all survives
        tx().executeWithoutResult(status -> {
            checkoutService.checkout(user.getId(), request());
            status.setRollbackOnly();
        });

        assertEquals(0, userRepository.countOrdersOf(user.getId()));
        assertEquals(0, reserved(first));
        assertEquals(0, reserved(second));
        assertEquals(2, cartItemRepository.findByCartId(cartId).size(), "the cart is kept");
        assertEquals(0, count("SELECT count(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.user_id = ?", user.getId()));
    }

    @Test
    void checkout_secondItemInsufficient_changesNothing_andKeepsTheCart() {
        User user = newUser("i");
        Long plenty = newProduct("plenty", 5, "100000");
        Long scarce = newProduct("scarce", 1, "50000");
        Long cartId = fillCart(user, 1, plenty);
        // the scarce product is asked for twice as often as it exists
        tx().executeWithoutResult(status -> {
            CartItem item = new CartItem();
            item.setCart(cartRepository.findById(cartId).orElseThrow());
            item.setProduct(productRepository.getReferenceById(scarce));
            item.setQuantity(2);
            cartItemRepository.save(item);
        });

        assertThrows(BusinessRuleViolationException.class,
                () -> checkoutService.checkout(user.getId(), request()));

        assertEquals(0, userRepository.countOrdersOf(user.getId()));
        assertEquals(0, reserved(plenty));
        assertEquals(0, reserved(scarce));
        assertEquals(2, cartItemRepository.findByCartId(cartId).size());
    }

    // ---- lock ordering ----

    @Test
    void checkout_oppositeCartOrders_doNotDeadlock() throws Exception {
        Long a = newProduct("a", 100, "10000");
        Long b = newProduct("b", 100, "10000");
        int rounds = 5;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                User first = newUser("d1-" + round);
                User second = newUser("d2-" + round);
                fillCart(first, 1, a, b);   // lines in the order a, b
                fillCart(second, 1, b, a);  // lines in the order b, a
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> one = pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return checkoutService.checkout(first.getId(), request());
                });
                Future<?> two = pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return checkoutService.checkout(second.getId(), request());
                });
                one.get(60, TimeUnit.SECONDS); // a deadlock would surface here as an ExecutionException / timeout
                two.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2 * rounds, reserved(a));
        assertEquals(2 * rounds, reserved(b));
    }

    // ---- statement counts (the efficiency evidence) ----

    @Test
    void cartRead_statementCount_doesNotGrowWithTheNumberOfLines() {
        User user = newUser("q");
        Long p1 = newProduct("q1", 10, "1000");
        fillCart(user, 1, p1);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        CartResponse small = cartService.getCart(user.getId());
        long smallStatements = stats.getPrepareStatementCount();

        Long[] more = new Long[9];
        for (int i = 0; i < more.length; i++) more[i] = newProduct("q" + (i + 2), 10, "1000");
        fillCart(user, 1, more);
        stats.clear();
        CartResponse large = cartService.getCart(user.getId());
        long largeStatements = stats.getPrepareStatementCount();

        assertEquals(1, small.items.size());
        assertEquals(10, large.items.size());
        assertEquals(smallStatements, largeStatements, "the statement count must not grow with the number of lines");
        assertTrue(largeStatements <= 3, "cart row + lines with products + one availability batch, got " + largeStatements);
        System.out.println("B04 cart read: " + largeStatements + " statements for 1 and for 10 lines");
    }

    @Test
    void checkout_statementCount_doesNotGrowWithTheNumberOfLines_forTheReadSteps() {
        User small = newUser("s1");
        User large = newUser("s2");
        Long first = newProduct("s1", 100, "1000");
        fillCart(small, 1, first);
        Long[] ids = new Long[8];
        for (int i = 0; i < ids.length; i++) ids[i] = newProduct("sl" + i, 100, "1000");
        fillCart(large, 1, ids);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        checkoutService.preview(small.getId(), null);
        long previewSmall = stats.getPrepareStatementCount();
        stats.clear();
        checkoutService.preview(large.getId(), null);
        long previewLarge = stats.getPrepareStatementCount();
        assertEquals(previewSmall, previewLarge, "preview: the statement count must not grow with the number of lines");

        stats.clear();
        checkoutService.checkout(large.getId(), request());
        long checkoutLarge = stats.getPrepareStatementCount();
        System.out.println("B04 preview: " + previewLarge + " statements; checkout of " + ids.length
                + " lines: " + checkoutLarge + " statements (includes one INSERT/UPDATE per line for the order and the ledger)");
    }
}
