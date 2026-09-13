package com.example.backend.service;

import com.example.backend.entity.Category;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    @Autowired private CategoryRepository categoryRepository;

    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    public Category create(String name, String description) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category name already exists: " + name);
        }
        Category c = new Category();
        c.setName(name);
        c.setDescription(description);
        return categoryRepository.save(c);
    }

    public Category update(Long id, String name, String description) {
        Category c = getById(id);
        c.setName(name);
        c.setDescription(description);
        return categoryRepository.save(c);
    }

    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
