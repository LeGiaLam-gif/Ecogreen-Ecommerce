package com.example.backend.service;

import com.example.backend.entity.Cart;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PendingOrderExpiryProcessor {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final CartService cartService;

    public PendingOrderExpiryProcessor(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                                       OrderService orderService, CartService cartService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.cartService = cartService;
    }

    @Transactional
    public boolean expireOne(Long orderId, LocalDateTime cutoff) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getUser() == null) {
            return false;
        }

        Cart cart = cartService.lockOrCreateCart(order.getUser());
        if (!orderService.cancelIfExpired(orderId, cutoff)) {
            return false;
        }

        List<OrderItem> items = new ArrayList<>(orderItemRepository.findByOrderIdWithProduct(orderId));
        items.sort(Comparator.comparing(item -> item.getProduct().getId()));
        cartService.restoreExpiredOrderItems(cart, items);
        return true;
    }
}
