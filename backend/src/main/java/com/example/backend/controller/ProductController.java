package com.example.backend.controller;

import com.example.backend.dto.ProductResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired private ProductService productService;
    @Autowired private AuthGuard authGuard;

    /** Public: only ACTIVE products, so an empty/seeded-free catalog degrades gracefully. */
    @GetMapping
    public List<ProductResponse> getAllProducts() {
        return productService.getAllActive().stream().map(ProductResponse::from).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return ProductResponse.from(productService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        var p = productService.create(
                (String) body.get("name"),
                (String) body.get("description"),
                new BigDecimal(body.get("price").toString()),
                Integer.parseInt(body.get("stockQuantity").toString()),
                (String) body.get("image"),
                Long.parseLong(body.get("categoryId").toString())
        );
        return ResponseEntity.status(201).body(ProductResponse.from(p));
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        var p = productService.update(
                id,
                (String) body.get("name"),
                (String) body.get("description"),
                new BigDecimal(body.get("price").toString()),
                Integer.parseInt(body.get("stockQuantity").toString()),
                (String) body.get("image"),
                Long.parseLong(body.get("categoryId").toString()),
                (String) body.get("status")
        );
        return ProductResponse.from(p);
    }

    /** Admin listing includes INACTIVE products too (for inventory management). */
    @GetMapping("/admin/all")
    public List<ProductResponse> getAllForAdmin(HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return productService.getAllForAdmin().stream().map(ProductResponse::from).collect(Collectors.toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        productService.delete(id); // deactivates rather than hard-deleting
        return ResponseEntity.noContent().build();
    }
}
