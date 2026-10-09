package com.example.backend.service;

import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductImageRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.storage.ImageSnifferTest;
import com.example.backend.storage.ObjectStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductImageServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ObjectStorageClient storage;
    @InjectMocks private ProductImageService service;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(7L);
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(storage.store(any(byte[].class), anyString())).thenReturn("0123456789abcdef0123456789abcdef.png");
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static byte[] pngOfSize(int size) {
        byte[] b = Arrays.copyOf(ImageSnifferTest.png(), size);
        return b;
    }

    @Test
    void upload_nonImageBytesWithImageExtension_isRejected() {
        // The service never sees the client's file name, so ".png" on an SVG/HTML/EXE payload cannot help the caller.
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        byte[] exe = new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        for (byte[] payload : List.of(svg, html, exe)) {
            assertThrows(BadRequestException.class, () -> service.upload(7L, payload, null));
        }
        verify(storage, never()).store(any(byte[].class), anyString());
        verify(productImageRepository, never()).save(any(ProductImage.class));
    }

    @Test
    void upload_oversize_isRejected_exactlyFiveMbIsAccepted() {
        assertThrows(BadRequestException.class, () -> service.upload(7L, pngOfSize(5 * 1024 * 1024 + 1), null));
        verify(storage, never()).store(any(byte[].class), anyString());

        ProductImage ok = service.upload(7L, pngOfSize(5 * 1024 * 1024), null);
        assertEquals("/api/v1/files/0123456789abcdef0123456789abcdef.png", ok.getImageUrl());
    }

    @Test
    void upload_emptyOrNull_isRejected() {
        assertThrows(BadRequestException.class, () -> service.upload(7L, new byte[0], null));
        assertThrows(BadRequestException.class, () -> service.upload(7L, null, null));
    }

    @Test
    void upload_valid_storesWithDetectedExtension_andAppendsToGallery() {
        ProductImage existing = new ProductImage();
        existing.setSortOrder(3);
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(existing));

        ProductImage saved = service.upload(7L, ImageSnifferTest.jpeg(), "  Bình giữ nhiệt  ");

        verify(storage).store(any(byte[].class), eq("jpg"));
        assertEquals(4, saved.getSortOrder());
        assertEquals("Bình giữ nhiệt", saved.getAltText());
        assertEquals("0123456789abcdef0123456789abcdef.png", saved.getStorageKey());
        assertEquals(product, saved.getProduct());
    }

    @Test
    void upload_unknownProduct_isNotFound_andNothingIsStored() {
        assertThrows(ResourceNotFoundException.class, () -> service.upload(99L, ImageSnifferTest.png(), null));
        verify(storage, never()).store(any(byte[].class), anyString());
    }

    @Test
    void upload_failedInsert_removesTheStoredFile() {
        when(productImageRepository.save(any(ProductImage.class))).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> service.upload(7L, ImageSnifferTest.png(), null));
        verify(storage).delete("0123456789abcdef0123456789abcdef.png");
    }

    @Test
    void delete_removesRowAndFile_onlyForTheOwningProduct() {
        ProductImage image = new ProductImage();
        image.setId(11L);
        image.setProduct(product);
        image.setStorageKey("0123456789abcdef0123456789abcdef.png");
        when(productImageRepository.findById(11L)).thenReturn(Optional.of(image));

        assertThrows(ResourceNotFoundException.class, () -> service.delete(8L, 11L)); // other product's id
        verify(productImageRepository, never()).delete(any(ProductImage.class));

        service.delete(7L, 11L);
        verify(productImageRepository).delete(image);
        verify(storage).delete("0123456789abcdef0123456789abcdef.png");
    }

    @Test
    void delete_legacyCopiedImageWithoutKey_doesNotTouchStorage() {
        ProductImage legacy = new ProductImage();
        legacy.setId(12L);
        legacy.setProduct(product);
        legacy.setImageUrl("bottle.jpg");
        when(productImageRepository.findById(12L)).thenReturn(Optional.of(legacy));

        service.delete(7L, 12L);

        verify(productImageRepository).delete(legacy);
        verify(storage, never()).delete(anyString());
    }
}
