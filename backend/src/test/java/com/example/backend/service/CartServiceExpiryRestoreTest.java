package com.example.backend.service;

import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.Product;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.service.inventory.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceExpiryRestoreTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductService productService;
    @Mock private InventoryService inventoryService;
    @InjectMocks private CartService cartService;

    @Test
    void expiredOrderLineMergesIntoCartWithoutApplyingCurrentStockLimit() {
        Cart cart = new Cart();
        cart.setId(50L);
        Product product = new Product();
        product.setId(7L);
        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(product);
        orderItem.setQuantity(3);
        CartItem existing = new CartItem();
        existing.setCart(cart);
        existing.setProduct(product);
        existing.setQuantity(2);
        when(cartItemRepository.findByCartIdAndProductId(50L, 7L)).thenReturn(Optional.of(existing));

        cartService.restoreExpiredOrderItems(cart, java.util.List.of(orderItem));

        assertEquals(5, existing.getQuantity());
        verify(cartItemRepository).save(any(CartItem.class));
    }
}
