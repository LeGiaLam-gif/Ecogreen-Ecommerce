package com.example.backend.dto;

import com.example.backend.entity.Product;
import java.math.BigDecimal;

public class ProductResponse {
    public Long id;
    public Long categoryId;
    public String categoryName;
    public String name;
    public String description;
    public BigDecimal price;
    public int stockQuantity;
    public String image;
    public String status;

    public static ProductResponse from(Product p) {
        ProductResponse r = new ProductResponse();
        r.id = p.getId();
        r.categoryId = p.getCategory() != null ? p.getCategory().getId() : null;
        r.categoryName = p.getCategory() != null ? p.getCategory().getName() : null;
        r.name = p.getName();
        r.description = p.getDescription();
        r.price = p.getPrice();
        r.stockQuantity = p.getStockQuantity();
        r.image = p.getImage();
        r.status = p.getStatus().name();
        return r;
    }
}
