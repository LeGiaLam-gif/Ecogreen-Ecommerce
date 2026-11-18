package com.example.backend.service;

import com.example.backend.entity.Cart;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingOrderExpiryProcessorTest {

    @Test
    void expirationLocksCartBeforeCancellingThenRestoresOrderLines() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        OrderService orderService = mock(OrderService.class);
        CartService cartService = mock(CartService.class);
        PendingOrderExpiryProcessor processor = new PendingOrderExpiryProcessor(
                orderRepository, orderItemRepository, orderService, cartService);
        User user = new User();
        user.setId(8L);
        Order order = new Order();
        order.setId(12L);
        order.setUser(user);
        Cart cart = new Cart();
        cart.setId(4L);
        OrderItem item = new OrderItem();
        Product product = new Product();
        product.setId(21L);
        item.setProduct(product);
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        when(cartService.lockOrCreateCart(user)).thenReturn(cart);
        when(orderService.cancelIfExpired(12L, LocalDateTime.MIN)).thenReturn(true);
        when(orderItemRepository.findByOrderIdWithProduct(12L)).thenReturn(List.of(item));

        assertTrue(processor.expireOne(12L, LocalDateTime.MIN));

        InOrder inOrder = inOrder(cartService, orderService, orderItemRepository);
        inOrder.verify(cartService).lockOrCreateCart(user);
        inOrder.verify(orderService).cancelIfExpired(12L, LocalDateTime.MIN);
        inOrder.verify(orderItemRepository).findByOrderIdWithProduct(12L);
        inOrder.verify(cartService).restoreExpiredOrderItems(cart, List.of(item));
    }
}
