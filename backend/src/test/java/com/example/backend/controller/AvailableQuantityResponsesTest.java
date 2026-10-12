package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.CartResponse;
import com.example.backend.dto.ProductResponse;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B03 regression: the public {@code stockQuantity} of the product list, the product detail and the cart is the AVAILABLE
 * quantity (on hand minus reserved) from the inventory, never the frozen products.stock_quantity column. Every product here
 * carries 99 in the frozen column so a response that still read it would show 99.
 */
class AvailableQuantityResponsesTest {

    private static final int FROZEN = 99;

    private static Product product(long id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Sản phẩm " + id);
        p.setSlug("san-pham-" + id);
        p.setPrice(new BigDecimal("1000"));
        p.setStatus(Product.Status.ACTIVE);
        ReflectionTestUtils.setField(p, "stockQuantity", FROZEN);
        return p;
    }

    @Test
    void productResponse_stockQuantity_equalsTheAvailableQuantity_inBothOverloads() {
        Product p = product(1L);

        assertEquals(7, ProductResponse.from(p, 7).stockQuantity);
        assertEquals(0, ProductResponse.from(p, List.of(), 0).stockQuantity);
    }

    @Test
    void list_carriesAvailable_fromOneBatchedLookup() {
        ProductService service = mock(ProductService.class);
        Product a = product(1L);
        Product b = product(2L);
        List<Product> content = List.of(a, b);
        when(service.loadImages(content)).thenReturn(Map.of());
        when(service.loadAvailability(content)).thenReturn(Map.of(1L, 3, 2L, 0));

        ApiResponse<List<ProductResponse>> response = ProductV1Controller.toResponse(service, new PageImpl<>(content));

        assertEquals(3, response.data().get(0).stockQuantity);
        assertEquals(0, response.data().get(1).stockQuantity);
        verify(service, times(1)).loadAvailability(content);
    }

    @Test
    void detail_carriesAvailable() {
        ProductService service = mock(ProductService.class);
        Product p = product(5L);
        when(service.getByIdOrSlug("san-pham-5", true)).thenReturn(p);
        when(service.loadImages(List.of(p))).thenReturn(Map.of());
        when(service.loadAvailability(List.of(p))).thenReturn(Map.of(5L, 4));
        ProductV1Controller controller = new ProductV1Controller();
        ReflectionTestUtils.setField(controller, "productService", service);

        ApiResponse<ProductResponse> response = controller.get("san-pham-5");

        assertEquals(4, response.data().stockQuantity);
    }

    @Test
    void cart_carriesAvailablePerProduct_andZeroWhenTheLookupHasNoEntry() {
        CartItem withRow = item(10L, product(1L), 2);
        CartItem withoutRow = item(11L, product(2L), 1);

        CartResponse cart = CartResponse.from(1L, List.of(withRow, withoutRow), Map.of(1L, 2));

        assertEquals(2, cart.items.get(0).product.stockQuantity);
        assertEquals(0, cart.items.get(1).product.stockQuantity);
        assertEquals(3, cart.totalItems);
    }

    private static CartItem item(long id, Product product, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }
}
