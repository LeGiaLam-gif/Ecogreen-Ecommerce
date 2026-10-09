package com.example.backend.service;

import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductImageRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.storage.ImageSniffer;
import com.example.backend.storage.ObjectStorageClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductImageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String FILE_URL_PREFIX = "/api/v1/files/";

    @Autowired private ProductRepository productRepository;
    @Autowired private ProductImageRepository productImageRepository;
    @Autowired private ObjectStorageClient storage;

    /** Validates by size and magic bytes, stores under a random server-side name, appends to the gallery. */
    public ProductImage upload(Long productId, byte[] content, String altText) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("Vui lòng chọn một tệp ảnh.");
        }
        if (content.length > MAX_BYTES) {
            throw new BadRequestException("Ảnh vượt quá dung lượng cho phép (tối đa 5 MB).");
        }
        if (altText != null && altText.length() > 255) {
            throw new BadRequestException("Mô tả ảnh tối đa 255 ký tự.");
        }
        ImageSniffer.Detected type = ImageSniffer.detect(content);
        if (type == null) {
            throw new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP hợp lệ.");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm #" + productId));

        List<ProductImage> existing = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        int nextOrder = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1;

        String key = storage.store(content, type.extension());
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setStorageKey(key);
        image.setImageUrl(FILE_URL_PREFIX + key);
        image.setAltText(altText == null || altText.isBlank() ? null : altText.trim());
        image.setSortOrder(nextOrder);
        try {
            return productImageRepository.save(image);
        } catch (RuntimeException e) {
            storage.delete(key); // do not leave an orphan file behind a failed insert
            throw e;
        }
    }

    /** The image must belong to {@code productId}; the stored file is removed with the row. */
    public void delete(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .filter(i -> i.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh #" + imageId));
        productImageRepository.delete(image);
        if (image.getStorageKey() != null) {
            storage.delete(image.getStorageKey());
        }
    }
}
