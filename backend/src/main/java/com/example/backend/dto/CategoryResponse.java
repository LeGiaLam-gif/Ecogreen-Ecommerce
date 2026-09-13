package com.example.backend.dto;

import com.example.backend.entity.Category;

public class CategoryResponse {
    public Long id;
    public String name;
    public String description;

    public static CategoryResponse from(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.id = c.getId();
        r.name = c.getName();
        r.description = c.getDescription();
        return r;
    }
}
