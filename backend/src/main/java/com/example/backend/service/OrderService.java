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
                           String paymentMethod, String deliveryNote) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found."));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Your cart is empty.");
        }
        if (customerName == null || customerName.isBlank()
                || customerPhone == null || customerPhone.isBlank()
                || shippingAddress == null || shippingAddress.isBlank()) {
            throw new BadRequestException("Customer name, phone and shipping address are required.");
        }

        // 1. Re-validate every product & stock straight from the database.
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product no longer exists."));
            if (product.getStatus() != Product.Status.ACTIVE) {
                throw new BadRequestException("\"" + product.getName() + "\" is no longer available.");
            }
            if (item.getQuantity() > product.getStockQuantity()) {
                throw new BadRequestException("Not enough stock for \"" + product.getName() + "\".");
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        String normalizedMethod = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod : "COD";

        // 2. Create the order (with a shipping/customer snapshot).
        // COD ("cash on delivery") means the customer hasn't paid yet - the order
        // is already CONFIRMED for fulfillment, but payment stays PENDING until
        // the courier collects cash and an admin confirms it. MOCK_PAYMENT keeps
        // the previous behaviour of staying PENDING until "PAY NOW" succeeds.
        Order order = new Order();
        order.setUser(user);
        order.setCustomerName(customerName);
        order.setCustomerPhone(customerPhone);
        order.setShippingAddress(shippingAddress);
        order.setDeliveryNote(deliveryNote);
        order.setTotalPrice(total);
        order.setStatus("COD".equalsIgnoreCase(normalizedMethod) ? Order.Status.CONFIRMED : Order.Status.PENDING);
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
        payment.setPaymentMethod(normalizedMethod);
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
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    /** A user may only view their own order; an admin may view any order. */
    public Order getOwnedOrAdmin(Long orderId, User user, boolean isAdmin) {
        Order order = getById(orderId);
        if (!isAdmin && !order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not have access to this order.");
        }
        return order;
    }

    public Order updateStatus(Long orderId, String status) {
        Order order = getById(orderId);
        order.setStatus(Order.Status.valueOf(status));
        return orderRepository.save(order);
    }

    /**
     * Admin-only action: mark a COD order as "cash collected on delivery".
     * Only valid for orders whose payment method is COD and whose payment is
     * still PENDING - this is intentionally NOT exposed for MOCK_PAYMENT
     * orders, which follow the online "PAY NOW" flow instead.
     */
    @Transactional
    public Order confirmCodPayment(Long orderId) {
        Order order = getById(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));

        if (!"COD".equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new BadRequestException("This action only applies to COD orders.");
        }
        if (payment.getStatus() == Payment.Status.SUCCESS) {
            throw new BadRequestException("Payment for this order has already been confirmed.");
        }

        payment.setStatus(Payment.Status.SUCCESS);
        paymentRepository.save(payment);

        order.setStatus(Order.Status.PAID);
        return orderRepository.save(order);
    }
}
