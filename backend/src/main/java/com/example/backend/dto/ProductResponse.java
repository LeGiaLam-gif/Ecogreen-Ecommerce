package com.example.backend.dto;

import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;

import java.math.BigDecimal;
import java.util.List;

public class ProductResponse {
    public Long id;
    public Long categoryId;
    public String categoryName;
    public String name;
    public String description;
    public BigDecimal price;
    public int stockQuantity;
    /** Legacy field, still returned: the first image URL when images exist, otherwise the legacy products.image value. */
    public String image;
    public String status;

    /** B02 additions. */
    public String slug;
    public BigDecimal comparePrice;
    public String sku;
    public String brand;
    /** Null when built with {@link #from(Product)} (cart/order/legacy callers); a list (possibly empty) otherwise. */
    public List<ImageDto> images;

    public record ImageDto(Long id, String url, String altText, int sortOrder) {
        public static ImageDto from(ProductImage i) {
            return new ImageDto(i.getId(), i.getImageUrl(), i.getAltText(), i.getSortOrder());
        }
    }

    /** Does not touch images, so it never triggers an extra query. */
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
        r.slug = p.getSlug();
        r.comparePrice = p.getComparePrice();
        r.sku = p.getSku();
        r.brand = p.getBrand();
        return r;
    }

    /** {@code images} must already be ordered by sort_order and belong to {@code p}. */
    public static ProductResponse from(Product p, List<ProductImage> images) {
        ProductResponse r = from(p);
        List<ProductImage> safe = images == null ? List.of() : images;
        r.images = safe.stream().map(ImageDto::from).toList();
        if (!safe.isEmpty()) {
            r.image = safe.get(0).getImageUrl();
        }
        return r;
    }
}
