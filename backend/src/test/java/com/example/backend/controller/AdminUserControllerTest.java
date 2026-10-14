package com.example.backend.controller;

import com.example.backend.dto.AdminUserResponse;
import com.example.backend.dto.OrderResponse;
import com.example.backend.entity.OrderStatus;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.OrderService;
import com.example.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B08 admin user endpoints through the real GlobalExceptionHandler and the real AuthGuard (standalone MockMvc, mocked
 * services). The request attribute stands in for what AuthInterceptor sets from a verified token.
 */
class AdminUserControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final long ACTOR = 42L;

    private UserService userService;
    private OrderService orderService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        orderService = mock(OrderService.class);
        AdminUserV1Controller controller = new AdminUserV1Controller();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "authGuard", new AuthGuard());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();

        when(userService.list(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row(7L))));
        when(userService.detail(7L)).thenReturn(row(7L));
        when(userService.disable(anyLong(), eq(7L))).thenReturn(row(7L));
        when(userService.enable(anyLong(), eq(7L))).thenReturn(row(7L));
        when(userService.setRoles(anyLong(), anyBoolean(), eq(7L), any())).thenReturn(row(7L));
        when(orderService.listMyOrders(anyLong(), any(), any(Pageable.class))).thenReturn(new PageImpl<OrderResponse>(List.of()));
    }

    private static AdminUserResponse row(long id) {
        AdminUserResponse r = new AdminUserResponse();
        r.id = id;
        r.username = "user" + id;
        r.email = "user" + id + "@example.com";
        r.active = true;
        r.roles = List.of("CUSTOMER");
        return r;
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String... permissions) {
        return b.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(ACTOR, List.of("MANAGER"), List.of(permissions)));
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b, String... permissions) {
        return b.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(ACTOR, List.of("ADMIN"), List.of(permissions)));
    }

    // ── authentication and permissions (denied AND allowed) ────────────────────

    @Test
    void everyEndpoint_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/admin/users/7")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users/7/orders")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/7/disable")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/7/enable")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/7/roles").contentType(JSON).content("{\"roles\":[\"CUSTOMER\"]}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/admin/users/7")).andExpect(status().isUnauthorized());
        verify(userService, never()).disable(anyLong(), anyLong());
    }

    @Test
    void read_withoutUserView_is403_withUserView_is200() throws Exception {
        mvc.perform(as(get("/api/v1/admin/users"), Permissions.USER_UPDATE, Permissions.USER_DISABLE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(as(get("/api/v1/admin/users/7"), Permissions.ORDER_VIEW_ALL)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/admin/users/7/orders"), Permissions.ORDER_VIEW_ALL)).andExpect(status().isForbidden());

        mvc.perform(as(get("/api/v1/admin/users"), Permissions.USER_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("user7"))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
        mvc.perform(as(get("/api/v1/admin/users/7"), Permissions.USER_VIEW)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    void disableEnableDelete_needUserDisable_userViewAndUserUpdateAreNotEnough() throws Exception {
        String[] notEnough = {Permissions.USER_VIEW, Permissions.USER_UPDATE};
        mvc.perform(as(patch("/api/v1/admin/users/7/disable"), notEnough)).andExpect(status().isForbidden());
        mvc.perform(as(patch("/api/v1/admin/users/7/enable"), notEnough)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/v1/admin/users/7"), notEnough)).andExpect(status().isForbidden());
        verify(userService, never()).disable(anyLong(), anyLong());
        verify(userService, never()).enable(anyLong(), anyLong());
        verify(userService, never()).deleteUser(anyLong(), anyLong());

        mvc.perform(as(patch("/api/v1/admin/users/7/disable"), Permissions.USER_DISABLE)).andExpect(status().isOk());
        mvc.perform(as(patch("/api/v1/admin/users/7/enable"), Permissions.USER_DISABLE)).andExpect(status().isOk());
        mvc.perform(as(delete("/api/v1/admin/users/7"), Permissions.USER_DISABLE)).andExpect(status().isNoContent());
        verify(userService).disable(ACTOR, 7L);   // the actor is the token's user, never a client value
        verify(userService).enable(ACTOR, 7L);
        verify(userService).deleteUser(ACTOR, 7L);
    }

    @Test
    void roles_needUserUpdate_andTheActorsAdminRoleComesFromTheToken() throws Exception {
        String body = "{\"roles\":[\"MANAGER\"]}";
        mvc.perform(as(patch("/api/v1/admin/users/7/roles").contentType(JSON).content(body), Permissions.USER_VIEW, Permissions.USER_DISABLE))
                .andExpect(status().isForbidden());
        verify(userService, never()).setRoles(anyLong(), anyBoolean(), anyLong(), any());

        mvc.perform(as(patch("/api/v1/admin/users/7/roles").contentType(JSON).content(body), Permissions.USER_UPDATE)).andExpect(status().isOk());
        verify(userService).setRoles(ACTOR, false, 7L, Set.of("MANAGER")); // a MANAGER token: actorIsAdmin = false

        mvc.perform(asAdmin(patch("/api/v1/admin/users/7/roles").contentType(JSON).content(body), Permissions.USER_UPDATE)).andExpect(status().isOk());
        verify(userService).setRoles(ACTOR, true, 7L, Set.of("MANAGER"));
    }

    // ── validation and error mapping ───────────────────────────────────────────

    @Test
    void roles_emptyOrMissingBody_is400ValidationError() throws Exception {
        mvc.perform(as(patch("/api/v1/admin/users/7/roles").contentType(JSON).content("{\"roles\":[]}"), Permissions.USER_UPDATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(as(patch("/api/v1/admin/users/7/roles").contentType(JSON).content("{}"), Permissions.USER_UPDATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(userService, never()).setRoles(anyLong(), anyBoolean(), anyLong(), any());
    }

    @Test
    void conflicts_areMappedTo409Conflict_withTheServiceMessage() throws Exception {
        doThrow(new ConflictException("Không thể xóa tài khoản đã có đơn hàng. Hãy khóa tài khoản thay vì xóa."))
                .when(userService).deleteUser(ACTOR, 8L);
        when(userService.disable(ACTOR, 9L)).thenThrow(new ConflictException("Bạn không thể tự khóa tài khoản của chính mình."));

        mvc.perform(as(delete("/api/v1/admin/users/8"), Permissions.USER_DISABLE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Không thể xóa tài khoản đã có đơn hàng. Hãy khóa tài khoản thay vì xóa."));
        mvc.perform(as(patch("/api/v1/admin/users/9/disable"), Permissions.USER_DISABLE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void forbiddenFromTheService_isMappedTo403() throws Exception {
        when(userService.setRoles(ACTOR, false, 7L, Set.of("ADMIN"))).thenThrow(new ForbiddenException("Chỉ Quản trị viên mới được cấp hoặc gỡ vai trò ADMIN."));

        mvc.perform(as(patch("/api/v1/admin/users/7/roles").contentType(JSON).content("{\"roles\":[\"ADMIN\"]}"), Permissions.USER_UPDATE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void unknownUser_is404() throws Exception {
        when(userService.detail(404L)).thenThrow(new ResourceNotFoundException("Không tìm thấy người dùng #404"));

        mvc.perform(as(get("/api/v1/admin/users/404"), Permissions.USER_VIEW))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ── list parameters ────────────────────────────────────────────────────────

    @Test
    void list_passesFiltersAndAClampedPageable() throws Exception {
        mvc.perform(as(get("/api/v1/admin/users").param("keyword", "lan").param("role", "CUSTOMER")
                .param("active", "false").param("page", "2").param("size", "1000"), Permissions.USER_VIEW))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).list(eq("lan"), eq(false), eq("CUSTOMER"), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize(), "size is clamped server-side");
    }

    @Test
    void list_unknownSortField_is400_andNothingIsQueried() throws Exception {
        mvc.perform(as(get("/api/v1/admin/users").param("sort", "password,asc"), Permissions.USER_VIEW))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(userService, never()).list(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void list_invalidActiveValue_is400() throws Exception {
        mvc.perform(as(get("/api/v1/admin/users").param("active", "maybe"), Permissions.USER_VIEW))
                .andExpect(status().isBadRequest());
    }

    // ── order history ──────────────────────────────────────────────────────────

    @Test
    void adminUserOrders_paged() throws Exception {
        OrderResponse order = new OrderResponse();
        order.id = 500L;
        order.userId = 7L;
        order.status = "PAID";
        when(orderService.listMyOrders(eq(7L), eq(OrderStatus.PAID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), org.springframework.data.domain.PageRequest.of(1, 5), 11));

        mvc.perform(as(get("/api/v1/admin/users/7/orders").param("status", "paid").param("page", "1").param("size", "5"),
                        Permissions.USER_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(500))
                .andExpect(jsonPath("$.data[0].userId").value(7))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(5))
                .andExpect(jsonPath("$.meta.totalElements").value(11))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).listMyOrders(eq(7L), eq(OrderStatus.PAID), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());
        assertTrue(pageable.getValue().getSort().getOrderFor("createdAt").isDescending(), "newest first by default");
        verify(userService).requireExists(7L);
    }

    @Test
    void adminUserOrders_unknownUser_is404_andNoOrdersAreQueried() throws Exception {
        doThrow(new ResourceNotFoundException("Không tìm thấy người dùng #404")).when(userService).requireExists(404L);

        mvc.perform(as(get("/api/v1/admin/users/404/orders"), Permissions.USER_VIEW)).andExpect(status().isNotFound());

        verify(orderService, never()).listMyOrders(anyLong(), any(), any(Pageable.class));
    }

    @Test
    void adminUserOrders_invalidStatus_is400() throws Exception {
        mvc.perform(as(get("/api/v1/admin/users/7/orders").param("status", "NOPE"), Permissions.USER_VIEW))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(orderService, never()).listMyOrders(anyLong(), any(), any(Pageable.class));
    }

    @Test
    void adminUserOrders_withoutUserView_is403_evenWithOrderPermissions() throws Exception {
        mvc.perform(as(get("/api/v1/admin/users/7/orders"), Permissions.ORDER_VIEW, Permissions.ORDER_VIEW_ALL))
                .andExpect(status().isForbidden());
        verify(orderService, never()).listMyOrders(anyLong(), any(), any(Pageable.class));
    }
}
