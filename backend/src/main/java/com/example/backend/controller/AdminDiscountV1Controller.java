package com.example.backend.controller;

import com.example.backend.api.ApiPaging;
import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.DiscountRequest;
import com.example.backend.dto.DiscountResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import com.example.backend.service.DiscountService;
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
 * Discount codes for staff (B04): paged list, read, create, replace, delete. Reading needs {@code discount:view}; every
 * change needs {@code discount:manage} (the two do not imply each other). There is no admin UI for it yet (B12).
 */
@RestController
@RequestMapping("/api/v1/admin/discounts")
public class AdminDiscountV1Controller {

    /** API sort field -> entity property (the whitelist; client text never reaches JPQL). */
    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "code", "code", "id", "id");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired private DiscountService discountService;
    @Autowired private AuthGuard authGuard;

    @GetMapping
    public ApiResponse<List<DiscountResponse>> list(@RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size,
                                                    @RequestParam(required = false) String sort,
                                                    HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.DISCOUNT_VIEW);
        Pageable pageable = ApiPaging.of(page, size, sort, SORTS, DEFAULT_SORT);
        return PageResponse.of(discountService.list(pageable).map(DiscountResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiscountResponse> get(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.DISCOUNT_VIEW);
        return ApiResponse.of(DiscountResponse.from(discountService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DiscountResponse>> create(@Valid @RequestBody DiscountRequest body,
                                                               HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.DISCOUNT_MANAGE);
        return ResponseEntity.status(201).body(ApiResponse.of(DiscountResponse.from(discountService.create(body))));
    }

    /** Replaces every field of the discount with the request. */
    @PutMapping("/{id}")
    public ApiResponse<DiscountResponse> update(@PathVariable Long id, @Valid @RequestBody DiscountRequest body,
                                                HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.DISCOUNT_MANAGE);
        return ApiResponse.of(DiscountResponse.from(discountService.update(id, body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.DISCOUNT_MANAGE);
        discountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
