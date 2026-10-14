package com.example.backend.repository;

import com.example.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSub(String googleSub);

    /** Cheap per-request check used by AuthInterceptor: no entity, no roles loaded. */
    boolean existsByIdAndActiveTrue(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * "New customers" for the sales dashboard summary (User.createdAt exists - see
     * entity).
     */
    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    // ------------------------------------------------------------------------------------------------------------
    // B08 - admin user management
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Step 1 of the admin list: ONE page of user ids (plus one count query). No collection is fetched here, so the page
     * limit is applied by the database. {@code pattern} is a lower-cased LIKE pattern that is never null ("%" = no
     * filter) and {@code filterActive} / {@code filterRole} switch the optional filters on, so no parameter is ever null
     * (PostgreSQL cannot infer the type of a null parameter inside lower() / comparisons).
     */
    @Query(value = "SELECT u.id FROM User u WHERE "
            + "(lower(u.username) LIKE :pattern ESCAPE '!' OR lower(u.email) LIKE :pattern ESCAPE '!') "
            + "AND (:filterActive = false OR u.active = :active) "
            + "AND (:filterRole = false OR EXISTS (SELECT 1 FROM u.roles r WHERE r.name = :role))",
            countQuery = "SELECT count(u) FROM User u WHERE "
            + "(lower(u.username) LIKE :pattern ESCAPE '!' OR lower(u.email) LIKE :pattern ESCAPE '!') "
            + "AND (:filterActive = false OR u.active = :active) "
            + "AND (:filterRole = false OR EXISTS (SELECT 1 FROM u.roles r WHERE r.name = :role))")
    Page<Long> searchIds(@Param("pattern") String pattern,
                         @Param("filterActive") boolean filterActive, @Param("active") boolean active,
                         @Param("filterRole") boolean filterRole, @Param("role") String role,
                         Pageable pageable);

    /** Step 2 of the admin list: the users of one page with their roles in ONE statement (IN, no Pageable). */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN :ids")
    List<User> findAllWithRolesByIdIn(@Param("ids") Collection<Long> ids);

    /** One user with roles, for the admin detail view. */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findWithRolesById(@Param("id") Long id);

    /**
     * Locks the ids of every ACTIVE user that holds {@code roleName}, in id order, for the rest of the transaction. Every
     * operation that can reduce the number of active admins (disable, delete, role change) calls this first, so two
     * concurrent operations cannot both see "another admin exists" and remove the last two. Native SQL because HQL cannot
     * express a row lock on a projection.
     */
    @Query(value = "SELECT u.id FROM users u WHERE u.is_active = true AND u.id IN "
            + "(SELECT ur.user_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.name = :roleName) "
            + "ORDER BY u.id FOR UPDATE", nativeQuery = true)
    List<Long> lockActiveIdsByRole(@Param("roleName") String roleName);

    /** Locks one user row until the end of the transaction (serialises address-book changes of one user). */
    @Query(value = "SELECT u.id FROM users u WHERE u.id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockRowById(@Param("id") Long id);

    @Query("SELECT count(o) FROM Order o WHERE o.userId = :userId")
    long countOrdersOf(@Param("userId") Long userId);

    @Query("SELECT count(r) FROM ReturnRequest r WHERE r.user.id = :userId")
    long countReturnRequestsOf(@Param("userId") Long userId);
}
