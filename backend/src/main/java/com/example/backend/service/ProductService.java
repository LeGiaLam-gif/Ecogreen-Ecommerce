package com.example.backend.service;

import com.example.backend.api.ApiPaging;
import com.example.backend.dto.ProductCreateRequest;
import com.example.backend.dto.ProductSearchCriteria;
import com.example.backend.dto.ProductUpdateRequest;
import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductImageRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.ProductSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {

    /** API sort field -> entity property. Anything else is rejected by ApiPaging (400 VALIDATION_ERROR). */
    public static final Map<String, String> SORT_WHITELIST =
            Map.of("createdAt", "createdAt", "price", "price", "name", "name");
    public static final int DEFAULT_PAGE_SIZE = 12;

    @Autowired private ProductRepository productRepository;
    @Autowired private ProductImageRepository productImageRepository;
    @Autowired private CategoryService categoryService;

    // ── B02 read side ────────────────────────────────────────────────────────

    /**
     * Safe pageable for the catalogue list: page >= 0, size default 12 and at most 100, whitelisted sort, and a trailing
     * {@code id} tie-breaker so "load more" never repeats or skips rows that share a createdAt/price/name.
     */
    public static Pageable toPageable(Integer page, Integer size, String sort) {
        Pageable p = ApiPaging.of(page, size, sort, SORT_WHITELIST, Sort.by(Sort.Direction.DESC, "createdAt"), DEFAULT_PAGE_SIZE);
        return PageRequest.of(p.getPageNumber(), p.getPageSize(), p.getSort().and(Sort.by(Sort.Direction.ASC, "id")));
    }

    /** Public search: ACTIVE products only, whatever {@code criteria.status()} says. */
    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        return productRepository.findAll(ProductSpecifications.matching(criteria, Product.Status.ACTIVE), pageable);
    }

    /** Admin search: every status, optionally narrowed by {@code criteria.status()}. */
    public Page<Product> searchAdmin(ProductSearchCriteria criteria, Pageable pageable) {
        return productRepository.findAll(ProductSpecifications.matching(criteria, criteria.status()), pageable);
    }

    /**
     * All-digit text is an id, anything else a slug (slugs always contain a letter, so they never collide with ids).
     * {@code publicOnly}: a non-ACTIVE product is reported as not found.
     */
    public Product getByIdOrSlug(String idOrSlug, boolean publicOnly) {
        String key = idOrSlug == null ? "" : idOrSlug.trim();
        Product p;
        if (key.matches("\\d{1,18}")) {
            p = productRepository.findById(Long.parseLong(key)).orElse(null);
        } else if (key.matches("[a-z0-9]+(-[a-z0-9]+)*") && key.length() <= 180) {
            p = productRepository.findBySlug(key).orElse(null);
        } else {
            p = null;
        }
        if (p == null || (publicOnly && p.getStatus() != Product.Status.ACTIVE)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm.");
        }
        return p;
    }

    /** Images of a page of products with ONE IN query, ordered by sort_order. Every product id is present in the result. */
    public Map<Long, List<ProductImage>> loadImages(Collection<Product> products) {
        Map<Long, List<ProductImage>> byProduct = new LinkedHashMap<>();
        List<Long> ids = products.stream().map(Product::getId).collect(Collectors.toList());
        ids.forEach(id -> byProduct.put(id, new ArrayList<>()));
        if (ids.isEmpty()) {
            return byProduct;
        }
        for (ProductImage image : productImageRepository.findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(ids)) {
            byProduct.get(image.getProduct().getId()).add(image);
        }
        return byProduct;
    }

    // ── B02 write side ───────────────────────────────────────────────────────

    public Product create(ProductCreateRequest r) {
        String name = r.name() == null ? "" : r.name().trim();
        validate(name, r.price(), r.stockQuantity() == null ? 0 : r.stockQuantity(), r.categoryId());
        checkComparePrice(r.comparePrice(), r.price());
        Product.Status status = r.status() == null ? Product.Status.ACTIVE : r.status();
        if (status != Product.Status.ACTIVE && status != Product.Status.DRAFT) {
            throw new BadRequestException("Khi tạo mới, trạng thái chỉ có thể là DRAFT hoặc ACTIVE.");
        }
        String sku = blankToNull(r.sku());
        if (sku != null && productRepository.existsBySku(sku)) {
            throw new ConflictException("SKU đã tồn tại: " + sku);
        }
        Category category = categoryService.getById(r.categoryId());

        Product p = new Product();
        p.setName(name);
        p.setDescription(r.description());
        p.setPrice(r.price());
        p.setComparePrice(r.comparePrice());
        p.setStockQuantity(r.stockQuantity() == null ? 0 : r.stockQuantity());
        p.setImage(blankToNull(r.image()));
        p.setCategory(category);
        p.setSku(sku);
        p.setBrand(blankToNull(r.brand()));
        p.setStatus(status);
        p.setSlug(uniqueSlug(name, null));
        return saveChecked(p);
    }

    /** Partial update (PATCH). */
    public Product update(Long id, ProductUpdateRequest r) {
        Product p = getByIdOrSlug(String.valueOf(id), false);

        if (r.name() != null) {
            String name = r.name().trim();
            if (name.isEmpty()) throw new BadRequestException("Tên sản phẩm không được để trống.");
            if (!name.equals(p.getName())) {
                p.setName(name);
                p.setSlug(uniqueSlug(name, p.getId()));
            }
        }
        if (r.description() != null) p.setDescription(r.description());
        if (r.price() != null) p.setPrice(r.price());
        if (r.stockQuantity() != null) {
            if (r.stockQuantity() < 0) throw new BadRequestException("Số lượng tồn kho không được âm.");
            p.setStockQuantity(r.stockQuantity());
        }
        if (r.image() != null) p.setImage(blankToNull(r.image()));
        if (r.categoryId() != null) p.setCategory(categoryService.getById(r.categoryId()));
        if (r.brand() != null) p.setBrand(blankToNull(r.brand()));
        if (r.sku() != null) {
            String sku = blankToNull(r.sku());
            if (sku != null && productRepository.existsBySkuAndIdNot(sku, p.getId())) {
                throw new ConflictException("SKU đã tồn tại: " + sku);
            }
            p.setSku(sku);
        }
        if (Boolean.TRUE.equals(r.clearComparePrice())) {
            p.setComparePrice(null);
        } else if (r.comparePrice() != null) {
            p.setComparePrice(r.comparePrice());
        }
        checkComparePrice(p.getComparePrice(), p.getPrice());

        if (r.status() != null && r.status() != p.getStatus()) {
            if (r.status() == Product.Status.ACTIVE) {
                requirePublishable(p); // reactivating goes through the same checks as publish
            }
            p.setStatus(r.status());
        }
        return saveChecked(p);
    }

    /** DRAFT / INACTIVE -> ACTIVE. Requires name, price > 0, a category and at least one image (a legacy image counts). */
    public Product publish(Long id) {
        Product p = getByIdOrSlug(String.valueOf(id), false);
        if (p.getStatus() == Product.Status.ACTIVE) {
            return p;
        }
        if (p.getStatus() != Product.Status.DRAFT && p.getStatus() != Product.Status.INACTIVE) {
            throw new BusinessRuleViolationException("Chỉ có thể xuất bản sản phẩm ở trạng thái DRAFT hoặc INACTIVE.");
        }
        requirePublishable(p);
        p.setStatus(Product.Status.ACTIVE);
        return productRepository.save(p);
    }

    private void requirePublishable(Product p) {
        List<String> missing = new ArrayList<>();
        if (p.getName() == null || p.getName().isBlank()) missing.add("tên sản phẩm");
        if (p.getPrice() == null || p.getPrice().signum() <= 0) missing.add("giá lớn hơn 0");
        if (p.getCategory() == null) missing.add("danh mục");
        boolean hasLegacyImage = p.getImage() != null && !p.getImage().isBlank();
        if (!hasLegacyImage && !productImageRepository.existsByProductId(p.getId())) missing.add("ít nhất một hình ảnh");
        if (!missing.isEmpty()) {
            throw new BusinessRuleViolationException("Chưa thể xuất bản sản phẩm, còn thiếu: " + String.join(", ", missing) + ".");
        }
    }

    // ── single-product lookups used by CartService and the soft delete ───────

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm #" + id));
    }

    /** Soft-delete: INACTIVE (never ARCHIVED), so historical order_items keep a valid product reference. */
    public void delete(Long id) {
        Product p = getById(id);
        p.setStatus(Product.Status.INACTIVE);
        productRepository.save(p);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String uniqueSlug(String name, Long excludeId) {
        String base = SlugGenerator.slugify(name, "product");
        return SlugGenerator.unique(base, candidate -> excludeId == null
                ? productRepository.existsBySlug(candidate)
                : productRepository.existsBySlugAndIdNot(candidate, excludeId));
    }

    private Product saveChecked(Product p) {
        try {
            return productRepository.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request took the same slug/sku between the existence check and the insert.
            throw new ConflictException("Slug hoặc SKU của sản phẩm đã tồn tại. Vui lòng thử lại.");
        }
    }

    private static void checkComparePrice(BigDecimal comparePrice, BigDecimal price) {
        if (comparePrice != null && price != null && comparePrice.compareTo(price) < 0) {
            throw new BadRequestException("Giá so sánh phải lớn hơn hoặc bằng giá bán.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void validate(String name, BigDecimal price, int stockQuantity, Long categoryId) {
        if (name == null || name.isBlank()) throw new BadRequestException("Tên sản phẩm không được để trống.");
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) throw new BadRequestException("Giá sản phẩm phải lớn hơn hoặc bằng 0.");
        if (stockQuantity < 0) throw new BadRequestException("Số lượng tồn kho không được âm.");
        if (categoryId == null) throw new BadRequestException("Vui lòng chọn danh mục cho sản phẩm.");
    }
}
