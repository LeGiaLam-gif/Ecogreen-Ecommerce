package com.example.backend.controller;

import com.example.backend.dto.CategoryResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired private CategoryService categoryService;
    @Autowired private AuthGuard authGuard;

    @GetMapping
    public List<CategoryResponse> getAll() {
        return categoryService.getAll().stream().map(CategoryResponse::from).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@RequestBody Map<String, String> body, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        var c = categoryService.create(body.get("name"), body.get("description"));
        return ResponseEntity.status(201).body(CategoryResponse.from(c));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return CategoryResponse.from(categoryService.update(id, body.get("name"), body.get("description")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
