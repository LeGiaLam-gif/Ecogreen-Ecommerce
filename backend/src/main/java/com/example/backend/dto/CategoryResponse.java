package com.example.backend.dto;

import com.example.backend.entity.Category;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoryResponse {
    public Long id;
    public String name;
    public String description;
    /** B02 additions (legacy fields above are unchanged). */
    public String slug;
    public Long parentId;
    /** Only set by {@link #tree(List)}; null in the flat list. */
    public List<CategoryResponse> children;

    public static CategoryResponse from(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.id = c.getId();
        r.name = c.getName();
        r.description = c.getDescription();
        r.slug = c.getSlug();
        // getId() on a lazy parent proxy does not load the parent: no extra query per category.
        r.parentId = c.getParent() != null ? c.getParent().getId() : null;
        return r;
    }

    /** Builds the parent/child tree from a flat list (input order is kept). Categories whose parent is absent become roots. */
    public static List<CategoryResponse> tree(List<Category> all) {
        Map<Long, CategoryResponse> byId = new LinkedHashMap<>();
        for (Category c : all) {
            CategoryResponse node = from(c);
            node.children = new ArrayList<>();
            byId.put(node.id, node);
        }
        List<CategoryResponse> roots = new ArrayList<>();
        for (CategoryResponse node : byId.values()) {
            CategoryResponse parent = node.parentId == null ? null : byId.get(node.parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots;
    }
}
