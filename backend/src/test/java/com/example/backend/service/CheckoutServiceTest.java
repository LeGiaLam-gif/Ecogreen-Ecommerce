package com.example.backend.service;

import com.example.backend.dto.CheckoutPreviewResponse;
import com.example.backend.dto.CheckoutRequest;
import com.example.backend.entity.Address;
import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Discount;
import com.example.backend.entity.Order;
import com.example.backend.entity.Product;
import com.example.backend.event.OrderPlacedEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.repository.DiscountRepository;
import com.example.backend.service.inventory.InsufficientStockException;
import com.example.backend.service.inventory.InventoryGateway;
import com.example.backend.service.inventory.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B04 checkout and preview with mocked collaborators (no Spring context, no database): the order of the steps, every rule,
 * and exact amounts. What only a real database can prove (the cart row lock, the rollback, the lock-ordering of the
 * inventory rows) is in {@code CheckoutPostgresTest}, which is skipped unless ECOGREEN_TEST_PG_URL is set.
 */
class CheckoutServiceTest {

    private static final long USER = 5L;
    private static final long CART = 3L;

    private CartRepository carts;
    private CartItemRepository cartItems;
    private OrderService orderService;
    private PaymentService paymentService;
    private InventoryGateway inventoryGateway;
    private InventoryService inventoryService;
    private DiscountRepository discounts;
    private AddressService addressService;
    private ApplicationEventPublisher events;
    private CheckoutService service;
    private Cart cart;

    /** What CheckoutService handed to OrderService.createPendingOrder. */
    private record Created(Long userId, List<OrderItemDraft> items, BigDecimal subtotal, BigDecimal discountAmount,
                           BigDecimal shippingFee, BigDecimal total, String name, String phone, String address,
                           String discountCode) {
    }

    private Created created;

