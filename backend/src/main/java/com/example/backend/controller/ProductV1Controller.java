package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.ProductResponse;
import com.example.backend.dto.ProductSearchCriteria;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Public catalogue (B02): ACTIVE products only. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    @Autowired private ProductService productService;

    @GetMapping
    public ApiResponse<List<ProductResponse>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) BigDecimal minPrice,
                                                   @RequestParam(required = false) BigDecimal maxPrice,
                                                   @RequestParam(required = false) Boolean inStock,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort) {
        var criteria = new ProductSearchCriteria(keyword, categoryId, minPrice, maxPrice, inStock, null);
        Page<Product> result = productService.search(criteria, ProductService.toPageable(page, size, sort));
        return toResponse(productService, result);
    }

    @GetMapping("/{idOrSlug}")
    public ApiResponse<ProductResponse> get(@PathVariable String idOrSlug) {
        Product p = productService.getByIdOrSlug(idOrSlug, true);
        return ApiResponse.of(ProductResponse.from(p, productService.loadImages(List.of(p)).get(p.getId())));
    }

    /** One page of products + ONE images query for the whole page. */
    static ApiResponse<List<ProductResponse>> toResponse(ProductService service, Page<Product> page) {
        Map<Long, List<ProductImage>> images = service.loadImages(page.getContent());
        return PageResponse.of(page, p -> ProductResponse.from(p, images.get(p.getId())));
    }
}
