package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.OrderStatusHistoryRepository;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.inventory.InventoryGateway;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** B05 test helper: an OrderService wired with mocks (no Spring context, no database) and small entity builders. */
final class OrderServiceFixture {

    final OrderRepository orders = mock(OrderRepository.class);
    final OrderItemRepository items = mock(OrderItemRepository.class);
    final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    final CartRepository carts = mock(CartRepository.class);
    final CartItemRepository cartItems = mock(CartItemRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final InventoryGateway inventory = mock(InventoryGateway.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final EntityManager entityManager = mock(EntityManager.class);
    final OrderService service = new OrderService();

    OrderServiceFixture() {
        ReflectionTestUtils.setField(service, "orderRepository", orders);
        ReflectionTestUtils.setField(service, "orderItemRepository", items);
        ReflectionTestUtils.setField(service, "historyRepository", history);
        ReflectionTestUtils.setField(service, "cartRepository", carts);
        ReflectionTestUtils.setField(service, "cartItemRepository", cartItems);
        ReflectionTestUtils.setField(service, "productRepository", products);
        ReflectionTestUtils.setField(service, "paymentRepository", payments);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "inventoryGateway", inventory);
        ReflectionTestUtils.setField(service, "eventPublisher", events);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    /** An order owned by {@code userId} in {@code status}. The row-lock query returns it. */
    Order order(long id, long userId, OrderStatus status) {
        User owner = new User();
        owner.setId(userId);
        Order order = new Order();
        order.setId(id);
        order.setUser(owner);
        ReflectionTestUtils.setField(order, "userId", userId);
        order.setCustomerName("Nguyen Van A");
        order.setCustomerPhone("0900000000");
        order.setShippingAddress("1 Le Loi");
        order.setSubtotal(new BigDecimal("200000"));
        order.setTotalPrice(new BigDecimal("200000"));
        order.setStatus(status);
        when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(order));
        when(orders.findById(id)).thenReturn(Optional.of(order));
        return order;
    }

    Product product(long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(100);
        product.setStatus(Product.Status.ACTIVE);
        return product;
    }

    OrderItem item(Order order, long productId, int quantity, String price) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product(productId, "P" + productId, price));
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    /** What the single "items with products" query returns for this order. */
    void withItems(Order order, OrderItem... orderItems) {
        when(items.findByOrderIdWithProduct(order.getId())).thenReturn(List.of(orderItems));
    }

    Payment payment(Order order, String method, Payment.Status status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(method);
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(status);
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        return payment;
    }
}