    @BeforeEach
    void setUp() {
        carts = mock(CartRepository.class);
        cartItems = mock(CartItemRepository.class);
        orderService = mock(OrderService.class);
        paymentService = mock(PaymentService.class);
        inventoryGateway = mock(InventoryGateway.class);
        inventoryService = mock(InventoryService.class);
        discounts = mock(DiscountRepository.class);
        addressService = mock(AddressService.class);
        events = mock(ApplicationEventPublisher.class);

        DiscountService discountService = new DiscountService();
        ReflectionTestUtils.setField(discountService, "discountRepository", discounts);

        service = new CheckoutService();
        ReflectionTestUtils.setField(service, "cartRepository", carts);
        ReflectionTestUtils.setField(service, "cartItemRepository", cartItems);
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "paymentService", paymentService);
        ReflectionTestUtils.setField(service, "inventoryGateway", inventoryGateway);
        ReflectionTestUtils.setField(service, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(service, "discountService", discountService);
        ReflectionTestUtils.setField(service, "addressService", addressService);
        ReflectionTestUtils.setField(service, "shippingPolicy",
                new ShippingPolicy(new BigDecimal("30000"), new BigDecimal("500000")));
        ReflectionTestUtils.setField(service, "eventPublisher", events);

        cart = new Cart();
        cart.setId(CART);
        when(carts.findByUserIdForUpdate(USER)).thenReturn(Optional.of(cart));
        when(carts.findByUserId(USER)).thenReturn(Optional.of(cart));

        // creating the order assigns it id 100 and keeps the total the service computed
        when(orderService.createPendingOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    created = new Created(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                            invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5),
                            invocation.getArgument(6), invocation.getArgument(7), invocation.getArgument(8),
                            invocation.getArgument(9));
                    Order order = new Order();
                    order.setId(100L);
                    order.setTotalPrice(created.total());
                    return order;
                });
    }

    // ---- builders ----

    private static Product product(long id, String name, String price, Product.Status status) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStatus(status);
        return product;
    }

    private static Product product(long id, String name, String price) {
        return product(id, name, price, Product.Status.ACTIVE);
    }

    private CartItem line(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    /** The cart holds the given lines (in the given order) and every product has plenty of stock. */
    private void cartHolds(CartItem... lines) {
        when(cartItems.findByCartIdWithProduct(CART)).thenReturn(List.of(lines));
        Map<Long, Integer> plenty = Arrays.stream(lines)
                .collect(Collectors.toMap(l -> l.getProduct().getId(), l -> 100, (a, b) -> a));
        when(inventoryService.availableFor(any())).thenReturn(plenty);
    }

    /** Cart: product 11 "Hộp cơm" 50 000 x1 first, product 10 "Bình nước" 100 000 x2 second. Subtotal 250 000. */
    private void defaultCart() {
        cartHolds(line(product(11L, "Hộp cơm", "50000"), 1), line(product(10L, "Bình nước", "100000"), 2));
    }

    private static CheckoutRequest text(String discountCode, String method) {
        return new CheckoutRequest(null, "  Nguyễn Văn A ", " 0901234567 ", " 1 Lê Lợi, Quận 1 ", discountCode, method);
    }

    private void storedDiscount(String code, Integer percentOff, String amountOff, String minOrder) {
        Discount discount = new Discount();
        discount.setId(1L);
        discount.setCode(code);
        discount.setPercentOff(percentOff);
        discount.setAmountOff(amountOff == null ? null : new BigDecimal(amountOff));
        discount.setMinOrderAmount(minOrder == null ? null : new BigDecimal(minOrder));
        discount.setActive(true);
        when(discounts.findByUpperCode(code)).thenReturn(Optional.of(discount));
    }

    private static boolean transactional(String method, Class<?>... types) {
        try {
            return CheckoutService.class.getMethod(method, types).isAnnotationPresent(Transactional.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    // ---- the happy path, with exact amounts ----

    @Test
    void checkout_computesEveryAmountOnTheServer_andRunsTheStepsInOrder() {
        defaultCart();

        Order order = service.checkout(USER, text(null, "momo"));

        assertEquals(100L, order.getId());
        // subtotal 50 000 x1 + 100 000 x2 = 250 000; below the 500 000 threshold so + 30 000 shipping; no discount
        assertEquals(USER, created.userId());
        assertEquals(new BigDecimal("250000.00"), created.subtotal());
        assertEquals(new BigDecimal("0.00"), created.discountAmount());
        assertEquals(new BigDecimal("30000.00"), created.shippingFee());
        assertEquals(new BigDecimal("280000.00"), created.total());
        assertNull(created.discountCode());
        // the recipient is trimmed text, copied into the order
        assertEquals("Nguyễn Văn A", created.name());
        assertEquals("0901234567", created.phone());
        assertEquals("1 Lê Lợi, Quận 1", created.address());
        // the items carry the database prices, lowest product id first
        assertEquals(List.of(10L, 11L), created.items().stream().map(OrderItemDraft::productId).toList());
        assertEquals(new BigDecimal("100000.00"), created.items().get(0).unitPrice());
        assertEquals(2, created.items().get(0).quantity());
        assertEquals(new BigDecimal("50000.00"), created.items().get(1).unitPrice());

        // the order of the steps: lock -> lines -> order -> reserve -> payment -> clear the cart -> event
        InOrder steps = inOrder(carts, cartItems, orderService, inventoryGateway, paymentService, events);
        steps.verify(carts).findByUserIdForUpdate(USER);
        steps.verify(cartItems).findByCartIdWithProduct(CART);
        steps.verify(orderService).createPendingOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        steps.verify(inventoryGateway).reserve(10L, 2, "ORDER", 100L);
        steps.verify(inventoryGateway).reserve(11L, 1, "ORDER", 100L);
        steps.verify(paymentService).createPendingPayment(order, "MOMO");
        steps.verify(cartItems).deleteAllByCartId(CART);
        steps.verify(events).publishEvent(any(OrderPlacedEvent.class));
        verify(carts, never()).findByUserId(any()); // the unlocked lookup is never used by checkout
    }

    @Test
    void checkout_withADiscountCode_appliesItToTheItemsTotal() {
        defaultCart();
        storedDiscount("SALE10", 10, null, null);

        service.checkout(USER, text(" sale10 ", "COD"));

        // 10% of 250 000 = 25 000; shipping is decided on the items total BEFORE the discount (250 000 < 500 000) = 30 000
        assertEquals(new BigDecimal("250000.00"), created.subtotal());
        assertEquals(new BigDecimal("25000.00"), created.discountAmount());
        assertEquals(new BigDecimal("30000.00"), created.shippingFee());
        assertEquals(new BigDecimal("255000.00"), created.total());
        assertEquals("SALE10", created.discountCode());
    }

    @Test
    void checkout_shippingIsFreeExactlyAtTheThreshold_andPaidOneDongBelow() {
        cartHolds(line(product(10L, "Bình nước", "250000"), 2)); // 500 000 exactly
        service.checkout(USER, text(null, "COD"));
        assertEquals(new BigDecimal("500000.00"), created.subtotal());
        assertEquals(new BigDecimal("0.00"), created.shippingFee());
        assertEquals(new BigDecimal("500000.00"), created.total());

        cartHolds(line(product(10L, "Bình nước", "249999.50"), 2)); // 499 999.00
        service.checkout(USER, text(null, "COD"));
        assertEquals(new BigDecimal("499999.00"), created.subtotal());
        assertEquals(new BigDecimal("30000.00"), created.shippingFee());
        assertEquals(new BigDecimal("529999.00"), created.total());
    }

    @Test
    void checkout_discountLargerThanTheItems_leavesOnlyShipping() {
        cartHolds(line(product(10L, "Bình nước", "20000"), 1));
        storedDiscount("HUGE", null, "100000", null);

        service.checkout(USER, text("huge", "COD"));

        assertEquals(new BigDecimal("20000.00"), created.discountAmount()); // capped at the subtotal
        assertEquals(new BigDecimal("30000.00"), created.shippingFee());
        assertEquals(new BigDecimal("30000.00"), created.total()); // never below zero
    }

    // ---- refusals ----

    @Test
    void checkout_secondItemInsufficient_rollsBackOrderReservationAndKeepsCart() {
        defaultCart();
        // product 10 can be reserved, product 11 ran out between the early check and the reservation
        doThrow(new InsufficientStockException(11L, 1, "Hộp cơm")).when(inventoryGateway).reserve(11L, 1, "ORDER", 100L);

        InsufficientStockException e = assertThrows(InsufficientStockException.class, () -> service.checkout(USER, text(null, "COD")));

        assertTrue(e.getMessage().contains("Hộp cơm"));
        // nothing after the failed reservation ran: no payment, the cart is not cleared, no event
        verifyNoInteractions(paymentService, events);
        verify(cartItems, never()).deleteAllByCartId(anyLong());
        // the order, its items, the history row and the stock already taken for product 10 are undone by the transaction:
        // the whole method is ONE @Transactional unit (the proof against a real database is CheckoutPostgresTest)
        assertTrue(transactional("checkout", Long.class, CheckoutRequest.class));
    }

    @Test
    void checkout_notEnoughAvailable_isRefusedBeforeAnyWrite_namingTheProduct() {
        defaultCart();
        when(inventoryService.availableFor(any())).thenReturn(Map.of(10L, 2, 11L, 0));

        InsufficientStockException e = assertThrows(InsufficientStockException.class, () -> service.checkout(USER, text(null, "COD")));

        assertEquals("Sản phẩm \"Hộp cơm\" không đủ số lượng tồn kho.", e.getMessage());
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
        verify(cartItems, never()).deleteAllByCartId(anyLong());
    }

    @Test
    void checkout_emptyCart_isRejected() {
        cartHolds(); // the cart row exists but holds nothing

        BusinessRuleViolationException e = assertThrows(BusinessRuleViolationException.class, () -> service.checkout(USER, text(null, "COD")));

        assertEquals(CheckoutService.EMPTY_CART, e.getMessage());
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
    }

    @Test
    void checkout_userWithoutACartRow_isRejectedAsAnEmptyCart() {
        when(carts.findByUserIdForUpdate(USER)).thenReturn(Optional.empty());

        BusinessRuleViolationException e = assertThrows(BusinessRuleViolationException.class, () -> service.checkout(USER, text(null, "COD")));

        assertEquals(CheckoutService.EMPTY_CART, e.getMessage());
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
    }

    @Test
    void checkout_inactiveProduct_isRejected() {
        for (Product.Status status : Product.Status.values()) {
            if (status == Product.Status.ACTIVE) {
                continue;
            }
            cartHolds(line(product(10L, "Bình nước", "100000", status), 1));

            BusinessRuleViolationException e = assertThrows(BusinessRuleViolationException.class,
                    () -> service.checkout(USER, text(null, "COD")), status.name());

            assertTrue(e.getMessage().contains("Bình nước"), status.name());
        }
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
    }

    @Test
    void checkout_invalidDiscountCode_isRejectedWithTheGenericMessage_andNothingIsWritten() {
        defaultCart();
        when(discounts.findByUpperCode("NOSUCH")).thenReturn(Optional.empty());

        BusinessRuleViolationException e = assertThrows(BusinessRuleViolationException.class, () -> service.checkout(USER, text("nosuch", "COD")));

        assertEquals("Mã giảm giá không hợp lệ hoặc đã hết hạn.", e.getMessage());
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
    }

    @Test
    void checkout_unsupportedPaymentMethod_isRefusedBeforeTheCartIsEvenLocked() {
        defaultCart();

        for (String method : new String[] {null, "", "  ", "PAYPAL", "VNPAY"}) {
            assertThrows(BadRequestException.class, () -> service.checkout(USER, text(null, method)), String.valueOf(method));
        }
        verifyNoInteractions(carts, orderService, inventoryGateway, paymentService, events);
    }

    @Test
    void checkout_acceptsTheSupportedMethods_inAnyCase() {
        defaultCart();

        for (String method : new String[] {"cod", "COD", " VietQR ", "momo"}) {
            service.checkout(USER, text(null, method));
        }

        ArgumentCaptor<String> methods = ArgumentCaptor.forClass(String.class);
        verify(paymentService, org.mockito.Mockito.times(4)).createPendingPayment(any(Order.class), methods.capture());
        assertEquals(List.of("COD", "COD", "VIETQR", "MOMO"), methods.getAllValues());
    }

    @Test
    void checkout_withoutAnAddressOrRecipientFields_isRejected() {
        defaultCart();

        assertThrows(BadRequestException.class,
                () -> service.checkout(USER, new CheckoutRequest(null, " ", "0901234567", "1 Lê Lợi", null, "COD")));
        assertThrows(BadRequestException.class,
                () -> service.checkout(USER, new CheckoutRequest(null, "An", null, "1 Lê Lợi", null, "COD")));
        assertThrows(BadRequestException.class,
                () -> service.checkout(USER, new CheckoutRequest(null, "An", "0901234567", "", null, "COD")));

        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
    }

    // ---- the client is never trusted ----

    @Test
    void checkout_ignoresClientSuppliedPriceAndTotal() {
        // the database says 100 000; whatever a client may claim, only the database price reaches the order
        cartHolds(line(product(10L, "Bình nước", "100000"), 1));

        service.checkout(USER, text(null, "COD"));

        assertEquals(new BigDecimal("100000.00"), created.items().get(0).unitPrice());
        assertEquals(new BigDecimal("100000.00"), created.subtotal());
        assertEquals(new BigDecimal("130000.00"), created.total());
        // and the request type cannot even carry a price, total, discount value, shipping fee, status or user id
        Set<String> inputs = Arrays.stream(CheckoutRequest.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertEquals(Set.of("addressId", "customerName", "customerPhone", "shippingAddress", "discountCode", "paymentMethod"),
                inputs);
    }

    // ---- the address ----

    @Test
    void checkout_addressOfAnotherUser_isForbidden() {
        defaultCart();
        when(addressService.getOwned(USER, 77L)).thenThrow(new ForbiddenException("Bạn không có quyền truy cập địa chỉ này."));

        assertThrows(ForbiddenException.class,
                () -> service.checkout(USER, new CheckoutRequest(77L, null, null, null, null, "COD")));

        // the ownership check used the user of the token
        verify(addressService).getOwned(USER, 77L);
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
        verify(cartItems, never()).deleteAllByCartId(anyLong());
    }

    @Test
    void checkout_withASavedAddress_snapshotsItsFields_andIgnoresTheTextFields() {
        defaultCart();
        Address address = new Address();
        address.setId(8L);
        address.setUserId(USER);
        address.setRecipientName("Trần Thị B");
        address.setPhone("0912345678");
        address.setLine1("12 Nguyễn Huệ");
        address.setWard("Bến Nghé");
        address.setDistrict("Quận 1");
        address.setProvince("TP. Hồ Chí Minh");
        when(addressService.getOwned(USER, 8L)).thenReturn(address);

        service.checkout(USER, new CheckoutRequest(8L, "Ignored", "000", "Ignored street", null, "COD"));

        assertEquals("Trần Thị B", created.name());
        assertEquals("0912345678", created.phone());
        assertEquals("12 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh", created.address());
    }

    // ---- the order of the inventory locks ----

    @Test
    void checkout_reservesItemsInAscendingProductOrder() {
        // the cart lists the lines as 12, 10, 11
        cartHolds(line(product(12L, "C", "10000"), 3), line(product(10L, "A", "10000"), 1), line(product(11L, "B", "10000"), 2));

        service.checkout(USER, text(null, "COD"));

        InOrder reserved = inOrder(inventoryGateway);
        reserved.verify(inventoryGateway).reserve(10L, 1, "ORDER", 100L);
        reserved.verify(inventoryGateway).reserve(11L, 2, "ORDER", 100L);
        reserved.verify(inventoryGateway).reserve(12L, 3, "ORDER", 100L);
        assertEquals(List.of(10L, 11L, 12L), created.items().stream().map(OrderItemDraft::productId).toList());
    }

    // ---- the event ----

    @Test
    void checkout_publishesOrderPlacedEventAfterCommit() {
        defaultCart();

        service.checkout(USER, text(null, "COD"));

        ArgumentCaptor<OrderPlacedEvent> event = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(new OrderPlacedEvent(100L, USER, new BigDecimal("280000.00")), event.getValue());
        // It is the LAST step of the checkout transaction (after the cart was cleared) and the method is @Transactional, so a
        // consumer listening with @TransactionalEventListener(AFTER_COMMIT) is only called once the order is committed.
        InOrder last = inOrder(cartItems, events);
        last.verify(cartItems).deleteAllByCartId(CART);
        last.verify(events).publishEvent(any(OrderPlacedEvent.class));
        assertTrue(transactional("checkout", Long.class, CheckoutRequest.class));
    }

    @Test
    void checkout_whenItFails_publishesNoEvent() {
        defaultCart();
        doThrow(new InsufficientStockException(10L, 2, "Bình nước")).when(inventoryGateway).reserve(10L, 2, "ORDER", 100L);

        assertThrows(InsufficientStockException.class, () -> service.checkout(USER, text(null, "COD")));

        verifyNoInteractions(events);
    }

    // ---- preview ----

    @Test
    void preview_hasNoSideEffects() {
        defaultCart();
        storedDiscount("SALE10", 10, null, null);

        CheckoutPreviewResponse preview = service.preview(USER, "sale10");

        // the server's numbers
        assertEquals(new BigDecimal("250000.00"), preview.subtotal());
        assertEquals(new BigDecimal("25000.00"), preview.discountAmount());
        assertEquals(new BigDecimal("30000.00"), preview.shippingFee());
        assertEquals(new BigDecimal("255000.00"), preview.total());
        assertTrue(preview.issues().isEmpty());
        assertEquals(2, preview.items().size());
        CheckoutPreviewResponse.Line first = preview.items().get(0);
        assertEquals(11L, first.productId());
        assertEquals("Hộp cơm", first.name());
        assertEquals(new BigDecimal("50000.00"), first.unitPrice());
        assertEquals(1, first.quantity());
        assertEquals(new BigDecimal("50000.00"), first.lineTotal());
        assertEquals(100, first.available());

        // and nothing was changed: no cart lock, no order, no reservation, no payment, no cart clearing, no event
        verify(carts, never()).findByUserIdForUpdate(any());
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events, addressService);
        verify(cartItems, never()).deleteAllByCartId(anyLong());
        verify(cartItems, never()).save(any());
        verify(discounts, never()).saveAndFlush(any());
        assertTrue(readOnlyTransaction("preview", Long.class, String.class), "preview must run in a read-only transaction");
    }

    private static boolean readOnlyTransaction(String method, Class<?>... types) {
        try {
            Transactional tx = CheckoutService.class.getMethod(method, types).getAnnotation(Transactional.class);
            return tx != null && tx.readOnly();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void preview_reportsProblemsAsIssues_insteadOfFailing() {
        Product soldOut = product(10L, "Bình nước", "100000");
        Product retired = product(11L, "Hộp cơm", "50000", Product.Status.INACTIVE);
        Product low = product(12L, "Túi vải", "20000");
        cartHolds(line(soldOut, 1), line(retired, 1), line(low, 5));
        when(inventoryService.availableFor(any())).thenReturn(Map.of(10L, 0, 11L, 9, 12L, 2));
        when(discounts.findByUpperCode("NOSUCH")).thenReturn(Optional.empty());

        CheckoutPreviewResponse preview = service.preview(USER, "nosuch");

        List<String> codes = preview.issues().stream().map(CheckoutPreviewResponse.Issue::code).toList();
        assertEquals(List.of("INSUFFICIENT_STOCK", "PRODUCT_UNAVAILABLE", "INSUFFICIENT_STOCK", "INVALID_DISCOUNT"), codes);
        assertEquals(10L, preview.issues().get(0).productId());
        assertEquals("Sản phẩm \"Bình nước\" đã hết hàng.", preview.issues().get(0).message());
        assertEquals(11L, preview.issues().get(1).productId());
        assertEquals("Sản phẩm \"Túi vải\" chỉ còn 2 sản phẩm.", preview.issues().get(2).message());
        assertNull(preview.issues().get(3).productId());
        assertEquals("Mã giảm giá không hợp lệ hoặc đã hết hạn.", preview.issues().get(3).message());
        // the numbers are still there: 100 000 + 50 000 + 5 x 20 000 = 250 000, no discount, 30 000 shipping
        assertEquals(new BigDecimal("250000.00"), preview.subtotal());
        assertEquals(new BigDecimal("0.00"), preview.discountAmount());
        assertEquals(new BigDecimal("280000.00"), preview.total());
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
    }

    @Test
    void preview_ofAnEmptyCartOrAUserWithoutOne_isZerosWithAnEmptyCartIssue() {
        cartHolds();
        CheckoutPreviewResponse empty = service.preview(USER, null);
        assertTrue(empty.items().isEmpty());
        assertEquals(new BigDecimal("0.00"), empty.subtotal());
        assertEquals(new BigDecimal("0.00"), empty.shippingFee());
        assertEquals(new BigDecimal("0.00"), empty.total());
        assertEquals("EMPTY_CART", empty.issues().get(0).code());

        when(carts.findByUserId(USER)).thenReturn(Optional.empty());
        CheckoutPreviewResponse none = service.preview(USER, null);
        assertEquals("EMPTY_CART", none.issues().get(0).code());
        assertFalse(none.issues().isEmpty());
        verifyNoInteractions(orderService, inventoryGateway, paymentService, events);
    }

    @Test
    void preview_shippingBoundary_matchesCheckout() {
        cartHolds(line(product(10L, "Bình nước", "500000"), 1));

        CheckoutPreviewResponse preview = service.preview(USER, null);

        assertEquals(new BigDecimal("0.00"), preview.shippingFee());
        assertEquals(new BigDecimal("500000.00"), preview.total());
    }
}
