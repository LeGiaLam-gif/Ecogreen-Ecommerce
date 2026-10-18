package com.example.backend.service;

import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.service.inventory.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B03: the cart checks the AVAILABLE quantity of the inventory (on hand minus reserved), never the frozen
 * products.stock_quantity column (every product here carries 99 there). Mocked repositories, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceAvailabilityTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductService productService;
    @Mock private InventoryService inventoryService;
    @InjectMocks private CartService service;

    private User user;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        cart = new Cart();
        cart.setId(50L);
        cart.setUser(user);
        product = new Product();
        product.setId(7L);
        ReflectionTestUtils.setField(product, "stockQuantity", 99); // frozen value: must be ignored
        when(cartRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(productService.getById(7L)).thenReturn(product);
        when(cartItemRepository.findByCartIdAndProductId(50L, 7L)).thenReturn(Optional.empty());
    }

    @Test
    void addItem_moreThanAvailable_isRejectedWithTheAvailableCount_notTheFrozenOne() {
        when(inventoryService.available(7L)).thenReturn(2);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.addItem(user, 7L, 3));

        assertEquals("Kho chỉ còn 2 sản phẩm.", ex.getMessage());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_withinAvailable_savesTheItem() {
        when(inventoryService.available(7L)).thenReturn(5);

        service.addItem(user, 7L, 3);

        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addItem_countsWhatTheCartAlreadyHolds() {
        CartItem existing = new CartItem();
        existing.setCart(cart);
        existing.setProduct(product);
        existing.setQuantity(2);
        when(cartItemRepository.findByCartIdAndProductId(50L, 7L)).thenReturn(Optional.of(existing));
        when(inventoryService.available(7L)).thenReturn(3);

        assertThrows(BadRequestException.class, () -> service.addItem(user, 7L, 2)); // 2 + 2 > 3
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void updateQuantity_moreThanAvailable_isRejected() {
        CartItem item = new CartItem();
        item.setId(10L);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(1);
        when(cartItemRepository.findByIdWithProductAndCart(10L)).thenReturn(Optional.of(item));
        when(inventoryService.available(7L)).thenReturn(1);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.updateQuantity(user, 10L, 2));

        assertEquals("Kho chỉ còn 1 sản phẩm.", ex.getMessage());
        assertEquals(1, item.getQuantity());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void availabilityFor_isOneBatchedInventoryCall() {
        CartItem a = new CartItem();
        a.setProduct(product);
        Product other = new Product();
        other.setId(8L);
        CartItem b = new CartItem();
        b.setProduct(other);
        when(inventoryService.availableFor(List.of(7L, 8L))).thenReturn(Map.of(7L, 2, 8L, 0));

        assertEquals(Map.of(7L, 2, 8L, 0), service.availabilityFor(List.of(a, b)));
        verify(inventoryService, times(1)).availableFor(anyCollection());
    }
}
