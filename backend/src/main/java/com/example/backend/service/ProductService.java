package com.example.backend.service;

import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryService categoryService;

    public List<Product> getAllActive() {
        return productRepository.findByStatus(Product.Status.ACTIVE);
    }

    public List<Product> getAllForAdmin() {
        return productRepository.findAll();
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public Product create(String name, String description, BigDecimal price, int stockQuantity,
                           String image, Long categoryId) {
        validate(name, price, stockQuantity, categoryId);
        Category category = categoryService.getById(categoryId);

        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setStockQuantity(stockQuantity);
        p.setImage(image);
        p.setCategory(category);
        p.setStatus(Product.Status.ACTIVE);
        return productRepository.save(p);
    }

    public Product update(Long id, String name, String description, BigDecimal price, int stockQuantity,
                           String image, Long categoryId, String status) {
        validate(name, price, stockQuantity, categoryId);
        Product p = getById(id);
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setStockQuantity(stockQuantity);
        p.setImage(image);
        p.setCategory(categoryService.getById(categoryId));
        if (status != null) {
            p.setStatus(Product.Status.valueOf(status));
        }
        return productRepository.save(p);
    }

    public void delete(Long id) {
        // Soft-delete by deactivating, so historical order_items keep a valid product reference.
        Product p = getById(id);
        p.setStatus(Product.Status.INACTIVE);
        productRepository.save(p);
    }

    private void validate(String name, BigDecimal price, int stockQuantity, Long categoryId) {
        if (name == null || name.isBlank()) throw new BadRequestException("Product name is required.");
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) throw new BadRequestException("Price must be >= 0.");
        if (stockQuantity < 0) throw new BadRequestException("Stock quantity cannot be negative.");
        if (categoryId == null) throw new BadRequestException("Category is required.");
    }
}
