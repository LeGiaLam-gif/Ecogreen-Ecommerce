package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.CategoryResponse;
import com.example.backend.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public category list: flat by default, {@code ?tree=true} for the parent/child tree. Not paged (small, bounded set). */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryV1Controller {

    @Autowired private CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(@RequestParam(defaultValue = "false") boolean tree) {
        if (tree) {
            return ApiResponse.of(categoryService.getTree());
        }
        return ApiResponse.of(categoryService.getAll().stream().map(CategoryResponse::from).toList());
    }
}
