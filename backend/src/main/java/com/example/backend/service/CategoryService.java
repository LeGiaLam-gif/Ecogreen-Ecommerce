package com.example.backend.service;

import com.example.backend.dto.CategoryRequest;
import com.example.backend.dto.CategoryResponse;
import com.example.backend.entity.Category;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CategoryRepository;
import com.example.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CategoryService {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;

    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    /** Parent/child tree built in memory from one findAll() (a category list is small and bounded). */
    public List<CategoryResponse> getTree() {
        return CategoryResponse.tree(categoryRepository.findAll());
    }

    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục #" + id));
    }

    public Category create(CategoryRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Tên danh mục không được để trống.");
        }
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Tên danh mục này đã tồn tại: " + name);
        }
        Category parent = null;
        if (request.parentId() != null) {
            parent = checkedParent(null, request.parentId());
        }
        Category c = new Category();
        c.setName(name);
        c.setDescription(request.description());
        c.setParent(parent);
        c.setSlug(SlugGenerator.unique(SlugGenerator.slugify(name, "category"), categoryRepository::existsBySlug));
        return categoryRepository.save(c);
    }

    /** Replaces name, description and parent. The slug is kept stable on rename. */
    public Category update(Long id, CategoryRequest request) {
        Category c = getById(id);
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Tên danh mục không được để trống.");
        }
        categoryRepository.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ConflictException("Tên danh mục này đã tồn tại: " + name);
            }
        });
        c.setParent(request.parentId() == null ? null : checkedParent(id, request.parentId()));
        c.setName(name);
        c.setDescription(request.description());
        return categoryRepository.save(c);
    }

    /** 409 CONFLICT while the category still has products or child categories. */
    public void delete(Long id) {
        Category c = getById(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Không thể xóa danh mục đang có sản phẩm.");
        }
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("Không thể xóa danh mục đang có danh mục con.");
        }
        categoryRepository.delete(c);
    }

    /**
     * Loads the proposed parent and rejects a cycle: the parent may not be the category itself nor any of its descendants
     * (walking up from the parent must never reach {@code id}). {@code id} is null when the category is being created.
     */
    private Category checkedParent(Long id, Long parentId) {
        Map<Long, Category> all = new LinkedHashMap<>();
        for (Category c : categoryRepository.findAll()) {
            all.put(c.getId(), c);
        }
        Category parent = all.get(parentId);
        if (parent == null) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục cha #" + parentId);
        }
        if (id != null) {
            Set<Long> seen = new HashSet<>();
            Category cursor = parent;
            while (cursor != null && seen.add(cursor.getId())) {
                if (cursor.getId().equals(id)) {
                    throw new BusinessRuleViolationException("Danh mục cha không hợp lệ: tạo vòng lặp trong cây danh mục.");
                }
                Category next = cursor.getParent();
                cursor = next == null ? null : all.get(next.getId());
            }
        }
        return parent;
    }
}
