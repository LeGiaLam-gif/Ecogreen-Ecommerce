package com.example.backend.service;

import com.example.backend.dto.OrderResponse;
import com.example.backend.dto.OrderStatusHistoryResponse;
import com.example.backend.entity.*;
import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.*;
import com.example.backend.service.inventory.InventoryGateway;
import com.example.backend.service.inventory.OrderInventoryPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orders: creation, the status state machine, cancellation and the order queries.
 *
 * <p><b>One writer.</b> {@link #transitionStatus} is the ONLY method that changes {@code orders.status}
 * (OrderStatusSingleWriterTest scans the source to keep it that way). It locks the row, validates the transition table in
 * {@link OrderStatus}, applies the inventory side effect through {@link InventoryGateway}, writes one history row and
 * publishes the events.
 */
@Service
public class OrderService {

    private static final String INVENTORY_REF_TYPE = "ORDER";
    private static final String COD = "COD";
    private static final String REFUND_REQUIRED = "REFUND_REQUIRED";
    private static final int NOTE_MAX = 500;

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderStatusHistoryRepository historyRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InventoryGateway inventoryGateway;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @PersistenceContext private EntityManager entityManager;

    // ------------------------------------------------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------------------------------------------------

    /**
     * TEMPORARY-COMPAT(B05): the legacy checkout behind {@code POST /api/orders}; removed when B04 ships
     * {@code POST /api/v1/checkout}.
     *
     * <p>One transaction: validate the cart, create the PENDING_PAYMENT order with its items and first history row, reserve
     * the stock through the inventory port (atomic conditional UPDATE), create the pending payment, clear the cart. If any
     * product runs out, {@code InsufficientStockException} rolls back all of it, including the order.
     */
    @Transactional
    public Order checkout(User user, String customerName, String customerPhone, String shippingAddress,
                           String paymentMethod) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng."));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Giỏ hàng của bạn đang trống.");
        }
        if (isBlank(customerName) || isBlank(customerPhone) || isBlank(shippingAddress)) {
            throw new BadRequestException("Vui lòng nhập đầy đủ tên người nhận, số điện thoại và địa chỉ giao hàng.");
        }

        // 1. Re-read every product from the database (one query); prices and status never come from the client.
        List<Long> productIds = cartItems.stream().map(ci -> ci.getProduct().getId()).toList();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderItemDraft> drafts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            Product product = products.get(item.getProduct().getId());
            if (product == null) {
                throw new ResourceNotFoundException("Sản phẩm không còn tồn tại.");
            }
            if (product.getStatus() != Product.Status.ACTIVE) {
                throw new BadRequestException("Sản phẩm \"" + product.getName() + "\" hiện không còn kinh doanh.");
            }
            drafts.add(new OrderItemDraft(product.getId(), item.getQuantity(), product.getPrice()));
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        // Reserve in product-id order so two checkouts that share products always lock rows in the same order.
        drafts.sort(Comparator.comparing(OrderItemDraft::productId));

        // 2. Order + items + first history row (no stock movement yet).
        Order order = createPendingOrder(user.getId(), drafts, total, BigDecimal.ZERO, BigDecimal.ZERO, total,
                customerName, customerPhone, shippingAddress, null);

        // 3. Take the stock. Atomic per product; an InsufficientStockException aborts and rolls back everything above.
        for (OrderItemDraft draft : drafts) {
            inventoryGateway.reserve(draft.productId(), draft.quantity(), INVENTORY_REF_TYPE, order.getId());
        }

        // 4. Pending payment for the mock payment flow.
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentMethod != null ? paymentMethod : COD);
        payment.setAmount(total);
        payment.setStatus(Payment.Status.PENDING);
        paymentRepository.save(payment);

        // 5. Only clear the cart once everything above succeeded.
        cartItemRepository.deleteAllByCartId(cart.getId());

        return order;
    }

    /**
     * Persists a PENDING_PAYMENT order with its items (price snapshot) and the first history row. It does NOT reserve stock
     * and does NOT touch the cart: the caller orchestrates both inside one transaction. Money is validated here
     * (never trusted): the lines must add up to {@code subtotal} and {@code total} must equal
     * {@code subtotal - discountAmount + shippingFee}.
     */
    @Transactional
    public Order createPendingOrder(Long userId, List<OrderItemDraft> items, BigDecimal subtotal,
                                    BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal total,
                                    String customerName, String customerPhone, String shippingAddress,
                                    String discountCode) {
        if (userId == null) {
            throw new BadRequestException("Thiếu thông tin người đặt hàng.");
        }
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Đơn hàng phải có ít nhất một sản phẩm.");
        }
        if (isBlank(customerName) || isBlank(customerPhone) || isBlank(shippingAddress)) {
            throw new BadRequestException("Vui lòng nhập đầy đủ tên người nhận, số điện thoại và địa chỉ giao hàng.");
        }
        if (subtotal == null || discountAmount == null || shippingFee == null || total == null
                || subtotal.signum() < 0 || discountAmount.signum() < 0 || shippingFee.signum() < 0) {
            throw new BadRequestException("Số tiền của đơn hàng không hợp lệ.");
        }

        BigDecimal linesTotal = BigDecimal.ZERO;
        for (OrderItemDraft draft : items) {
            if (draft == null || draft.productId() == null || draft.quantity() <= 0
                    || draft.unitPrice() == null || draft.unitPrice().signum() < 0) {
                throw new BadRequestException("Sản phẩm trong đơn hàng không hợp lệ.");
            }
            linesTotal = linesTotal.add(draft.unitPrice().multiply(BigDecimal.valueOf(draft.quantity())));
        }
        if (linesTotal.compareTo(subtotal) != 0) {
            throw new BusinessRuleViolationException("Tạm tính không khớp với các sản phẩm trong đơn hàng.");
        }
        BigDecimal expectedTotal = subtotal.subtract(discountAmount).add(shippingFee);
        if (expectedTotal.signum() < 0 || total.compareTo(expectedTotal) != 0) {
            throw new BusinessRuleViolationException("Tổng tiền không khớp với tạm tính, giảm giá và phí vận chuyển.");
        }

        Order order = new Order();
        order.setUser(userRepository.getReferenceById(userId));
        order.setCustomerName(customerName);
        order.setCustomerPhone(customerPhone);
        order.setShippingAddress(shippingAddress);
        order.setSubtotal(subtotal);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setDiscountCode(isBlank(discountCode) ? null : discountCode.trim());
        order.setTotalPrice(total);
        // The initial status is the entity default (PENDING_PAYMENT); this is not a status change.
        order = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemDraft draft : items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(productRepository.getReferenceById(draft.productId()));
            orderItem.setQuantity(draft.quantity());
            orderItem.setPrice(draft.unitPrice()); // historical price snapshot
            orderItems.add(orderItem);
        }
        orderItemRepository.saveAll(orderItems);

        writeHistory(order, null, order.getStatus(), userId, "Order placed");
        return order;
    }

    // ------------------------------------------------------------------------------------------------------------
    // The state machine
    // ------------------------------------------------------------------------------------------------------------

    /**
     * The ONLY place that changes {@code orders.status}.
     *
     * <ol>
     *   <li>locks the order row ({@code SELECT ... FOR UPDATE}) so concurrent transitions run one after the other;</li>
     *   <li>validates the transition table (same status and unlisted pairs are a 400 {@code BUSINESS_RULE_VIOLATION} naming
     *       the from/to statuses), plus the COD rule for PENDING_PAYMENT to PROCESSING;</li>
     *   <li>applies the inventory side effect ({@link OrderInventoryPolicy});</li>
     *   <li>writes exactly one history row and publishes {@link OrderStatusChangedEvent}
     *       (and {@link OrderCancelledEvent} when the order becomes CANCELLED).</li>
     * </ol>
     *
     * <p>Authorization is the caller's job: cancelling a PAID or PROCESSING order is staff only, so the controller
     * requires {@code order:cancel} and the owner path ({@link #cancelOwnOrder}) only ever cancels PENDING_PAYMENT orders.
     *
     * @param actorUserId the acting user, or null for a system transition (for example the mock payment)
     */
    @Transactional
    public Order transitionStatus(Long orderId, OrderStatus newStatus, Long actorUserId, String note) {
        if (newStatus == null) {
            throw new BadRequestException("Trạng thái mới là bắt buộc.");
        }
        Order order = lockOrder(orderId);

        OrderStatus oldStatus = order.getStatus();
        if (oldStatus == newStatus) {
            throw new BusinessRuleViolationException("Đơn hàng #" + orderId + " đã ở trạng thái " + oldStatus + ".");
        }
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new BusinessRuleViolationException(
                    "Không thể chuyển đơn hàng từ " + oldStatus + " sang " + newStatus + ".");
        }

        Payment payment = null;
        if (oldStatus == OrderStatus.PENDING_PAYMENT && newStatus == OrderStatus.PROCESSING) {
            payment = paymentRepository.findByOrderId(orderId).orElse(null);
            if (payment == null || !COD.equalsIgnoreCase(payment.getPaymentMethod())) {
                throw new BusinessRuleViolationException(
                        "Chỉ đơn hàng thanh toán khi nhận hàng (COD) mới có thể chuyển thẳng sang xử lý.");
            }
        }

        applyInventoryEffect(order, oldStatus, newStatus);

        order.setStatus(newStatus);
        orderRepository.save(order);

        String historyNote = note;
        boolean refundRequired = false;
        if (newStatus == OrderStatus.CANCELLED) {
            if (payment == null) {
                payment = paymentRepository.findByOrderId(orderId).orElse(null);
            }
            refundRequired = payment != null && payment.getStatus() == Payment.Status.SUCCESS;
            if (refundRequired) {
                historyNote = isBlank(note) ? REFUND_REQUIRED : REFUND_REQUIRED + ": " + note.trim();
            }
        }
        writeHistory(order, oldStatus, newStatus, actorUserId, historyNote);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, oldStatus, newStatus, actorUserId));
        if (newStatus == OrderStatus.CANCELLED) {
            eventPublisher.publishEvent(new OrderCancelledEvent(orderId, refundRequired));
        }
        return order;
    }

    /**
     * The customer cancels their own order. Allowed only for the order's owner and only while it is PENDING_PAYMENT. The
     * check runs on the locked row, so a payment that lands a moment earlier cannot be cancelled by the owner.
     */
    @Transactional
    public Order cancelOwnOrder(Long orderId, Long userId, String note) {
        Order order = lockOrder(orderId);
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn hàng này.");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleViolationException("Chỉ có thể tự hủy đơn hàng đang chờ thanh toán.");
        }
        return transitionStatus(orderId, OrderStatus.CANCELLED, userId, note);
    }

    /**
     * Locks the order row and re-reads it. The refresh matters: with open-in-view an Order loaded earlier in the same
     * HTTP request would otherwise be returned from the persistence context with a stale status even though the row is now
     * locked, and the transition would be validated against old data.
     */
    private Order lockOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
        entityManager.refresh(order);
        return order;
    }

    private void applyInventoryEffect(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        OrderInventoryPolicy.Effect effect = OrderInventoryPolicy.effectFor(oldStatus, newStatus);
        if (effect == OrderInventoryPolicy.Effect.NONE) {
            return;
        }
        List<OrderItem> items = new ArrayList<>(orderItemRepository.findByOrderIdWithProduct(order.getId()));
        items.sort(Comparator.comparing(item -> item.getProduct().getId()));
        for (OrderItem item : items) {
            Long productId = item.getProduct().getId();
            int quantity = item.getQuantity();
            switch (effect) {
                case COMMIT -> inventoryGateway.commit(productId, quantity, INVENTORY_REF_TYPE, order.getId());
                case RELEASE -> inventoryGateway.release(productId, quantity, INVENTORY_REF_TYPE, order.getId());
                case RESTOCK -> inventoryGateway.restock(productId, quantity, INVENTORY_REF_TYPE, order.getId());
                default -> { }
            }
        }
    }

    private void writeHistory(Order order, OrderStatus oldStatus, OrderStatus newStatus, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(actorUserId);
        history.setNote(truncate(note));
        historyRepository.save(history);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Queries for /api/v1
    // ------------------------------------------------------------------------------------------------------------

    /** The caller's own orders, newest first by default. Three queries for any page size: page, count, items with products. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listMyOrders(Long userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = orderRepository.findAll(filter(userId, status, null, null, null), pageable);
        Map<Long, List<OrderItem>> items = loadItems(page.getContent());
        OrderViewer owner = new OrderViewer(userId, false, false, false);
        return page.map(order -> toResponse(order, items.getOrDefault(order.getId(), List.of()), owner, false));
    }

    /** Admin list with filters. At most four queries for any page size: page, count, items with products, payments. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listAdminOrders(OrderStatus status, String keyword, LocalDate from, LocalDate to,
                                               Pageable pageable, OrderViewer viewer) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        }
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        Page<Order> page = orderRepository.findAll(filter(null, status, keyword, fromTime, toExclusive), pageable);

        Map<Long, List<OrderItem>> items = loadItems(page.getContent());
        Set<Long> codOrderIds = new HashSet<>();
        if (viewer.canUpdate() && !page.isEmpty()) {
            List<Long> ids = page.getContent().stream().map(Order::getId).toList();
            for (Payment payment : paymentRepository.findByOrderIdIn(ids)) {
                if (COD.equalsIgnoreCase(payment.getPaymentMethod())) {
                    codOrderIds.add(payment.getOrder().getId());
                }
            }
        }
        return page.map(order -> toResponse(order, items.getOrDefault(order.getId(), List.of()), viewer,
                codOrderIds.contains(order.getId())));
    }

    /** One order for its owner or for staff with {@code order:view}; anyone else gets 403. */
    @Transactional(readOnly = true)
    public OrderResponse getOrderView(Long orderId, OrderViewer viewer) {
        Order order = requireVisible(orderId, viewer);
        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(orderId);
        boolean cod = viewer.canUpdate() && paymentRepository.findByOrderId(orderId)
                .map(p -> COD.equalsIgnoreCase(p.getPaymentMethod())).orElse(false);
        return toResponse(order, items, viewer, cod);
    }

    /** The status timeline, oldest first. Internal fields (actor, note) are only filled for staff. */
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getHistory(Long orderId, OrderViewer viewer) {
        requireVisible(orderId, viewer);
        boolean staff = viewer.canViewOthers();
        return historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(orderId).stream()
                .map(h -> OrderStatusHistoryResponse.from(h, staff))
                .toList();
    }

    private Order requireVisible(Long orderId, OrderViewer viewer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
        boolean owner = Objects.equals(order.getUserId(), viewer.userId());
        if (!owner && !viewer.canViewOthers()) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn hàng này.");
        }
        return order;
    }

    private Map<Long, List<OrderItem>> loadItems(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = orders.stream().map(Order::getId).toList();
        return orderItemRepository.findByOrderIdInWithProduct(ids).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items, OrderViewer viewer, boolean cod) {
        boolean owner = Objects.equals(order.getUserId(), viewer.userId());
        boolean canCancel = owner && order.getStatus() == OrderStatus.PENDING_PAYMENT;
        return OrderResponse.from(order, items, allowedNextFor(order.getStatus(), cod, viewer), canCancel);
    }

    /**
     * The statuses a staff caller may move an order to: the transition table, minus the options that would be refused for
     * this caller (PROCESSING straight from PENDING_PAYMENT needs a COD order; CANCELLED needs {@code order:cancel}).
     * Empty for a caller without {@code order:update}. The frontend renders its options from this and never hard-codes
     * the table.
     */
    static List<String> allowedNextFor(OrderStatus current, boolean cod, OrderViewer viewer) {
        if (!viewer.canUpdate()) {
            return List.of();
        }
        List<String> allowed = new ArrayList<>();
        for (OrderStatus next : current.allowedNext()) {
            if (current == OrderStatus.PENDING_PAYMENT && next == OrderStatus.PROCESSING && !cod) {
                continue;
            }
            if (next == OrderStatus.CANCELLED && !viewer.canCancel()) {
                continue;
            }
            allowed.add(next.name());
        }
        return allowed;
    }

    /** Builds the optional filters; a predicate is only added for a filter that was given. LIKE wildcards are escaped. */
    private static Specification<Order> filter(Long userId, OrderStatus status, String keyword,
                                               LocalDateTime from, LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), toExclusive));
            }
            if (!isBlank(keyword)) {
                String text = keyword.trim();
                List<Predicate> anyOf = new ArrayList<>();
                anyOf.add(cb.like(cb.lower(root.<String>get("customerName")),
                        "%" + escapeLike(text.toLowerCase()) + "%", '\\'));
                String digits = text.startsWith("#") ? text.substring(1) : text;
                if (digits.matches("\\d{1,18}")) {
                    anyOf.add(cb.equal(root.<Long>get("id"), Long.parseLong(digits)));
                }
                predicates.add(cb.or(anyOf.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= NOTE_MAX ? trimmed : trimmed.substring(0, NOTE_MAX);
    }

    // ------------------------------------------------------------------------------------------------------------
    // TEMPORARY-COMPAT(B05): getItems serves the legacy POST /api/orders response (until B03 rewrites checkout);
    // getById/getOwnedOrAdmin serve PaymentController (until B06 rewrites it).
    // ------------------------------------------------------------------------------------------------------------

    public List<OrderItem> getItems(Long orderId) {
        return orderItemRepository.findByOrderIdWithProduct(orderId);
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + id));
    }

    /** A user may only view their own order; an admin may view any order. */
    public Order getOwnedOrAdmin(Long orderId, User user, boolean isAdmin) {
        Order order = getById(orderId);
        if (!isAdmin && !Objects.equals(order.getUserId(), user.getId())) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn hàng này.");
        }
        return order;
    }
}
