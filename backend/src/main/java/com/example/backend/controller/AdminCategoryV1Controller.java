package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.CategoryRequest;
import com.example.backend.dto.CategoryResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import com.example.backend.service.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryV1Controller {

    @Autowired private CategoryService categoryService;
    @Autowired private AuthGuard authGuard;

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest body, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.CATEGORY_CREATE);
        return ResponseEntity.status(201).body(ApiResponse.of(CategoryResponse.from(categoryService.create(body))));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest body,
                                                HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.CATEGORY_UPDATE);
        return ApiResponse.of(CategoryResponse.from(categoryService.update(id, body)));
    }

    /** 409 CONFLICT while the category still has products or child categories. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.CATEGORY_DELETE);
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
