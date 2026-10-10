package com.example.backend.controller;

import com.example.backend.dto.OrderResponse;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.OrderService;
import com.example.backend.service.OrderViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B05: the /api/v1 order endpoints through standalone MockMvc (real controller, real AuthGuard, the real
 * GlobalExceptionHandler, mocked OrderService): allowed and denied callers, envelopes, validation and paging rules.
 */
class OrderV1ControllerTest {

    private OrderService orderService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        OrderV1Controller controller = new OrderV1Controller();
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "authGuard", new AuthGuard());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static CurrentUser customer() {
        return new CurrentUser(5L, List.of("CUSTOMER"), List.of());
    }

    private static CurrentUser staff(String... permissions) {
        return new CurrentUser(99L, List.of("MANAGER"), List.of(permissions));
    }

    private static OrderResponse response(long id) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return OrderResponse.from(order, List.of());
    }

    // ---- GET /orders/my ----

    @Test
    void myOrders_unauthenticated_is401() throws Exception {
        mvc.perform(get("/api/v1/orders/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(orderService);
    }

    @Test
    void myOrders_returnsTheEnvelope_andUsesTheTokenUserNotAClientId() throws Exception {
        when(orderService.listMyOrders(eq(5L), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(7L)), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/orders/my").param("userId", "6")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[0].canCancel").value(false))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
        verify(orderService).listMyOrders(eq(5L), eq(null), any(Pageable.class));
    }

    @Test
    void myOrders_sizeAbove100_isClampedTo100() throws Exception {
        when(orderService.listMyOrders(anyLong(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get("/api/v1/orders/my").param("size", "1000")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).listMyOrders(eq(5L), any(), pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void myOrders_sortOutsideTheWhitelist_is400ValidationError() throws Exception {
        mvc.perform(get("/api/v1/orders/my").param("sort", "customerPhone,asc")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    void myOrders_unknownStatusFilter_is400ValidationError() throws Exception {
        mvc.perform(get("/api/v1/orders/my").param("status", "PENDING")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---- GET /orders/{id} and /history ----

    @Test
    void getOrder_forAnotherCustomer_is403Forbidden() throws Exception {
        when(orderService.getOrderView(eq(7L), any(OrderViewer.class))).thenThrow(new ForbiddenException("no"));

        mvc.perform(get("/api/v1/orders/7").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void getOrder_viewerIsBuiltFromTheTokenPermissions() throws Exception {
        when(orderService.getOrderView(eq(7L), any(OrderViewer.class))).thenReturn(response(7L));

        mvc.perform(get("/api/v1/orders/7").requestAttr(AuthInterceptor.REQUEST_ATTR,
                        staff(Permissions.ORDER_VIEW, Permissions.ORDER_UPDATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.meta").doesNotExist());

        ArgumentCaptor<OrderViewer> viewer = ArgumentCaptor.forClass(OrderViewer.class);
        verify(orderService).getOrderView(eq(7L), viewer.capture());
        assertEquals(new OrderViewer(99L, true, true, false), viewer.getValue());
    }

    @Test
    void history_returnsTheTimeline() throws Exception {
        when(orderService.getHistory(eq(7L), any(OrderViewer.class))).thenReturn(List.of());

        mvc.perform(get("/api/v1/orders/7/history").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---- POST /orders/{id}/cancel ----

    @Test
    void cancel_customer_usesTheOwnerPath_withoutABody() throws Exception {
        when(orderService.getOrderView(eq(7L), any(OrderViewer.class))).thenReturn(response(7L));

        mvc.perform(post("/api/v1/orders/7/cancel").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk());

        verify(orderService).cancelOwnOrder(7L, 5L, null);
        verify(orderService, never()).transitionStatus(anyLong(), any(OrderStatus.class), any(), any());
    }

    @Test
    void cancel_staffWithOrderCancel_usesTheStateMachine_withTheNote() throws Exception {
        when(orderService.getOrderView(eq(7L), any(OrderViewer.class))).thenReturn(response(7L));

        mvc.perform(post("/api/v1/orders/7/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"hết hàng\"}")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_CANCEL)))
                .andExpect(status().isOk());

        verify(orderService).transitionStatus(7L, OrderStatus.CANCELLED, 99L, "hết hàng");
        verify(orderService, never()).cancelOwnOrder(anyLong(), anyLong(), any());
    }

    @Test
    void cancel_ownerOfPaidOrder_is400BusinessRule() throws Exception {
        when(orderService.cancelOwnOrder(7L, 5L, null)).thenThrow(new BusinessRuleViolationException("paid"));

        mvc.perform(post("/api/v1/orders/7/cancel").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void cancel_noteLongerThan500_is400ValidationError() throws Exception {
        mvc.perform(post("/api/v1/orders/7/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "x".repeat(501) + "\"}")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    // ---- GET /admin/orders ----

    @Test
    void adminOrders_withoutViewAll_is403_evenForACustomerOrAViewOnlyRole() throws Exception {
        mvc.perform(get("/api/v1/admin/orders").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/orders")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_VIEW)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void adminOrders_withViewAll_passesFiltersAndPaging() throws Exception {
        when(orderService.listAdminOrders(any(), any(), any(), any(), any(Pageable.class), any(OrderViewer.class)))
                .thenReturn(new PageImpl<>(List.of(response(7L)), PageRequest.of(1, 5), 11));

        mvc.perform(get("/api/v1/admin/orders").param("status", "paid").param("keyword", "an")
                        .param("from", "2026-10-01").param("to", "2026-10-09").param("page", "1").param("size", "5")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_VIEW_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(5))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        verify(orderService).listAdminOrders(eq(OrderStatus.PAID), eq("an"), eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 9)), any(Pageable.class), any(OrderViewer.class));
    }

    // ---- PATCH /admin/orders/{id}/status ----

    private String patchBody(String newStatus) {
        return "{\"newStatus\":" + (newStatus == null ? "null" : "\"" + newStatus + "\"") + ",\"note\":\"ghi chú\"}";
    }

    @Test
    void updateStatus_withoutOrderUpdate_is403() throws Exception {
        mvc.perform(patch("/api/v1/admin/orders/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("PROCESSING"))
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_VIEW_ALL)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void updateStatus_toCancelled_needsOrderCancelAsWell() throws Exception {
        mvc.perform(patch("/api/v1/admin/orders/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("CANCELLED"))
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_UPDATE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verify(orderService, never()).transitionStatus(anyLong(), any(OrderStatus.class), any(), any());
    }

    @Test
    void updateStatus_unknownStatus_is400ValidationError() throws Exception {
        mvc.perform(patch("/api/v1/admin/orders/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("SHIPPED_LATER"))
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_UPDATE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    void updateStatus_blankStatus_is400ValidationError_withTheFieldName() throws Exception {
        mvc.perform(patch("/api/v1/admin/orders/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody(null))
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_UPDATE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.newStatus").exists());
    }

    @Test
    void updateStatus_invalidTransition_is400BusinessRuleViolation() throws Exception {
        when(orderService.transitionStatus(eq(7L), eq(OrderStatus.PAID), eq(99L), anyString()))
                .thenThrow(new BusinessRuleViolationException("Không thể chuyển đơn hàng từ DELIVERED sang PAID."));

        mvc.perform(patch("/api/v1/admin/orders/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("PAID"))
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_UPDATE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void updateStatus_allowed_recordsTheTokenUserAsActor_andReturnsTheOrder() throws Exception {
        when(orderService.getOrderView(eq(7L), any(OrderViewer.class))).thenReturn(response(7L));

        mvc.perform(patch("/api/v1/admin/orders/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("processing"))
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.ORDER_UPDATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));

        verify(orderService).transitionStatus(7L, OrderStatus.PROCESSING, 99L, "ghi chú");
    }

    @Test
    void updateStatus_toCancelled_withBothPermissions_isAllowed() throws Exception {
        when(orderService.getOrderView(eq(7L), any(OrderViewer.class))).thenReturn(response(7L));

        mvc.perform(patch("/api/v1/admin/orders/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("CANCELLED"))
                        .requestAttr(AuthInterceptor.REQUEST_ATTR,
                                staff(Permissions.ORDER_UPDATE, Permissions.ORDER_CANCEL)))
                .andExpect(status().isOk());

        verify(orderService).transitionStatus(7L, OrderStatus.CANCELLED, 99L, "ghi chú");
    }
}
