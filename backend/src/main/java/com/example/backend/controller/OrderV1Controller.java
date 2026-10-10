package com.example.backend.controller;

import com.example.backend.api.ApiPaging;
import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.CancelOrderRequest;
import com.example.backend.dto.OrderResponse;
import com.example.backend.dto.OrderStatusHistoryResponse;
import com.example.backend.dto.UpdateOrderStatusRequest;
import com.example.backend.entity.OrderStatus;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.OrderService;
import com.example.backend.service.OrderViewer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Orders on the /api/v1 contract (B05): typed DTOs, envelopes, paging, server-computed {@code canCancel} and
 * {@code allowedNextStatuses}. Ownership and permissions are enforced here and in OrderService; ids, statuses and amounts
 * from the client are never trusted. The caller is read from the verified token ({@link CurrentUser}), so no User entity
 * is loaded.
 */
@RestController
@RequestMapping("/api/v1")
public class OrderV1Controller {

    /** API sort field -> entity property (the whitelist; client text never reaches JPQL). */
    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "totalPrice", "totalPrice");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired private OrderService orderService;
    @Autowired private AuthGuard authGuard;

    /** The caller's own orders, paged. */
    @GetMapping("/orders/my")
    public ApiResponse<List<OrderResponse>> myOrders(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     @RequestParam(required = false) String sort,
                                                     @RequestParam(required = false) String status,
                                                     HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        Pageable pageable = ApiPaging.of(page, size, sort, SORTS, DEFAULT_SORT);
        return PageResponse.of(orderService.listMyOrders(current.getUserId(), parseStatusFilter(status), pageable));
    }

    /** One order: its owner, or staff with {@code order:view}. */
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.of(orderService.getOrderView(id, viewerOf(requireCurrent(request))));
    }

    /** The order's status timeline: its owner, or staff with {@code order:view}. */
    @GetMapping("/orders/{id}/history")
    public ApiResponse<List<OrderStatusHistoryResponse>> history(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.of(orderService.getHistory(id, viewerOf(requireCurrent(request))));
    }

    /**
     * Cancel an order. The owner may cancel only while it is PENDING_PAYMENT. Staff with {@code order:cancel} may cancel
     * according to the transition table; a paid order is then flagged for refund.
     */
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id,
                                             @RequestBody(required = false) @Valid CancelOrderRequest body,
                                             HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        String note = body == null ? null : body.note();
        if (current.hasPermission(Permissions.ORDER_CANCEL)) {
            orderService.transitionStatus(id, OrderStatus.CANCELLED, current.getUserId(), note);
            return ApiResponse.of(orderService.getOrderView(id, staffViewerOf(current)));
        }
        orderService.cancelOwnOrder(id, current.getUserId(), note);
        return ApiResponse.of(orderService.getOrderView(id, viewerOf(current)));
    }

    /** Staff: every order, filtered and paged. */
    @GetMapping("/admin/orders")
    public ApiResponse<List<OrderResponse>> adminOrders(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size,
                                                        @RequestParam(required = false) String sort,
                                                        HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ORDER_VIEW_ALL);
        CurrentUser current = requireCurrent(request);
        Pageable pageable = ApiPaging.of(page, size, sort, SORTS, DEFAULT_SORT);
        return PageResponse.of(orderService.listAdminOrders(
                parseStatusFilter(status), keyword, from, to, pageable, viewerOf(current)));
    }

    /** Staff: move an order to another status. Invalid transitions are a 400 BUSINESS_RULE_VIOLATION. */
    @PatchMapping("/admin/orders/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(@PathVariable Long id,
                                                   @RequestBody @Valid UpdateOrderStatusRequest body,
                                                   HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ORDER_UPDATE);
        OrderStatus newStatus = parseStatus(body.newStatus());
        if (newStatus == OrderStatus.CANCELLED) {
            authGuard.requirePermission(request, Permissions.ORDER_CANCEL);
        }
        CurrentUser current = requireCurrent(request);
        orderService.transitionStatus(id, newStatus, current.getUserId(), body.note());
        return ApiResponse.of(orderService.getOrderView(id, staffViewerOf(current)));
    }

    private static CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        }
        return current;
    }

    private static OrderViewer viewerOf(CurrentUser current) {
        return new OrderViewer(current.getUserId(),
                current.hasPermission(Permissions.ORDER_VIEW),
                current.hasPermission(Permissions.ORDER_UPDATE),
                current.hasPermission(Permissions.ORDER_CANCEL));
    }

    /**
     * The viewer for the response of a staff action that already succeeded: the actor may read the order they just changed
     * even if their role lacks {@code order:view}, so a committed change is never followed by a 403.
     */
    private static OrderViewer staffViewerOf(CurrentUser current) {
        return new OrderViewer(current.getUserId(), true,
                current.hasPermission(Permissions.ORDER_UPDATE),
                current.hasPermission(Permissions.ORDER_CANCEL));
    }

    private static OrderStatus parseStatusFilter(String status) {
        return status == null || status.isBlank() ? null : parseStatus(status);
    }

    private static OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Trạng thái đơn hàng không hợp lệ: " + status);
        }
    }
}
