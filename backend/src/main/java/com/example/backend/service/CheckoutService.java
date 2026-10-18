package com.example.backend.service;

import com.example.backend.dto.CheckoutPreviewResponse;
import com.example.backend.dto.CheckoutRequest;
import com.example.backend.entity.Address;
import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Order;
import com.example.backend.entity.Product;
import com.example.backend.event.OrderPlacedEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.service.inventory.InsufficientStockException;
import com.example.backend.service.inventory.InventoryGateway;
import com.example.backend.service.inventory.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cart -> order (B04). {@link #checkout} is ONE transaction, in this order:
 * <ol>
 *   <li>lock the user's cart row (a second, parallel checkout waits here, then finds the cart empty and is refused);</li>
 *   <li>load the cart lines WITH their products in one query; an empty cart is refused;</li>
 *   <li>per line: the product must be ACTIVE, the quantity positive, the price is the one in the database, and the quantity
 *       must be available (one batched inventory query);</li>
 *   <li>subtotal = sum of price x quantity;</li>
 *   <li>the discount code, if any (see {@link DiscountService#apply});</li>
 *   <li>the shipping fee ({@link ShippingPolicy});</li>
 *   <li>total = subtotal - discount + shipping;</li>
 *   <li>the order, its items and its first history row, with the recipient copied in as a snapshot;</li>
 *   <li>reserve the stock, product by product in ASCENDING product id (B03 lock ordering); running out of stock rolls back
 *       everything, the order included;</li>
 *   <li>the pending payment;</li>
 *   <li>clear the cart LAST, then publish {@link OrderPlacedEvent}.</li>
 * </ol>
 * Nothing in the request is trusted for money: prices, totals, discount and shipping are computed here from the database.
 * {@link #preview} runs the same arithmetic without any side effect.
 */
@Service
public class CheckoutService {

    static final String EMPTY_CART = "Giỏ hàng của bạn đang trống.";
    private static final String INVENTORY_REF_TYPE = "ORDER";

    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private InventoryGateway inventoryGateway;
    @Autowired private InventoryService inventoryService;
    @Autowired private DiscountService discountService;
    @Autowired private AddressService addressService;
    @Autowired private ShippingPolicy shippingPolicy;
    @Autowired private ApplicationEventPublisher eventPublisher;

    /** Where the order ships and to whom: a snapshot, never a reference to the address book. */
    private record Recipient(String name, String phone, String address) {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Checkout
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Places the order for {@code userId} (always the user of the verified token).
     *
     * @throws BusinessRuleViolationException empty cart, inactive product, invalid discount code (400 BUSINESS_RULE_VIOLATION);
     *                                        {@link InsufficientStockException} is one too and names the product
     * @throws BadRequestException            unsupported payment method or missing recipient fields (400 VALIDATION_ERROR)
     */
    @Transactional
    public Order checkout(Long userId, CheckoutRequest request) {
        String method = PaymentService.requireSupportedMethod(request.paymentMethod());

        // (1) Everything below happens under the cart row lock.
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(EMPTY_CART));

        // (2) Lines with their products in one query.
        List<CartItem> cartItems = cartItemRepository.findByCartIdWithProduct(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessRuleViolationException(EMPTY_CART);
        }

        Recipient recipient = resolveRecipient(userId, request);

        // (3) + (4) Validate every line against the database and add up the subtotal.
        Map<Long, Integer> available = inventoryService.availableFor(
                cartItems.stream().map(item -> item.getProduct().getId()).collect(Collectors.toList()));
        List<OrderItemDraft> drafts = new ArrayList<>();
        BigDecimal subtotal = money(BigDecimal.ZERO);
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            int quantity = item.getQuantity();
            if (product.getStatus() != Product.Status.ACTIVE) {
                throw new BusinessRuleViolationException("Sản phẩm \"" + product.getName() + "\" hiện không còn kinh doanh.");
            }
            if (quantity <= 0) {
                throw new BusinessRuleViolationException("Số lượng của sản phẩm \"" + product.getName() + "\" không hợp lệ.");
            }
            if (available.getOrDefault(product.getId(), 0) < quantity) {
                throw new InsufficientStockException(product.getId(), quantity, product.getName());
            }
            BigDecimal unitPrice = money(product.getPrice()); // read from the database, never from the client
            drafts.add(new OrderItemDraft(product.getId(), quantity, unitPrice));
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        // Reserve (and lock inventory rows) in ascending product id, whatever order the cart lines are in.
        drafts.sort(Comparator.comparing(OrderItemDraft::productId));

        // (5) + (6) + (7) Discount, shipping, total.
        AppliedDiscount discount = discountService.apply(request.discountCode(), subtotal, LocalDateTime.now());
        BigDecimal shippingFee = shippingPolicy.feeFor(subtotal);
        BigDecimal total = money(subtotal.subtract(discount.amount()).add(shippingFee));

        // (8) The order, its items and its first history row (no stock movement yet).
        Order order = orderService.createPendingOrder(userId, drafts, subtotal, discount.amount(), shippingFee, total,
                recipient.name(), recipient.phone(), recipient.address(), discount.code());

        // (9) Take the stock. An InsufficientStockException here aborts and rolls back everything above.
        for (OrderItemDraft draft : drafts) {
            inventoryGateway.reserve(draft.productId(), draft.quantity(), INVENTORY_REF_TYPE, order.getId());
        }

        // (10) The pending payment.
        paymentService.createPendingPayment(order, method);

        // (11) Only now clear the cart; then announce the order (consumers run after the commit).
        cartItemRepository.deleteAllByCartId(cart.getId());
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getId(), userId, order.getTotalPrice()));
        return order;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Preview
    // ------------------------------------------------------------------------------------------------------------

    /**
     * What checkout would charge for the user's cart right now, WITHOUT any side effect: no cart lock, no reservation, no write.
     * Problems that would make checkout fail (empty cart, unavailable product, not enough stock, rejected discount code) are
     * reported in {@code issues} instead of an error, so the page can show them next to the lines.
     */
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(Long userId, String discountCode) {
        Optional<Cart> cart = cartRepository.findByUserId(userId);
        List<CartItem> cartItems = cart.isEmpty() ? List.of() : cartItemRepository.findByCartIdWithProduct(cart.get().getId());

        List<CheckoutPreviewResponse.Issue> issues = new ArrayList<>();
        if (cartItems.isEmpty()) {
            issues.add(new CheckoutPreviewResponse.Issue("EMPTY_CART", null, EMPTY_CART));
            return new CheckoutPreviewResponse(List.of(), money(BigDecimal.ZERO), money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO), money(BigDecimal.ZERO), issues);
        }

        Map<Long, Integer> available = inventoryService.availableFor(
                cartItems.stream().map(item -> item.getProduct().getId()).collect(Collectors.toList()));
        List<CheckoutPreviewResponse.Line> lines = new ArrayList<>();
        BigDecimal subtotal = money(BigDecimal.ZERO);
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            int quantity = item.getQuantity();
            int stock = available.getOrDefault(product.getId(), 0);
            BigDecimal unitPrice = money(product.getPrice());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            lines.add(new CheckoutPreviewResponse.Line(product.getId(), product.getName(), unitPrice, quantity, lineTotal, stock));
            subtotal = subtotal.add(lineTotal);

            if (product.getStatus() != Product.Status.ACTIVE) {
                issues.add(new CheckoutPreviewResponse.Issue("PRODUCT_UNAVAILABLE", product.getId(),
                        "Sản phẩm \"" + product.getName() + "\" hiện không còn kinh doanh."));
            } else if (stock < quantity) {
                issues.add(new CheckoutPreviewResponse.Issue("INSUFFICIENT_STOCK", product.getId(), stock <= 0
                        ? "Sản phẩm \"" + product.getName() + "\" đã hết hàng."
                        : "Sản phẩm \"" + product.getName() + "\" chỉ còn " + stock + " sản phẩm."));
            }
        }

        AppliedDiscount discount = AppliedDiscount.none();
        try {
            discount = discountService.apply(discountCode, subtotal, LocalDateTime.now());
        } catch (BusinessRuleViolationException e) {
            issues.add(new CheckoutPreviewResponse.Issue("INVALID_DISCOUNT", null, e.getMessage()));
        }

        BigDecimal shippingFee = shippingPolicy.feeFor(subtotal);
        BigDecimal total = money(subtotal.subtract(discount.amount()).add(shippingFee));
        return new CheckoutPreviewResponse(lines, subtotal, discount.amount(), shippingFee, total, issues);
    }

    // ------------------------------------------------------------------------------------------------------------

    /**
     * The recipient snapshot: a saved address of the caller (someone else's is a 403 from AddressService, a missing one a 404),
     * or the three text fields. When {@code addressId} is given the text fields are ignored.
     */
    private Recipient resolveRecipient(Long userId, CheckoutRequest request) {
        if (request.addressId() != null) {
            Address address = addressService.getOwned(userId, request.addressId());
            return new Recipient(address.getRecipientName(), address.getPhone(), joinAddress(address));
        }
        String name = trimToNull(request.customerName());
        String phone = trimToNull(request.customerPhone());
        String text = trimToNull(request.shippingAddress());
        if (name == null || phone == null || text == null) {
            throw new BadRequestException("Vui lòng chọn địa chỉ giao hàng hoặc nhập đầy đủ tên người nhận, số điện thoại và địa chỉ.");
        }
        return new Recipient(name, phone, text);
    }

    private static String joinAddress(Address address) {
        List<String> parts = new ArrayList<>();
        for (String part : new String[] {address.getLine1(), address.getWard(), address.getDistrict(), address.getProvince()}) {
            String trimmed = trimToNull(part);
            if (trimmed != null) {
                parts.add(trimmed);
            }
        }
        return String.join(", ", parts);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** VND amounts are BigDecimal with scale 2 (DECIMAL(12,2)). */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
