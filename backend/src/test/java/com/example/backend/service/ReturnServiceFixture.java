package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Product;
import com.example.backend.entity.Return;
import com.example.backend.entity.ReturnItem;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.entity.User;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.RefundRepository;
import com.example.backend.repository.ReturnImageRepository;
import com.example.backend.repository.ReturnItemRepository;
import com.example.backend.repository.ReturnRepository;
import com.example.backend.repository.ReturnStatusHistoryRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.inventory.InventoryGateway;
import com.example.backend.storage.ObjectStorageClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** B07 test helper: a ReturnService wired with mocks (no Spring context, no database) and small entity builders. */
final class ReturnServiceFixture {

    final ReturnRepository returns = mock(ReturnRepository.class);
    final ReturnItemRepository returnItems = mock(ReturnItemRepository.class);
    final ReturnImageRepository returnImages = mock(ReturnImageRepository.class);
    final ReturnStatusHistoryRepository history = mock(ReturnStatusHistoryRepository.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final RefundRepository refunds = mock(RefundRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final OrderService orderService = mock(OrderService.class);
    final InventoryGateway inventory = mock(InventoryGateway.class);
    final ObjectStorageClient storage = mock(ObjectStorageClient.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final ReturnService service = new ReturnService();

    ReturnServiceFixture() {
        ReflectionTestUtils.setField(service, "returnRepository", returns);
        ReflectionTestUtils.setField(service, "returnItemRepository", returnItems);
        ReflectionTestUtils.setField(service, "returnImageRepository", returnImages);
        ReflectionTestUtils.setField(service, "returnHistoryRepository", history);
        ReflectionTestUtils.setField(service, "orderRepository", orders);
        ReflectionTestUtils.setField(service, "orderItemRepository", orderItems);
        ReflectionTestUtils.setField(service, "paymentRepository", payments);
        ReflectionTestUtils.setField(service, "refundRepository", refunds);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "inventoryGateway", inventory);
        ReflectionTestUtils.setField(service, "storage", storage);
        ReflectionTestUtils.setField(service, "eventPublisher", events);

        when(returns.save(any(Return.class))).thenAnswer(invocation -> {
            Return saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(50L);
            }
            return saved;
        });
        when(returnItems.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(returnImages.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.getReferenceById(anyLong())).thenAnswer(invocation -> user(invocation.getArgument(0)));
        AtomicInteger keys = new AtomicInteger();
        when(storage.store(any(byte[].class), anyString()))
                .thenAnswer(invocation -> String.format("%032x.jpg", keys.incrementAndGet()));
    }

    static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }

    /** An order owned by {@code userId} in {@code status}; the row-lock query returns it. */
    Order order(long id, long userId, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user(userId));
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("500000"));
        when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(order));
        return order;
    }

    OrderItem orderItem(Order order, long id, long productId, int quantity, String price) {
        Product product = new Product();
        product.setId(productId);
        product.setName("Sản phẩm " + productId);
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    /** What the "items of one order with their products" query returns. */
    void withOrderItems(Order order, OrderItem... items) {
        when(orderItems.findByOrderIdWithProduct(order.getId())).thenReturn(List.of(items));
    }

    Return returnRow(long id, Order order, ReturnStatus status) {
        Return request = new Return();
        request.setId(id);
        request.setOrder(order);
        request.setUser(order.getUser());
        request.setReason("Sản phẩm bị lỗi");
        request.setStatus(status);
        when(returns.findByIdForUpdate(id)).thenReturn(Optional.of(request));
        when(returns.findByIdWithUser(id)).thenReturn(Optional.of(request));
        return request;
    }

    static ReturnItem returnItem(Return request, OrderItem orderItem, int quantity) {
        ReturnItem item = new ReturnItem();
        item.setReturnRequest(request);
        item.setOrderItem(orderItem);
        item.setQuantity(quantity);
        return item;
    }

    /** What the "items of these returns" query returns (the service asks it with the id of the return). */
    void withReturnItems(ReturnItem... items) {
        when(returnItems.findByReturnIdInWithProduct(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(items));
    }

    Payment payment(Order order, Payment.Status status, String amount) {
        Payment payment = new Payment();
        payment.setId(900L);
        payment.setOrder(order);
        payment.setPaymentMethod("VNPAY");
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(status);
        when(payments.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(payment));
        return payment;
    }

    static byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    }

    static byte[] png() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    }
}
