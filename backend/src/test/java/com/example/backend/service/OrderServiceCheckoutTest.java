package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.OrderStatusHistory;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B05: {@code OrderService.createPendingOrder} and its own validation. The legacy {@code OrderService.checkout} was removed by
 * B04; the cart-to-order flow is tested in {@code CheckoutServiceTest}.
 */
class OrderServiceCheckoutTest {

    private OrderServiceFixture f;

    @BeforeEach
    void setUp() {
        f = new OrderServiceFixture();
        User user = new User();
        user.setId(5L);
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
        verifyNoInteractions(f.inventory);
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
