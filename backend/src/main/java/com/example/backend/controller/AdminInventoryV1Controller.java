package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.InventoryAdjustRequest;
import com.example.backend.dto.InventoryResponse;
import com.example.backend.dto.InventoryTransactionResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.inventory.InventoryAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin inventory endpoints (B03). Every method enforces its own permission server-side; reserved quantities are shown
 * here only. No endpoint lets a customer change stock.
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
public class AdminInventoryV1Controller {

    @Autowired private InventoryAdminService inventoryAdminService;
    @Autowired private AuthGuard authGuard;

    /** Ordered by available ascending. {@code lowStock=true} keeps rows with available <= {@code threshold} (default 10). */
    @GetMapping
    public ApiResponse<List<InventoryResponse>> list(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Boolean lowStock,
                                                     @RequestParam(required = false) Integer threshold,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.INVENTORY_VIEW);
        return PageResponse.of(
                inventoryAdminService.list(keyword, Boolean.TRUE.equals(lowStock), threshold,
                        InventoryAdminService.pageable(page, size)),
                InventoryResponse::from);
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> get(@PathVariable Long productId, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.INVENTORY_VIEW);
        return ApiResponse.of(InventoryResponse.from(inventoryAdminService.get(productId)));
    }

    @GetMapping("/{productId}/transactions")
    public ApiResponse<List<InventoryTransactionResponse>> transactions(@PathVariable Long productId,
                                                                        @RequestParam(required = false) Integer page,
                                                                        @RequestParam(required = false) Integer size,
                                                                        HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.INVENTORY_VIEW);
        return PageResponse.of(
                inventoryAdminService.ledger(productId, InventoryAdminService.ledgerPageable(page, size)),
                InventoryTransactionResponse::from);
    }

    @PatchMapping("/{productId}/adjust")
    public ApiResponse<InventoryResponse> adjust(@PathVariable Long productId,
                                                 @Valid @RequestBody InventoryAdjustRequest body,
                                                 HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.INVENTORY_ADJUST);
        return ApiResponse.of(InventoryResponse.from(
                inventoryAdminService.adjust(productId, body.quantityDelta(), body.reason(), actorId(request))));
    }

    /** The authenticated admin's id for the ledger; read from the request attribute the interceptor already set. */
    private static Long actorId(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        return current == null ? null : current.getUserId();
    }
}
