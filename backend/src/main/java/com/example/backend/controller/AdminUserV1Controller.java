package com.example.backend.controller;

import com.example.backend.api.ApiPaging;
import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.AdminUserResponse;
import com.example.backend.dto.AdminUserRolesRequest;
import com.example.backend.dto.OrderResponse;
import com.example.backend.entity.OrderStatus;
import com.example.backend.exception.BadRequestException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.OrderService;
import com.example.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Staff user management (B08). Every method enforces its own permission server-side: reading needs {@code user:view},
 * role changes {@code user:update}, disable / enable / delete {@code user:disable}. The actor id and roles always come from
 * the verified token. The self-lockout and last-admin rules live in {@link UserService}.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserV1Controller {

    /** API sort field -> entity property (the whitelist; client text never reaches JPQL). */
    private static final Map<String, String> USER_SORTS =
            Map.of("id", "id", "username", "username", "email", "email", "createdAt", "createdAt");
    private static final Sort USER_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private static final Map<String, String> ORDER_SORTS = Map.of("createdAt", "createdAt", "totalPrice", "totalPrice");
    private static final Sort ORDER_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired private UserService userService;
    @Autowired private OrderService orderService;
    @Autowired private AuthGuard authGuard;

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> list(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String role,
                                                     @RequestParam(required = false) Boolean active,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     @RequestParam(required = false) String sort,
                                                     HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.USER_VIEW);
        Pageable pageable = ApiPaging.of(page, size, sort, USER_SORTS, USER_DEFAULT_SORT);
        return PageResponse.of(userService.list(keyword, active, role, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> get(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.USER_VIEW);
        return ApiResponse.of(userService.detail(id));
    }

    /** The order history of one user, newest first by default. 404 when the user does not exist. */
    @GetMapping("/{id}/orders")
    public ApiResponse<List<OrderResponse>> orders(@PathVariable Long id,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort,
                                                   HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.USER_VIEW);
        userService.requireExists(id);
        Pageable pageable = ApiPaging.of(page, size, sort, ORDER_SORTS, ORDER_DEFAULT_SORT);
        return PageResponse.of(orderService.listMyOrders(id, parseStatusFilter(status), pageable));
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<AdminUserResponse> disable(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.USER_DISABLE);
        return ApiResponse.of(userService.disable(actor(request).getUserId(), id));
    }

    @PatchMapping("/{id}/enable")
    public ApiResponse<AdminUserResponse> enable(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.USER_DISABLE);
        return ApiResponse.of(userService.enable(actor(request).getUserId(), id));
    }

    @PatchMapping("/{id}/roles")
    public ApiResponse<AdminUserResponse> setRoles(@PathVariable Long id, @Valid @RequestBody AdminUserRolesRequest body,
                                                   HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.USER_UPDATE);
        CurrentUser actor = actor(request);
        return ApiResponse.of(userService.setRoles(actor.getUserId(), actor.isAdmin(), id, body.roles()));
    }

    /** 409 CONFLICT for yourself, for the last active admin, and for a user that has orders or return requests. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.USER_DISABLE);
        userService.deleteUser(actor(request).getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Only called after {@code requirePermission}, which already guarantees a CurrentUser exists. */
    private static CurrentUser actor(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
    }

    private static OrderStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Trạng thái đơn hàng không hợp lệ.");
        }
    }
}
