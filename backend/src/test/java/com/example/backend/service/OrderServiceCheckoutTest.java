package com.example.backend.service;

import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.OrderStatusHistory;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.service.inventory.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B05: the legacy checkout rebuilt on createPendingOrder + InventoryGateway, and createPendingOrder's own validation. */
class OrderServiceCheckoutTest {

    private OrderServiceFixture f;
    private User user;
    private Cart cart;

    @BeforeEach
    void setUp() {
        f = new OrderServiceFixture();
        user = new User();
        user.setId(5L);
        cart = new Cart();
        cart.setId(3L);
        when(f.carts.findByUserId(5L)).thenReturn(Optional.of(cart));
        when(f.users.getReferenceById(5L)).thenReturn(user);
        // persisting an order assigns it id 100
        when(f.orders.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(100L);
            }
            return order;
        });
    }

    private CartItem cartItem(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    /** Cart holds product 11 (x1, 50 000) first and product 10 (x2, 100 000) second. */
    private void cartWithTwoProducts() {
        Product p11 = f.product(11L, "Hộp cơm", "50000");
        Product p10 = f.product(10L, "Bình nước", "100000");
        when(f.cartItems.findByCartId(3L)).thenReturn(List.of(cartItem(p11, 1), cartItem(p10, 2)));
        when(f.products.findAllById(any())).thenReturn(List.of(p11, p10));
        when(f.products.getReferenceById(10L)).thenReturn(p10);
        when(f.products.getReferenceById(11L)).thenReturn(p11);
    }

    @Test
    void legacyCheckout_happyPath_createsPendingOrder_reservesStockInProductOrder_andClearsCart() {
        cartWithTwoProducts();

        Order order = f.service.checkout(user, "An", "0900000000", "1 Le Loi", "MOMO");

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(new BigDecimal("250000"), order.getTotalPrice());
        assertEquals(new BigDecimal("250000"), order.getSubtotal());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getDiscountAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getShippingFee()));

        InOrder reserved = inOrder(f.inventory);
        reserved.verify(f.inventory).reserve(10L, 2, "ORDER", 100L);   // lower product id first, whatever the cart order
        reserved.verify(f.inventory).reserve(11L, 1, "ORDER", 100L);

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(f.payments).save(payment.capture());
        assertEquals(Payment.Status.PENDING, payment.getValue().getStatus());
        assertEquals("MOMO", payment.getValue().getPaymentMethod());
        assertEquals(new BigDecimal("250000"), payment.getValue().getAmount());

        verify(f.cartItems).deleteAllByCartId(3L);

        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(f.history).save(history.capture());
        assertNull(history.getValue().getOldStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, history.getValue().getNewStatus());
        assertEquals(5L, history.getValue().getChangedBy());
    }

    @Test
    void legacyCheckout_paymentMethodDefaultsToCod() {
        cartWithTwoProducts();

        f.service.checkout(user, "An", "0900000000", "1 Le Loi", null);

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(f.payments).save(payment.capture());
        assertEquals("COD", payment.getValue().getPaymentMethod());
    }

    @Test
    void legacyCheckout_oneItemInsufficient_rollsBackOrderAndStock() {
        cartWithTwoProducts();
        // product 10 can be reserved, product 11 is sold out
        doThrow(new InsufficientStockException(11L, 1)).when(f.inventory).reserve(11L, 1, "ORDER", 100L);

        assertThrows(InsufficientStockException.class,
                () -> f.service.checkout(user, "An", "0900000000", "1 Le Loi", "COD"));

        // Nothing after the failed reservation ran: no payment row, the cart is not cleared.
        verifyNoInteractions(f.payments);
        verify(f.cartItems, never()).deleteAllByCartId(anyLong());
        // The order, its items, the history row and the stock taken for product 10 are rolled back by the transaction: the
        // whole method is one @Transactional unit. (Proof against a real database is the PostgreSQL-gated test, not run here.)
        assertTrue(transactional("checkout", User.class, String.class, String.class, String.class, String.class));
    }

    @Test
    void legacyCheckout_emptyCart_isRejected() {
        when(f.cartItems.findByCartId(3L)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> f.service.checkout(user, "An", "0900000000", "1 Le Loi", "COD"));

        verifyNoInteractions(f.inventory, f.payments);
    }

    @Test
    void legacyCheckout_blankRecipientFields_areRejected_beforeAnyWrite() {
        cartWithTwoProducts();

        assertThrows(BadRequestException.class, () -> f.service.checkout(user, " ", "0900000000", "1 Le Loi", "COD"));
        assertThrows(BadRequestException.class, () -> f.service.checkout(user, "An", null, "1 Le Loi", "COD"));
        assertThrows(BadRequestException.class, () -> f.service.checkout(user, "An", "0900000000", "", "COD"));

        verifyNoInteractions(f.inventory, f.payments);
        verify(f.orders, never()).save(any(Order.class));
    }

    @Test
    void legacyCheckout_inactiveProduct_isRejected() {
        Product inactive = f.product(10L, "Bình nước", "100000");
        inactive.setStatus(Product.Status.INACTIVE);
        when(f.cartItems.findByCartId(3L)).thenReturn(List.of(cartItem(inactive, 1)));
        when(f.products.findAllById(any())).thenReturn(List.of(inactive));

        assertThrows(BadRequestException.class, () -> f.service.checkout(user, "An", "0900000000", "1 Le Loi", "COD"));

        verifyNoInteractions(f.inventory);
    }

    @Test
    void legacyCheckout_productDeleted_isNotFound() {
        Product gone = f.product(10L, "Bình nước", "100000");
        when(f.cartItems.findByCartId(3L)).thenReturn(List.of(cartItem(gone, 1)));
        when(f.products.findAllById(any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> f.service.checkout(user, "An", "0900000000", "1 Le Loi", "COD"));
    }

    @Test
    void legacyCheckout_priceComesFromTheDatabase_notFromTheCart() {
        cartWithTwoProducts();

        f.service.checkout(user, "An", "0900000000", "1 Le Loi", "COD");

        // OrderItem prices are snapshots of the product row (100 000 and 50 000), recorded through saveAll
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<com.example.backend.entity.OrderItem>> saved = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(f.items).saveAll(saved.capture());
        BigDecimal sum = BigDecimal.ZERO;
        for (com.example.backend.entity.OrderItem item : saved.getValue()) {
            sum = sum.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        assertEquals(new BigDecimal("250000"), sum);
    }

    // ---- createPendingOrder ----

    private static List<OrderItemDraft> drafts() {
        return List.of(new OrderItemDraft(10L, 2, new BigDecimal("100000")));
    }

    @Test
    void createPendingOrder_valid_persistsOrderItemsAndFirstHistoryRow_withoutTouchingStockOrCart() {
        Product p10 = f.product(10L, "Bình nước", "100000");
        when(f.products.getReferenceById(10L)).thenReturn(p10);

        Order order = f.service.createPendingOrder(5L, drafts(), new BigDecimal("200000"), new BigDecimal("20000"),
                new BigDecimal("30000"), new BigDecimal("210000"), "An", "0900000000", "1 Le Loi", " SALE10 ");

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(new BigDecimal("210000"), order.getTotalPrice());
        assertEquals("SALE10", order.getDiscountCode());
        verify(f.history).save(any(OrderStatusHistory.class));
        verifyNoInteractions(f.inventory, f.cartItems, f.carts);
    }

    @Test
    void createPendingOrder_totalNotEqualSubtotalMinusDiscountPlusShipping_isRejected() {
        assertThrows(BusinessRuleViolationException.class, () -> f.service.createPendingOrder(5L, drafts(),
                new BigDecimal("200000"), new BigDecimal("20000"), new BigDecimal("30000"), new BigDecimal("200000"),
                "An", "0900000000", "1 Le Loi", null));

        verify(f.orders, never()).save(any(Order.class));
    }

    @Test
    void createPendingOrder_subtotalNotEqualToTheLines_isRejected() {
        assertThrows(BusinessRuleViolationException.class, () -> f.service.createPendingOrder(5L, drafts(),
                new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1"),
                "An", "0900000000", "1 Le Loi", null));
    }

    @Test
    void createPendingOrder_discountLargerThanSubtotal_isRejected() {
        assertThrows(BusinessRuleViolationException.class, () -> f.service.createPendingOrder(5L, drafts(),
                new BigDecimal("200000"), new BigDecimal("300000"), BigDecimal.ZERO, new BigDecimal("-100000"),
                "An", "0900000000", "1 Le Loi", null));
    }

    @Test
    void createPendingOrder_badInput_isRejected() {
        assertThrows(BadRequestException.class, () -> f.service.createPendingOrder(5L, List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "An", "0900000000", "x", null));
        assertThrows(BadRequestException.class, () -> f.service.createPendingOrder(5L,
                List.of(new OrderItemDraft(10L, 0, new BigDecimal("1"))),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "An", "0900000000", "x", null));
        assertThrows(BadRequestException.class, () -> f.service.createPendingOrder(5L, drafts(),
                new BigDecimal("200000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200000"), "", "0900000000", "x", null));
        assertThrows(BadRequestException.class, () -> f.service.createPendingOrder(null, drafts(),
                new BigDecimal("200000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200000"), "An", "0900000000", "x", null));
    }

    private static boolean transactional(String method, Class<?>... types) {
        try {
            return OrderService.class.getMethod(method, types).isAnnotationPresent(Transactional.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everyOrderWritingMethod_isTransactional() {
        assertTrue(transactional("createPendingOrder", Long.class, List.class, BigDecimal.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class, String.class, String.class, String.class, String.class));
        assertTrue(transactional("transitionStatus", Long.class, OrderStatus.class, Long.class, String.class));
        assertTrue(transactional("cancelOwnOrder", Long.class, Long.class, String.class));
        assertTrue(transactional("updateStatus", Long.class, String.class, Long.class));
    }
}
