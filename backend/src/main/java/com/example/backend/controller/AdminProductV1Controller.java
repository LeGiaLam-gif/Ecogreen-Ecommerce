package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.ProductCreateRequest;
import com.example.backend.dto.ProductResponse;
import com.example.backend.dto.ProductSearchCriteria;
import com.example.backend.dto.ProductUpdateRequest;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.exception.BadRequestException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import com.example.backend.service.ProductImageService;
import com.example.backend.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/** Admin catalogue endpoints (B02). Every method enforces its own permission server-side. */
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductV1Controller {

    @Autowired private ProductService productService;
    @Autowired private ProductImageService productImageService;
    @Autowired private AuthGuard authGuard;

    @GetMapping
    public ApiResponse<List<ProductResponse>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) BigDecimal minPrice,
                                                   @RequestParam(required = false) BigDecimal maxPrice,
                                                   @RequestParam(required = false) Boolean inStock,
                                                   @RequestParam(required = false) Product.Status status,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort,
                                                   HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PRODUCT_VIEW);
        var criteria = new ProductSearchCriteria(keyword, categoryId, minPrice, maxPrice, inStock, status);
        return ProductV1Controller.toResponse(productService,
                productService.searchAdmin(criteria, ProductService.toPageable(page, size, sort)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductCreateRequest body,
                                                               HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PRODUCT_CREATE);
        return ResponseEntity.status(201).body(ApiResponse.of(detail(productService.create(body))));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest body,
                                               HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PRODUCT_UPDATE);
        return ApiResponse.of(detail(productService.update(id, body)));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ProductResponse> publish(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PRODUCT_PUBLISH);
        return ApiResponse.of(detail(productService.publish(id)));
    }

    /** Soft-delete: the product becomes INACTIVE. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PRODUCT_DELETE);
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse.ImageDto>> uploadImage(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "altText", required = false) String altText,
            HttpServletRequest request) throws IOException {
        authGuard.requirePermission(request, Permissions.PRODUCT_UPDATE);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn một tệp ảnh.");
        }
        ProductImage image = productImageService.upload(id, file.getBytes(), altText);
        return ResponseEntity.status(201).body(ApiResponse.of(ProductResponse.ImageDto.from(image)));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId, HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PRODUCT_UPDATE);
        productImageService.delete(id, imageId);
        return ResponseEntity.noContent().build();
    }

    private ProductResponse detail(Product p) {
        return ProductResponse.from(p, productService.loadImages(List.of(p)).get(p.getId()));
    }
}
