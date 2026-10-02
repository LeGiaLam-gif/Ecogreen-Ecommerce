package com.example.backend.service;

import com.example.backend.entity.*;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Checkout is one logical transaction: validate cart -> validate stock (again,
 * from the database, never trusting the frontend) -> create order -> create
 * order_items with the CURRENT product price (which becomes the historical
 * price) -> decrease stock -> create a pending payment -> clear the cart.
 * If anything fails, nothing is committed.
 */
@Service
public class OrderService {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Transactional
    public Order checkout(User user, String customerName, String customerPhone, String shippingAddress,
                           String paymentMethod) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng."));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Giỏ hàng của bạn đang trống.");
        }
        if (customerName == null || customerName.isBlank()
                || customerPhone == null || customerPhone.isBlank()
                || shippingAddress == null || shippingAddress.isBlank()) {
            throw new BadRequestException("Vui lòng nhập đầy đủ tên người nhận, số điện thoại và địa chỉ giao hàng.");
        }

        // 1. Re-validate every product & stock straight from the database.
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không còn tồn tại."));
            if (product.getStatus() != Product.Status.ACTIVE) {
                throw new BadRequestException("Sản phẩm \"" + product.getName() + "\" hiện không còn kinh doanh.");
            }
            if (item.getQuantity() > product.getStockQuantity()) {
                throw new BadRequestException("Không đủ số lượng tồn kho cho sản phẩm \"" + product.getName() + "\".");
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        // 2. Create the order (with a shipping/customer snapshot).
        Order order = new Order();
        order.setUser(user);
        order.setCustomerName(customerName);
        order.setCustomerPhone(customerPhone);
        order.setShippingAddress(shippingAddress);
        order.setTotalPrice(total);
        order.setStatus(Order.Status.PENDING);
        order = orderRepository.save(order);

        // 3. Create order_items at the CURRENT price, and decrease stock.
        for (CartItem item : cartItems) {
            Product product = item.getProduct();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPrice(product.getPrice()); // historical price snapshot
            orderItemRepository.save(orderItem);

            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
        }

        // 4. Create a pending payment for the mock payment flow.
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentMethod != null ? paymentMethod : "COD");
        payment.setAmount(total);
        payment.setStatus(Payment.Status.PENDING);
        paymentRepository.save(payment);

        // 5. Only clear the cart once the order + items + stock update succeeded.
        cartItemRepository.deleteAllByCartId(cart.getId());

        return order;
    }

    public List<OrderItem> getItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    public List<Order> getOrdersForUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + id));
    }

    /** A user may only view their own order; an admin may view any order. */
    public Order getOwnedOrAdmin(Long orderId, User user, boolean isAdmin) {
        Order order = getById(orderId);
        if (!isAdmin && !order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn hàng này.");
        }
        return order;
    }

    public Order updateStatus(Long orderId, String status) {
        Order order = getById(orderId);
        order.setStatus(Order.Status.valueOf(status));
        return orderRepository.save(order);
    }
}
