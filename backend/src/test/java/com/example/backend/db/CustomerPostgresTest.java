package com.example.backend.db;

import com.example.backend.dto.AddressRequest;
import com.example.backend.dto.AdminUserResponse;
import com.example.backend.entity.Address;
import com.example.backend.entity.RefreshToken;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.repository.AddressRepository;
import com.example.backend.repository.RefreshTokenRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AddressService;
import com.example.backend.service.UserService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B08 against a REAL PostgreSQL with the REAL Spring context: the HQL / native queries no mock can check (paged id search,
 * IN load of roles, row locks, the default-clearing UPDATE, the refresh-token revocation) and the "list query count does not
 * depend on the number of rows" requirement. Needs ECOGREEN_TEST_PG_URL (optional ECOGREEN_TEST_PG_USER /
 * ECOGREEN_TEST_PG_PASSWORD); otherwise skipped = NOT RUN.
 * WARNING: it boots the application against that database with ddl-auto=update and inserts / removes its own rows (names
 * prefixed with a random tag). Point it only at a scratch database. If V70 was not applied, Hibernate creates the addresses
 * table without the foreign key and the partial unique index, so only the rules of the SERVICE are exercised then.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties")
@EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
class CustomerPostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECOGREEN_TEST_PG_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired private UserService userService;
    @Autowired private AddressService addressService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;

    private final String tag = "b08" + UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> createdUsers = new ArrayList<>();

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private User newUser(String suffix, boolean active, String roleName) {
        return tx().execute(status -> {
            User u = new User();
            u.setUsername(tag + suffix);
            u.setEmail(tag + suffix + "@example.test");
            u.setPassword("$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttest"); // not a real hash, never used to log in
            u.setActive(active);
            Role role = roleRepository.findByName(roleName).orElseThrow();
            u.setRoles(new HashSet<>(Set.of(role)));
            User saved = userRepository.save(u);
            createdUsers.add(saved.getId());
            return saved;
        });
    }

    private static AddressRequest request(String line1, Boolean isDefault) {
        return new AddressRequest("Nguyễn Văn A", "0901234567", line1, "Bến Nghé", "Quận 1", "TP. Hồ Chí Minh", isDefault);
    }

    @AfterEach
    void cleanUp() {
        tx().executeWithoutResult(status -> {
            for (Long id : createdUsers) {
                addressRepository.deleteAllByUser(id);
                refreshTokenRepository.findAll().stream().filter(t -> id.equals(t.getUserId())).forEach(refreshTokenRepository::delete);
                userRepository.findById(id).ifPresent(userRepository::delete);
            }
        });
    }

    @Test
    void addressBook_realQueries_keepExactlyOneDefault() {
        User owner = newUser("a", true, Role.CUSTOMER);
        long id = owner.getId();

        Address first = addressService.create(id, request("1", null));
        Address second = addressService.create(id, request("2", null));
        Address third = addressService.create(id, request("3", true));

        assertTrue(addressRepository.findById(third.getId()).orElseThrow().isDefault());
        assertEquals(1, addressRepository.findAllByUser(id).stream().filter(Address::isDefault).count());

        addressService.setDefault(id, first.getId());
        List<Address> book = addressRepository.findAllByUser(id);
        assertEquals(first.getId(), book.get(0).getId(), "the default is listed first");
        assertEquals(1, book.stream().filter(Address::isDefault).count());

        addressService.delete(id, first.getId()); // deleting the default promotes the most recent remaining
        assertTrue(addressRepository.findById(third.getId()).orElseThrow().isDefault());
        assertFalse(addressRepository.findById(second.getId()).orElseThrow().isDefault());
        assertEquals(1, addressRepository.findAllByUser(id).stream().filter(Address::isDefault).count());
    }

    @Test
    void lockQueries_runAgainstPostgres() {
        User admin = newUser("adm", true, Role.ADMIN);
        newUser("cus", true, Role.CUSTOMER);

        List<Long> locked = tx().execute(status -> userRepository.lockActiveIdsByRole(Role.ADMIN));
        assertTrue(locked.contains(admin.getId()));
        assertTrue(tx().execute(status -> userRepository.lockRowById(admin.getId())).isPresent());
        assertTrue(tx().execute(status -> userRepository.lockRowById(-1L)).isEmpty());
    }

    @Test
    void disable_revokesAllRefreshTokens_andDisabledUserIsNotActive() {
        User customer = newUser("dis", true, Role.CUSTOMER);
        for (String family : new String[] {"f1", "f1", "f2"}) {
            RefreshToken t = new RefreshToken();
            t.setUserId(customer.getId());
            t.setTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
            t.setFamilyId(tag + family);
            t.setExpiresAt(LocalDateTime.now().plusDays(1));
            t.setCreatedAt(LocalDateTime.now());
            refreshTokenRepository.save(t);
        }

        userService.disable(-1L, customer.getId());

        assertFalse(userRepository.existsByIdAndActiveTrue(customer.getId()));
        List<RefreshToken> tokens = refreshTokenRepository.findAll().stream().filter(t -> customer.getId().equals(t.getUserId())).toList();
        assertEquals(3, tokens.size());
        assertTrue(tokens.stream().allMatch(RefreshToken::isRevoked));
    }

    @Test
    void userList_queryCountIndependentOfRows() {
        for (int i = 0; i < 3; i++) newUser("s" + i, true, i == 0 ? Role.MANAGER : Role.CUSTOMER);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        Page<AdminUserResponse> small = userService.list(tag, null, null, PageRequest.of(0, 20));
        long smallStatements = stats.getPrepareStatementCount();

        for (int i = 3; i < 15; i++) newUser("s" + i, true, Role.CUSTOMER);
        stats.clear();
        Page<AdminUserResponse> large = userService.list(tag, null, null, PageRequest.of(0, 20));
        long largeStatements = stats.getPrepareStatementCount();

        assertEquals(3, small.getContent().size());
        assertEquals(15, large.getContent().size());
        assertEquals(smallStatements, largeStatements, "the statement count must not grow with the number of rows");
    }

    @Test
    void userList_filtersByKeywordRoleAndActive() {
        newUser("x1", true, Role.CUSTOMER);
        newUser("x2", false, Role.CUSTOMER);
        newUser("x3", true, Role.MANAGER);

        assertEquals(3, userService.list(tag, null, null, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(2, userService.list(tag, null, Role.CUSTOMER, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(1, userService.list(tag, false, null, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(1, userService.list(tag, true, Role.MANAGER, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(0, userService.list(tag + "%zz", null, null, PageRequest.of(0, 20)).getTotalElements(), "wildcards are literal");
    }
}
