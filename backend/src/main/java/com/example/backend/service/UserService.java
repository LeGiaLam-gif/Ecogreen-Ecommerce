package com.example.backend.service;

import com.example.backend.dto.AdminUserResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.AddressRepository;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin user management (B08): paged search, detail, enable / disable, role changes and the guarded delete.
 *
 * <p>Safety rules (all enforced here, never in the browser):
 * <ul>
 *   <li>an admin cannot disable, delete or change the roles of THEMSELVES (409 CONFLICT);</li>
 *   <li>the last ACTIVE {@code ADMIN} can never be disabled, deleted or demoted (409 CONFLICT). The active admins are
 *       locked ({@code FOR UPDATE}) before the check, so two concurrent requests cannot remove the last two;</li>
 *   <li>only an {@code ADMIN} may grant or remove the {@code ADMIN} role (403): {@code user:update} alone is held by
 *       {@code MANAGER}, and without this rule a manager could make themselves an admin;</li>
 *   <li>a user with orders or return requests cannot be deleted (409, "disable the account instead").</li>
 * </ul>
 * Disabling sets {@code active = false} and revokes every refresh token of the user; the access token stops working on the
 * next API call because AuthInterceptor rejects inactive users (B01-P2).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** The only role names an admin may assign (the three roles seeded by DataLoader / V5). */
    public static final Set<String> ASSIGNABLE_ROLES = Set.of(Role.CUSTOMER, Role.MANAGER, Role.ADMIN);

    static final int MAX_KEYWORD_LENGTH = 100;
    static final String SELF_DISABLE = "Bạn không thể tự khóa tài khoản của chính mình.";
    static final String SELF_DELETE = "Bạn không thể tự xóa tài khoản của chính mình.";
    static final String SELF_ROLES = "Bạn không thể tự thay đổi vai trò của chính mình.";
    static final String LAST_ADMIN = "Không thể thực hiện với tài khoản Quản trị viên đang hoạt động cuối cùng.";
    static final String ADMIN_ROLE_ADMIN_ONLY = "Chỉ Quản trị viên mới được cấp hoặc gỡ vai trò ADMIN.";
    static final String HAS_HISTORY = "Không thể xóa tài khoản đã có đơn hàng hoặc yêu cầu đổi/trả. Hãy khóa tài khoản thay vì xóa.";
    static final String BAD_ROLE = "Vai trò không hợp lệ. Chỉ chấp nhận CUSTOMER, MANAGER hoặc ADMIN.";

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private AddressRepository addressRepository;
    // TEMPORARY-COMPAT(B08): replace by RefreshTokenService.revokeAllForUser once B01 publishes it (see UserSessionRepository).
    @Autowired private UserSessionRepository userSessionRepository;

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng #" + id));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------------------

    /** 404 when no such user (one cheap existence query; used before listing the orders of a user). */
    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (id == null || !userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng #" + id);
        }
    }

    /**
     * One page of users. Three statements for any page size: the page of ids, the count, and the users of that page with
     * their roles (IN). No collection is fetched together with the limit.
     *
     * @param keyword literal, case-insensitive match on username or email (null / blank = no filter)
     * @param active  null = both
     * @param role    null / blank = any; otherwise one of {@link #ASSIGNABLE_ROLES}
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(String keyword, Boolean active, String role, Pageable pageable) {
        String roleFilter = normalizeRoleFilter(role);
        Page<Long> ids = userRepository.searchIds(likePattern(keyword),
                active != null, Boolean.TRUE.equals(active),
                roleFilter != null, roleFilter == null ? "" : roleFilter, pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<Long, User> byId = userRepository.findAllWithRolesByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<AdminUserResponse> rows = new ArrayList<>(ids.getNumberOfElements());
        for (Long id : ids.getContent()) { // keep the order of the id page
            User user = byId.get(id);
            if (user != null) rows.add(AdminUserResponse.from(user)); // a user deleted in between is simply skipped
        }
        return new PageImpl<>(rows, pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse detail(Long id) {
        User user = loadWithRoles(id);
        return AdminUserResponse.from(user, userRepository.countOrdersOf(id), userRepository.countReturnRequestsOf(id));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------------------------------------------------

    /** Disables the account and revokes all its refresh tokens. Idempotent (a disabled user is revoked again, harmlessly). */
    @Transactional
    public AdminUserResponse disable(Long actorId, Long targetId) {
        if (sameUser(actorId, targetId)) throw new ConflictException(SELF_DISABLE);
        List<Long> activeAdmins = userRepository.lockActiveIdsByRole(Role.ADMIN);
        User target = loadWithRoles(targetId);
        if (target.isActive()) {
            rejectLastAdmin(target, activeAdmins);
            target.setActive(false);
            userRepository.saveAndFlush(target);
        }
        int revoked = userSessionRepository.revokeAllForUser(targetId);
        log.info("User disabled: actor={} target={} refreshTokensRevoked={}", actorId, targetId, revoked);
        return AdminUserResponse.from(target);
    }

    @Transactional
    public AdminUserResponse enable(Long actorId, Long targetId) {
        User target = loadWithRoles(targetId);
        if (!target.isActive()) {
            target.setActive(true);
            userRepository.save(target);
        }
        log.info("User enabled: actor={} target={}", actorId, targetId);
        return AdminUserResponse.from(target);
    }

    /**
     * Replaces the role set of a user. The new roles reach the user's token after a refresh (at most the access-token TTL).
     *
     * @param actorIsAdmin whether the caller holds the ADMIN role (from the verified token)
     */
    @Transactional
    public AdminUserResponse setRoles(Long actorId, boolean actorIsAdmin, Long targetId, Set<String> requestedRoles) {
        Set<String> names = normalizeRoleNames(requestedRoles);
        if (sameUser(actorId, targetId)) throw new ConflictException(SELF_ROLES);
        List<Long> activeAdmins = userRepository.lockActiveIdsByRole(Role.ADMIN);
        User target = loadWithRoles(targetId);

        boolean hadAdmin = target.hasRole(Role.ADMIN);
        boolean wantsAdmin = names.contains(Role.ADMIN);
        if (hadAdmin != wantsAdmin && !actorIsAdmin) throw new ForbiddenException(ADMIN_ROLE_ADMIN_ONLY);
        if (hadAdmin && !wantsAdmin) rejectLastAdmin(target, activeAdmins);

        Set<Role> roles = new HashSet<>();
        for (String name : names) {
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new BusinessRuleViolationException("Vai trò " + name + " chưa được khởi tạo trong hệ thống.")));
        }
        target.setRoles(roles);
        userRepository.save(target);
        log.info("User roles changed: actor={} target={} roles={}", actorId, targetId, names);
        return AdminUserResponse.from(target);
    }

    /**
     * Hard delete, only for a user with no orders and no return requests. Removes the cart (existing behaviour), the address
     * book and the refresh tokens first, so the result does not depend on which foreign keys exist in the database.
     */
    @Transactional
    public void deleteUser(Long actorId, Long targetId) {
        if (sameUser(actorId, targetId)) throw new ConflictException(SELF_DELETE);
        List<Long> activeAdmins = userRepository.lockActiveIdsByRole(Role.ADMIN);
        User target = loadWithRoles(targetId);
        rejectLastAdmin(target, activeAdmins);
        if (userRepository.countOrdersOf(targetId) > 0 || userRepository.countReturnRequestsOf(targetId) > 0) {
            throw new ConflictException(HAS_HISTORY);
        }
        cartRepository.findByUserId(targetId).ifPresent(cart -> {
            cartItemRepository.deleteAllByCartId(cart.getId());
            cartRepository.delete(cart);
        });
        addressRepository.deleteAllByUser(targetId);
        userSessionRepository.deleteAllForUser(targetId);
        userRepository.delete(target);
        log.info("User deleted: actor={} target={}", actorId, targetId);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    private User loadWithRoles(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng #" + id));
    }

    private static boolean sameUser(Long actorId, Long targetId) {
        return actorId != null && actorId.equals(targetId);
    }

    /**
     * 409 when {@code target} is an active ADMIN and no OTHER active admin exists. {@code lockedActiveAdmins} are the ids
     * returned (and locked) by {@code lockActiveIdsByRole}.
     */
    private static void rejectLastAdmin(User target, List<Long> lockedActiveAdmins) {
        if (!target.isActive() || !target.hasRole(Role.ADMIN)) return;
        boolean anotherActiveAdmin = lockedActiveAdmins.stream().anyMatch(id -> !id.equals(target.getId()));
        if (!anotherActiveAdmin) throw new ConflictException(LAST_ADMIN);
    }

    private static Set<String> normalizeRoleNames(Set<String> requested) {
        if (requested == null || requested.isEmpty()) throw new BadRequestException("Cần ít nhất một vai trò.");
        Set<String> names = new HashSet<>();
        for (String raw : requested) {
            String name = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (!ASSIGNABLE_ROLES.contains(name)) throw new BadRequestException(BAD_ROLE);
            names.add(name);
        }
        return names;
    }

    private static String normalizeRoleFilter(String role) {
        if (role == null || role.isBlank()) return null;
        String name = role.trim().toUpperCase(Locale.ROOT);
        if (!ASSIGNABLE_ROLES.contains(name)) throw new BadRequestException(BAD_ROLE);
        return name;
    }

    /** Lower-cased LIKE pattern with {@code % _ !} escaped (escape char {@code !}); "%" when there is no keyword. */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) return "%";
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BadRequestException("Từ khóa tìm kiếm tối đa " + MAX_KEYWORD_LENGTH + " ký tự.");
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
