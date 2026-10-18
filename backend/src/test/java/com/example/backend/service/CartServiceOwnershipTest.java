package com.example.backend.service;

import com.example.backend.dto.CartResponse;
import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.service.inventory.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B04: the cart service with mocked repositories. Ownership of every line, the cart row lock every change takes, and a cart
 * read that costs a fixed number of statements whatever the number of lines.
 */
class CartServiceOwnershipTest {

    private CartRepository carts;
    private CartItemRepository items;
    private InventoryService inventory;
    private ProductService products;
    private CartService service;

    private User owner;
    private User stranger;
    private Cart ownerCart;
    private CartItem ownerItem;

    @BeforeEach
    void setUp() {
        carts = mock(CartRepository.class);
        items = mock(CartItemRepository.class);
        inventory = mock(InventoryService.class);
        products = mock(ProductService.class);
        service = new CartService();
        ReflectionTestUtils.setField(service, "cartRepository", carts);
        ReflectionTestUtils.setField(service, "cartItemRepository", items);
        ReflectionTestUtils.setField(service, "productService", products);
        ReflectionTestUtils.setField(service, "inventoryService", inventory);

        owner = user(1L);
        stranger = user(2L);
        ownerCart = new Cart();
        ownerCart.setId(50L);
        ownerCart.setUser(owner);
        ownerItem = line(10L, ownerCart, product(7L, "Bình nước", "100000"), 2);
        when(items.findByIdWithProductAndCart(10L)).thenReturn(Optional.of(ownerItem));
        when(carts.findByUserIdForUpdate(1L)).thenReturn(Optional.of(ownerCart));
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Product product(long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStatus(Product.Status.ACTIVE);
        return product;
    }

    private static CartItem line(long id, Cart cart, Product product, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    // ---- ownership ----

    @Test
    void cart_itemOwnership_enforced_updateOfAForeignLineIsNotFound_andChangesNothing() {
        when(inventory.available(7L)).thenReturn(50);

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class, () -> service.updateQuantity(stranger, 10L, 5));

        assertTrue(e.getMessage().contains("#10"));
        assertEquals(2, ownerItem.getQuantity());
        verify(items, never()).save(any());
    }

    @Test
    void cart_itemOwnership_enforced_removalOfAForeignLineIsNotFound_andDeletesNothing() {
        assertThrows(ResourceNotFoundException.class, () -> service.removeItem(stranger, 10L));

        verify(items, never()).delete(any());
    }

    @Test
    void cart_itemOwnership_theOwnerMayChangeAndRemoveTheirLine() {
        when(inventory.available(7L)).thenReturn(50);
        when(items.findByCartIdWithProduct(50L)).thenReturn(List.of(ownerItem));
        when(inventory.availableFor(anyCollection())).thenReturn(Map.of(7L, 50));

        CartResponse updated = service.updateQuantity(owner, 10L, 5);

        assertEquals(5, ownerItem.getQuantity());
        verify(items).save(ownerItem);
        assertEquals(new BigDecimal("500000"), updated.subtotal);

        service.removeItem(owner, 10L);
        verify(items).delete(ownerItem);
    }

    @Test
    void everyCartChange_takesTheCartRowLockBeforeReadingTheLine() {
        when(inventory.available(7L)).thenReturn(50);

        service.updateQuantity(owner, 10L, 3);
        service.removeItem(owner, 10L);

        InOrder updateThenRemove = inOrder(carts, items);
        updateThenRemove.verify(carts).findByUserIdForUpdate(1L);
        updateThenRemove.verify(items).findByIdWithProductAndCart(10L);
        updateThenRemove.verify(carts).findByUserIdForUpdate(1L);
        updateThenRemove.verify(items).findByIdWithProductAndCart(10L);
    }

    @Test
    void addItem_takesTheCartRowLock_andNeverUsesTheUnlockedLookup() {
        Product product = product(7L, "Bình nước", "100000");
        when(products.getById(7L)).thenReturn(product);
        when(inventory.available(7L)).thenReturn(5);
        when(items.findByCartIdAndProductId(50L, 7L)).thenReturn(Optional.empty());

        service.addItem(owner, 7L, 2);

        verify(carts).findByUserIdForUpdate(1L);
        verify(carts, never()).findByUserId(any());
    }

    @Test
    void clearCart_emptiesOnlyTheCallersOwnCart() {
        service.clearCart(owner);

        verify(items).deleteAllByCartId(50L);
        verify(items, never()).deleteAllByCartId(60L);
    }

    // ---- reading ----

    @Test
    void getCart_readsTheLinesWithTheirProductsOnce_andTheAvailabilityInOneBatch() {
        Cart cart = new Cart();
        cart.setId(60L);
        cart.setUser(owner);
        CartItem first = line(1L, cart, product(7L, "A", "10000"), 3);
        CartItem second = line(2L, cart, product(8L, "B", "20000"), 1);
        CartItem third = line(3L, cart, product(9L, "C", "30000"), 2);
        when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(items.findByCartIdWithProduct(60L)).thenReturn(List.of(first, second, third));
        when(inventory.availableFor(anyCollection())).thenReturn(Map.of(7L, 2, 8L, 0, 9L, 10));

        CartResponse response = service.getCart(1L);

        // cart_response_includesAvailableQuantity
        assertEquals(List.of(2, 0, 10), response.items.stream().map(i -> i.availableQuantity).toList());
        assertEquals(new BigDecimal("110000"), response.subtotal); // 3 x 10 000 + 1 x 20 000 + 2 x 30 000
        assertEquals(6, response.totalItems);
        // a fixed number of statements however many lines: the cart row, the lines + products (one join), the stock (one batch)
        verify(carts, times(1)).findByUserId(1L);
        verify(items, times(1)).findByCartIdWithProduct(60L);
        verify(inventory, times(1)).availableFor(anyCollection());
        verify(items, never()).findByCartId(any());
        verify(products, never()).getById(any());
    }

    @Test
    void getCart_ofAUserWithoutACartRow_isEmpty_andCreatesNothing() {
        when(carts.findByUserId(3L)).thenReturn(Optional.empty());

        CartResponse response = service.getCart(3L);

        assertTrue(response.items.isEmpty());
        assertEquals(0, response.totalItems);
        verify(carts, never()).save(any());
    }
}
