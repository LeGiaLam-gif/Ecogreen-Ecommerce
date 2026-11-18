package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.ProductReviewRequest;
import com.example.backend.dto.ProductReviewResponse;
import com.example.backend.dto.ProductReviewsResponse;
import com.example.backend.entity.Product;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.ProductReviewService;
import com.example.backend.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductReviewV1Controller {

    @Autowired private ProductReviewService reviewService;
    @Autowired private ProductService productService;

    @GetMapping("/{idOrSlug}/reviews")
    public ApiResponse<ProductReviewsResponse> list(@PathVariable String idOrSlug, HttpServletRequest request) {
        Product product = productService.getByIdOrSlug(idOrSlug, true);
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        return ApiResponse.of(reviewService.list(product.getId(), current == null ? null : current.getUserId()));
    }

    @PostMapping("/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductReviewResponse> create(@PathVariable Long productId,
                                                     @Valid @RequestBody ProductReviewRequest body,
                                                     HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        return ApiResponse.of(reviewService.create(current.getUserId(), productId, body));
    }
}
