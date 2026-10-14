package com.example.backend.service;

import com.example.backend.dto.AdminUserResponse;
import com.example.backend.entity.Cart;
import com.example.backend.entity.RefreshToken;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.AddressRepository;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B08 admin user rules: disable / enable, self-lockout, last admin, role changes, guarded delete, paged list. */
class UserServiceAdminTest {

    private static final long ACTOR = 99L;

    private UserRepository users;
    private RoleRepository roles;
    private CartRepository carts;
    private CartItemRepository cartItems;
    private AddressRepository addresses;
    private UserSessionRepository sessions;
    private UserService service;

    private final Role customerRole = new Role(1L, Role.CUSTOMER);
    private final Role managerRole = new Role(2L, Role.MANAGER);
    private final Role adminRole = new Role(3L, Role.ADMIN);

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        carts = mock(CartRepository.class);
        cartItems = mock(CartItemRepository.class);
        addresses = mock(AddressRepository.class);
        sessions = mock(UserSessionRepository.class);
        service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "roleRepository", roles);
        ReflectionTestUtils.setField(service, "cartRepository", carts);
        ReflectionTestUtils.setField(service, "cartItemRepository", cartItems);
        ReflectionTestUtils.setField(service, "addressRepository", addresses);
        ReflectionTestUtils.setField(service, "userSessionRepository", sessions);

        when(roles.findByName(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(roles.findByName(Role.MANAGER)).thenReturn(Optional.of(managerRole));
        when(roles.findByName(Role.ADMIN)).thenReturn(Optional.of(adminRole));
        lenient().when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(users.lockActiveIdsByRole(Role.ADMIN)).thenReturn(List.of());
    }

    private User user(long id, boolean active, Role... userRoles) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setEmail("user" + id + "@example.com");
        u.setActive(active);
        u.setRoles(new HashSet<>(Set.of(userRoles)));
        when(users.findWithRolesById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private void activeAdmins(Long... ids) {
        when(users.lockActiveIdsByRole(Role.ADMIN)).thenReturn(List.of(ids));
    }

    // ── disable / enable ────────────────────────────────────────────────────────

    /** Fake token table: several families of one user plus one token of another user. */
    @Test
    void disable_revokesAllRefreshTokens() {
        List<RefreshToken> table = new ArrayList<>();
        table.add(token(7L, "family-a"));
        table.add(token(7L, "family-a"));
        table.add(token(7L, "family-b")); // a second device
        table.add(token(8L, "family-c")); // somebody else
        when(sessions.revokeAllForUser(anyLong())).thenAnswer(inv -> {
            int n = 0;
            for (RefreshToken t : table) {
                if (t.getUserId().equals(inv.<Long>getArgument(0)) && !t.isRevoked()) { t.setRevoked(true); n++; }
            }
            return n;
        });
        User target = user(7L, true, customerRole);

        AdminUserResponse result = service.disable(ACTOR, 7L);

        assertFalse(target.isActive());
        assertFalse(result.active);
        assertTrue(table.subList(0, 3).stream().allMatch(RefreshToken::isRevoked), "every token of the user is revoked");
        assertFalse(table.get(3).isRevoked(), "another user's token is untouched");
        InOrder order = inOrder(users, sessions);
        order.verify(users).saveAndFlush(target); // the flag is persisted before the tokens are revoked
        order.verify(sessions).revokeAllForUser(7L);
    }

    private static RefreshToken token(long userId, String family) {
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setFamilyId(family);
        return t;
    }

    @Test
    void disable_alreadyDisabledUser_isIdempotent_andStillRevokes() {
        user(7L, false, customerRole);

        AdminUserResponse result = service.disable(ACTOR, 7L);

        assertFalse(result.active);
        verify(users, never()).saveAndFlush(any(User.class));
        verify(sessions).revokeAllForUser(7L);
    }

    @Test
    void disable_unknownUser_is404() {
        when(users.findWithRolesById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.disable(ACTOR, 5L));
        verify(sessions, never()).revokeAllForUser(anyLong());
    }

    @Test
    void enable_reactivatesTheUser() {
        User target = user(7L, false, customerRole);

        AdminUserResponse result = service.enable(ACTOR, 7L);

        assertTrue(target.isActive());
        assertTrue(result.active);
        verify(users).save(target);
        verify(sessions, never()).revokeAllForUser(anyLong());
    }

    // ── self lockout ────────────────────────────────────────────────────────────

    @Test
    void admin_cannotDisableOrDemoteSelf() {
        user(ACTOR, true, adminRole);
        activeAdmins(ACTOR, 10L); // another admin exists, so only the self rule can fire

        assertThrows(ConflictException.class, () -> service.disable(ACTOR, ACTOR));
        assertThrows(ConflictException.class, () -> service.setRoles(ACTOR, true, ACTOR, Set.of(Role.CUSTOMER)));
        assertThrows(ConflictException.class, () -> service.deleteUser(ACTOR, ACTOR));

        verify(users, never()).saveAndFlush(any(User.class));
        verify(users, never()).save(any(User.class));
        verify(users, never()).delete(any(User.class));
        verify(sessions, never()).revokeAllForUser(anyLong());
    }

    @Test
    void admin_cannotChangeOwnRoles_evenWhenKeepingAdmin() {
        user(ACTOR, true, adminRole);

        assertThrows(ConflictException.class, () -> service.setRoles(ACTOR, true, ACTOR, Set.of(Role.ADMIN, Role.CUSTOMER)));
    }

    // ── last active admin ───────────────────────────────────────────────────────

    @Test
    void lastActiveAdmin_cannotBeDisabledDeletedOrDemoted() {
        user(10L, true, adminRole);
        activeAdmins(10L); // the only active admin; the actor is somebody else (e.g. a manager with user:update)

        ConflictException disable = assertThrows(ConflictException.class, () -> service.disable(ACTOR, 10L));
        assertThrows(ConflictException.class, () -> service.deleteUser(ACTOR, 10L));
        assertThrows(ConflictException.class, () -> service.setRoles(ACTOR, true, 10L, Set.of(Role.MANAGER)));

        assertEquals(UserService.LAST_ADMIN, disable.getMessage());
        verify(users, never()).saveAndFlush(any(User.class));
        verify(users, never()).save(any(User.class));
        verify(users, never()).delete(any(User.class));
        verify(sessions, never()).revokeAllForUser(anyLong());
    }

    @Test
    void anAdminCanBeDisabledDeletedOrDemoted_whenAnotherActiveAdminExists() {
        User target = user(10L, true, adminRole);
        activeAdmins(10L, 11L);
        when(users.countOrdersOf(10L)).thenReturn(0L);
        when(users.countReturnRequestsOf(10L)).thenReturn(0L);

        service.setRoles(ACTOR, true, 10L, Set.of(Role.MANAGER)); // demote
        assertFalse(target.hasRole(Role.ADMIN));

        User second = user(11L, true, adminRole);
        service.disable(ACTOR, 11L);
        assertFalse(second.isActive());

        user(12L, true, adminRole);
        activeAdmins(12L, 13L);
        when(users.countOrdersOf(12L)).thenReturn(0L);
        when(users.countReturnRequestsOf(12L)).thenReturn(0L);
        service.deleteUser(ACTOR, 12L);
        verify(users).delete(any(User.class));
    }

    @Test
    void aDisabledAdmin_doesNotCountAsTheLastAdmin_whenHeIsTheTarget() {
        user(10L, false, adminRole); // already inactive: removing him cannot reduce the active admins
        activeAdmins(20L);
        when(users.countOrdersOf(10L)).thenReturn(0L);
        when(users.countReturnRequestsOf(10L)).thenReturn(0L);

        service.deleteUser(ACTOR, 10L);

        verify(users).delete(any(User.class));
    }

    @Test
    void theAdminRowsAreLockedBeforeTheRuleIsChecked() {
        user(10L, true, adminRole);
        activeAdmins(10L, 11L);

        service.disable(ACTOR, 10L);

        InOrder order = inOrder(users);
        order.verify(users).lockActiveIdsByRole(Role.ADMIN);
        order.verify(users).findWithRolesById(10L);
    }

    // ── delete ──────────────────────────────────────────────────────────────────

    @Test
    void deleteUser_withOrders_returns409() {
        user(7L, true, customerRole);
        when(users.countOrdersOf(7L)).thenReturn(3L);

        ConflictException e = assertThrows(ConflictException.class, () -> service.deleteUser(ACTOR, 7L));

        assertTrue(e.getMessage().contains("khóa tài khoản"), "tells the admin to disable instead");
        verify(users, never()).delete(any(User.class));
        verify(carts, never()).delete(any(Cart.class));
        verify(addresses, never()).deleteAllByUser(anyLong());
    }

    @Test
    void deleteUser_withReturnRequestsOnly_returns409() {
        user(7L, true, customerRole);
        when(users.countOrdersOf(7L)).thenReturn(0L);
        when(users.countReturnRequestsOf(7L)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.deleteUser(ACTOR, 7L));

        verify(users, never()).delete(any(User.class));
    }

    @Test
    void deleteUser_withoutOrders_succeeds_andRemovesCartAddressesAndTokensFirst() {
        User target = user(7L, true, customerRole);
        when(users.countOrdersOf(7L)).thenReturn(0L);
        when(users.countReturnRequestsOf(7L)).thenReturn(0L);
        Cart cart = mock(Cart.class);
        when(cart.getId()).thenReturn(55L);
        when(carts.findByUserId(7L)).thenReturn(Optional.of(cart));

        service.deleteUser(ACTOR, 7L);

        InOrder order = inOrder(cartItems, carts, addresses, sessions, users);
        order.verify(cartItems).deleteAllByCartId(55L);
        order.verify(carts).delete(cart);
        order.verify(addresses).deleteAllByUser(7L);
        order.verify(sessions).deleteAllForUser(7L);
        order.verify(users).delete(target);
    }

    @Test
    void deleteUser_withoutCart_stillSucceeds() {
        User target = user(7L, true, customerRole);
        when(users.countOrdersOf(7L)).thenReturn(0L);
        when(users.countReturnRequestsOf(7L)).thenReturn(0L);
        when(carts.findByUserId(7L)).thenReturn(Optional.empty());

        service.deleteUser(ACTOR, 7L);

        verify(users).delete(target);
        verify(cartItems, never()).deleteAllByCartId(anyLong());
    }

    // ── roles ───────────────────────────────────────────────────────────────────

    @Test
    void setRoles_unknownRole_isRejected() {
        User target = user(7L, true, customerRole);

        assertThrows(BadRequestException.class, () -> service.setRoles(ACTOR, true, 7L, Set.of("SUPERUSER")));
        assertThrows(BadRequestException.class, () -> service.setRoles(ACTOR, true, 7L, Set.of(Role.CUSTOMER, "ROOT")));
        assertThrows(BadRequestException.class, () -> service.setRoles(ACTOR, true, 7L, Set.of()));
        Set<String> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(BadRequestException.class, () -> service.setRoles(ACTOR, true, 7L, withNull));

        assertTrue(target.hasRole(Role.CUSTOMER));
        assertEquals(1, target.getRoles().size());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void setRoles_acceptsKnownRolesCaseInsensitively_andReplacesTheSet() {
        User target = user(7L, true, customerRole);

        AdminUserResponse result = service.setRoles(ACTOR, true, 7L, Set.of("manager", " Customer "));

        assertEquals(List.of(Role.CUSTOMER, Role.MANAGER), result.roles);
        assertEquals(2, target.getRoles().size());
        verify(users).save(target);
    }

    @Test
    void setRoles_byNonAdmin_cannotGrantAdmin() {
        User target = user(7L, true, customerRole);

        assertThrows(ForbiddenException.class, () -> service.setRoles(ACTOR, false, 7L, Set.of(Role.ADMIN)));

        assertFalse(target.hasRole(Role.ADMIN));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void setRoles_byNonAdmin_cannotRemoveAdmin() {
        user(10L, true, adminRole);
        activeAdmins(10L, 11L); // not the last admin: only the permission rule can fire

        assertThrows(ForbiddenException.class, () -> service.setRoles(ACTOR, false, 10L, Set.of(Role.CUSTOMER)));

        verify(users, never()).save(any(User.class));
    }

    @Test
    void setRoles_byNonAdmin_canChangeBetweenNonAdminRoles() {
        User target = user(7L, true, customerRole);

        service.setRoles(ACTOR, false, 7L, Set.of(Role.MANAGER));

        assertTrue(target.hasRole(Role.MANAGER));
        assertFalse(target.hasRole(Role.CUSTOMER));
    }

    @Test
    void setRoles_byAdmin_canGrantAdmin() {
        User target = user(7L, true, customerRole);

        service.setRoles(ACTOR, true, 7L, Set.of(Role.CUSTOMER, Role.ADMIN));

        assertTrue(target.hasRole(Role.ADMIN));
    }

    // ── list ────────────────────────────────────────────────────────────────────

    @Test
    void list_pagesIdsFirst_thenLoadsTheRolesOfThatPageOnce_keepingTheIdOrder() {
        Pageable pageable = PageRequest.of(0, 3);
        when(users.searchIds(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(30L, 10L, 20L), pageable, 42));
        User u10 = user(10L, true, customerRole);
        User u20 = user(20L, false, adminRole, customerRole);
        User u30 = user(30L, true, managerRole);
        when(users.findAllWithRolesByIdIn(anyCollection())).thenReturn(List.of(u10, u20, u30)); // arbitrary DB order

        Page<AdminUserResponse> page = service.list(null, null, null, pageable);

        assertEquals(List.of(30L, 10L, 20L), page.getContent().stream().map(r -> r.id).toList());
        assertEquals(42, page.getTotalElements());
        assertEquals(List.of(Role.ADMIN, Role.CUSTOMER), page.getContent().get(2).roles);
        verify(users, times(1)).findAllWithRolesByIdIn(List.of(30L, 10L, 20L));
        verify(users, never()).findAll();
        verify(users, never()).countOrdersOf(anyLong()); // no per-row query in the list
    }

    @Test
    void list_emptyPage_doesNotLoadAnyUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        when(users.searchIds(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<AdminUserResponse> page = service.list("nobody", null, null, pageable);

        assertTrue(page.isEmpty());
        verify(users, never()).findAllWithRolesByIdIn(anyCollection());
    }

    @Test
    void list_passesTheFiltersToTheQuery() {
        Pageable pageable = PageRequest.of(1, 5);
        when(users.searchIds(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list("  Lan_A% ", false, "customer", pageable);

        verify(users).searchIds("%lan!_a!%%", true, false, true, Role.CUSTOMER, pageable);
    }

    @Test
    void list_withoutFilters_usesMatchAllPatternAndNoNullParameters() {
        Pageable pageable = PageRequest.of(0, 20);
        when(users.searchIds(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list(null, null, " ", pageable);

        verify(users).searchIds("%", false, false, false, "", pageable);
    }

    @Test
    void list_unknownRoleFilter_isRejected() {
        assertThrows(BadRequestException.class, () -> service.list(null, null, "SUPERUSER", PageRequest.of(0, 20)));
        verify(users, never()).searchIds(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), any(Pageable.class));
    }

    @Test
    void list_tooLongKeyword_isRejected() {
        assertThrows(BadRequestException.class, () -> service.list("x".repeat(101), null, null, PageRequest.of(0, 20)));
    }

    @Test
    void likePattern_escapesWildcards() {
        assertEquals("%", UserService.likePattern(null));
        assertEquals("%", UserService.likePattern("   "));
        assertEquals("%abc%", UserService.likePattern("ABC"));
        assertEquals("%100!%%", UserService.likePattern("100%"));
        assertEquals("%a!_b%", UserService.likePattern("a_b"));
        assertEquals("%wow!!%", UserService.likePattern("wow!"));
    }

    // ── detail ──────────────────────────────────────────────────────────────────

    @Test
    void detail_includesTheCountsThatExplainWhyDeleteIsRefused() {
        user(7L, true, customerRole);
        when(users.countOrdersOf(7L)).thenReturn(4L);
        when(users.countReturnRequestsOf(7L)).thenReturn(1L);

        AdminUserResponse detail = service.detail(7L);

        assertEquals(4L, detail.orderCount);
        assertEquals(1L, detail.returnRequestCount);
    }

    @Test
    void detail_unknownUser_is404() {
        when(users.findWithRolesById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.detail(5L));
    }
}
