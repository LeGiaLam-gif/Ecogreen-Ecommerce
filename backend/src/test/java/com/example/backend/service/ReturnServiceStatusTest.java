package com.example.backend.service;

import com.example.backend.dto.ReturnDto;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Refund;
import com.example.backend.entity.Return;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.entity.ReturnStatusHistory;
import com.example.backend.event.ReturnStatusChangedEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B07: ReturnService.updateStatus - the transition table, the side effects on the order, the stock and the refund, and the
 * rule that a failing order transition leaves nothing behind. Mocked repositories only (no database). Order 7 belongs to
 * user 5; the acting staff user is 9.
 */
class ReturnServiceStatusTest {

    private static final long STAFF = 9L;

    /** The specified table, written out independently of the enum. */
    private static final Map<ReturnStatus, Set<ReturnStatus>> EXPECTED = new EnumMap<>(ReturnStatus.class);

    static {
        EXPECTED.put(ReturnStatus.PENDING, EnumSet.of(ReturnStatus.INFO_REQUESTED, ReturnStatus.APPROVED, ReturnStatus.REJECTED));
        EXPECTED.put(ReturnStatus.INFO_REQUESTED, EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.REJECTED));
        EXPECTED.put(ReturnStatus.APPROVED, EnumSet.of(ReturnStatus.RETURNED));
        EXPECTED.put(ReturnStatus.RETURNED, EnumSet.of(ReturnStatus.REFUNDED));
        EXPECTED.put(ReturnStatus.REJECTED, EnumSet.noneOf(ReturnStatus.class));
        EXPECTED.put(ReturnStatus.REFUNDED, EnumSet.noneOf(ReturnStatus.class));
    }

    private ReturnServiceFixture f;
    private Order order;
    private OrderItem line1; // id 101, product 10, ordered 2 x 100000
    private OrderItem line2; // id 102, product 20, ordered 3 x 50000
    private Return request;

    @BeforeEach
    void setUp() {
        f = new ReturnServiceFixture();
        order = f.order(7L, 5L, OrderStatus.RETURN_REQUESTED);
        line1 = f.orderItem(order, 101L, 10L, 2, "100000");
        line2 = f.orderItem(order, 102L, 20L, 3, "50000");
        f.withOrderItems(order, line1, line2);
    }

    private Return returnIn(ReturnStatus status) {
        request = f.returnRow(1L, order, status);
        return request;
    }

    private void returned(int qty1, int qty2) {
        List<com.example.backend.entity.ReturnItem> items = new ArrayList<>();
        if (qty1 > 0) {
            items.add(ReturnServiceFixture.returnItem(request, line1, qty1));
        }
        if (qty2 > 0) {
            items.add(ReturnServiceFixture.returnItem(request, line2, qty2));
        }
        f.withReturnItems(items.toArray(new com.example.backend.entity.ReturnItem[0]));
    }

    private void verifyNoSideEffects(ReturnServiceFixture fixture) {
        verifyNoInteractions(fixture.orderService);
        verifyNoInteractions(fixture.inventory);
        verify(fixture.refunds, never()).save(any(Refund.class));
        verify(fixture.returns, never()).save(any(Return.class));
        verify(fixture.events, never()).publishEvent(any(Object.class));
    }

    // ---- the table ----

    @TestFactory
    List<DynamicTest> invalidTransition_refused_forEveryPairOutsideTheTable() {
        List<DynamicTest> tests = new ArrayList<>();
        for (ReturnStatus from : ReturnStatus.values()) {
            for (ReturnStatus to : ReturnStatus.values()) {
                if (EXPECTED.get(from).contains(to)) {
                    continue;
                }
                tests.add(DynamicTest.dynamicTest(from + " -> " + to + " is refused", () -> {
                    ReturnServiceFixture fixture = new ReturnServiceFixture();
                    Order o = fixture.order(7L, 5L, OrderStatus.RETURN_REQUESTED);
                    Return r = fixture.returnRow(1L, o, from);
                    fixture.withReturnItems(ReturnServiceFixture.returnItem(r,
                            fixture.orderItem(o, 101L, 10L, 2, "100000"), 1));

                    assertThrows(BusinessRuleViolationException.class,
                            () -> fixture.service.updateStatus(1L, to, "ghi chú", STAFF));
                    assertEquals(from, r.getStatus(), "the status must not change");
                    verifyNoSideEffects(fixture);
                }));
            }
        }
        assertEquals(36 - 7, tests.size(), "6x6 pairs minus the 7 allowed ones");
        return tests;
    }

    @Test
    void theEnumTableIsExactlyTheSpecifiedTable() {
        for (ReturnStatus from : ReturnStatus.values()) {
            assertEquals(EXPECTED.get(from), from.allowedNext(), from.name());
        }
    }

    @Test
    void unknownReturn_isNotFound() {
        when(f.returns.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> f.service.updateStatus(99L, ReturnStatus.APPROVED, null, STAFF));
    }

    @Test
    void nullStatus_isRefused() {
        returnIn(ReturnStatus.PENDING);

        assertThrows(BadRequestException.class, () -> f.service.updateStatus(1L, null, null, STAFF));
    }

    // ---- notes ----

    @Test
    void rejectedAndInfoRequested_needANote() {
        for (ReturnStatus target : List.of(ReturnStatus.REJECTED, ReturnStatus.INFO_REQUESTED)) {
            returnIn(ReturnStatus.PENDING);
            returned(1, 0);
            for (String note : new String[] {null, "", "   "}) {
                assertThrows(BadRequestException.class, () -> f.service.updateStatus(1L, target, note, STAFF), target.name());
            }
        }
        verifyNoInteractions(f.orderService);
        verify(f.returns, never()).save(any(Return.class));
    }

    @Test
    void approved_needsNoNote_andKeepsTheOrderUntouched() {
        returnIn(ReturnStatus.PENDING);
        returned(1, 0);

        ReturnDto dto = f.service.updateStatus(1L, ReturnStatus.APPROVED, null, STAFF);

        assertEquals("APPROVED", dto.status);
        assertEquals(ReturnStatus.APPROVED, request.getStatus());
        verifyNoInteractions(f.orderService);
        verifyNoInteractions(f.inventory);
        verify(f.refunds, never()).save(any(Refund.class));
        verify(f.returns).save(request);
    }

    @Test
    void theAdminNoteIsStoredTrimmed_andKeptWhenTheNextChangeHasNone() {
        returnIn(ReturnStatus.PENDING);
        returned(1, 0);

        f.service.updateStatus(1L, ReturnStatus.INFO_REQUESTED, "  Vui lòng gửi thêm ảnh  ", STAFF);
        assertEquals("Vui lòng gửi thêm ảnh", request.getAdminNote());

        f.service.updateStatus(1L, ReturnStatus.APPROVED, null, STAFF);
        assertEquals("Vui lòng gửi thêm ảnh", request.getAdminNote());
    }

    // ---- history ----

    @Test
    void everyChange_appendsOneHistoryRow_soEarlierNotesAreNotLost() {
        returnIn(ReturnStatus.PENDING);
        returned(1, 0);

        f.service.updateStatus(1L, ReturnStatus.INFO_REQUESTED, "Gửi thêm ảnh", STAFF);
        f.service.updateStatus(1L, ReturnStatus.APPROVED, "Đã xem ảnh, đồng ý", STAFF);

        ArgumentCaptor<ReturnStatusHistory> rows = ArgumentCaptor.forClass(ReturnStatusHistory.class);
        verify(f.history, times(2)).save(rows.capture());
        ReturnStatusHistory first = rows.getAllValues().get(0);
        ReturnStatusHistory second = rows.getAllValues().get(1);
        assertEquals(ReturnStatus.PENDING, first.getOldStatus());
        assertEquals(ReturnStatus.INFO_REQUESTED, first.getNewStatus());
        assertEquals("Gửi thêm ảnh", first.getNote());
        assertEquals(STAFF, first.getChangedBy());
        assertEquals(ReturnStatus.INFO_REQUESTED, second.getOldStatus());
        assertEquals(ReturnStatus.APPROVED, second.getNewStatus());
        assertEquals("Đã xem ảnh, đồng ý", second.getNote());
        // the return row itself only keeps the latest note
        assertEquals("Đã xem ảnh, đồng ý", request.getAdminNote());
    }

    @Test
    void aRefusedChange_writesNoHistoryRow() {
        returnIn(ReturnStatus.PENDING);

        assertThrows(BusinessRuleViolationException.class,
                () -> f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF));

        verify(f.history, never()).save(any(ReturnStatusHistory.class));
    }

    // ---- REJECTED ----

    @Test
    void toRejected_movesOrderBackToDelivered() {
        returnIn(ReturnStatus.PENDING);
        returned(1, 0);

        ReturnDto dto = f.service.updateStatus(1L, ReturnStatus.REJECTED, "Không đủ điều kiện", STAFF);

        verify(f.orderService).transitionStatus(7L, OrderStatus.DELIVERED, STAFF, "Return rejected");
        assertEquals("REJECTED", dto.status);
        assertEquals("Không đủ điều kiện", dto.adminNote);
        verifyNoInteractions(f.inventory);
        verify(f.refunds, never()).save(any(Refund.class));
    }

    @Test
    void toRejected_legacyReturnWithoutItems_doesNotTouchTheOrder() {
        returnIn(ReturnStatus.PENDING);
        f.withReturnItems(); // created before B07: no items, and it never moved the order

        ReturnDto dto = f.service.updateStatus(1L, ReturnStatus.REJECTED, "Không đủ điều kiện", STAFF);

        assertEquals("REJECTED", dto.status);
        verifyNoInteractions(f.orderService);
    }

    // ---- RETURNED ----

    @Test
    void toReturned_restocksInventoryForEachItem() {
        // three lines entered out of order: products 30, 10, 20
        OrderItem line3 = f.orderItem(order, 103L, 30L, 4, "10000");
        f.withOrderItems(order, line1, line2, line3);
        returnIn(ReturnStatus.APPROVED);
        f.withReturnItems(
                ReturnServiceFixture.returnItem(request, line3, 1),
                ReturnServiceFixture.returnItem(request, line1, 2),
                ReturnServiceFixture.returnItem(request, line2, 3));

        f.service.updateStatus(1L, ReturnStatus.RETURNED, null, STAFF);

        InOrder inOrder = inOrder(f.inventory);
        inOrder.verify(f.inventory).restock(10L, 2, "RETURN", 1L);
        inOrder.verify(f.inventory).restock(20L, 3, "RETURN", 1L);
        inOrder.verify(f.inventory).restock(30L, 1, "RETURN", 1L);
        verify(f.inventory, times(3)).restock(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void toReturned_twoLinesOfTheSameProduct_areAddedUpIntoOneRestock() {
        OrderItem sameProduct = f.orderItem(order, 104L, 10L, 5, "100000");
        f.withOrderItems(order, line1, sameProduct);
        returnIn(ReturnStatus.APPROVED);
        f.withReturnItems(
                ReturnServiceFixture.returnItem(request, line1, 2),
                ReturnServiceFixture.returnItem(request, sameProduct, 3));

        f.service.updateStatus(1L, ReturnStatus.RETURNED, null, STAFF);

        // B03's idempotency key is (product, type, reference): a second call for the same product would be ignored
        verify(f.inventory, times(1)).restock(10L, 5, "RETURN", 1L);
        verify(f.inventory, times(1)).restock(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void toReturned_calledTwice_restocksOnce() {
        returnIn(ReturnStatus.APPROVED);
        returned(2, 3);

        f.service.updateStatus(1L, ReturnStatus.RETURNED, null, STAFF);
        assertThrows(BusinessRuleViolationException.class,
                () -> f.service.updateStatus(1L, ReturnStatus.RETURNED, null, STAFF));

        verify(f.inventory, times(1)).restock(10L, 2, "RETURN", 1L);
        verify(f.inventory, times(1)).restock(20L, 3, "RETURN", 1L);
        verify(f.orderService, times(1)).transitionStatus(eq(7L), eq(OrderStatus.RETURNED), any(), any());
        verify(f.events, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void toReturned_movesOrderToReturned() {
        returnIn(ReturnStatus.APPROVED);
        returned(1, 0);

        f.service.updateStatus(1L, ReturnStatus.RETURNED, null, STAFF);

        verify(f.orderService).transitionStatus(eq(7L), eq(OrderStatus.RETURNED), eq(STAFF), anyString());
        assertEquals(ReturnStatus.RETURNED, request.getStatus());
    }

    @Test
    void toReturned_legacyReturnWithoutItems_isRefused() {
        returnIn(ReturnStatus.APPROVED);
        f.withReturnItems();

        assertThrows(BusinessRuleViolationException.class,
                () -> f.service.updateStatus(1L, ReturnStatus.RETURNED, null, STAFF));
        verifyNoSideEffects(f);
        assertEquals(ReturnStatus.APPROVED, request.getStatus());
    }

    // ---- REFUNDED ----

    private Refund refundSaved() {
        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(f.refunds, times(1)).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void toRefunded_createsOnePendingRefundWithTheItemsAmount() {
        returnIn(ReturnStatus.RETURNED);
        returned(1, 1); // 1 x 100000 + 1 x 50000 = 150000 of an order of 2 + 3 units
        Payment payment = f.payment(order, Payment.Status.SUCCESS, "480000");

        f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF);

        Refund refund = refundSaved();
        assertEquals(0, new BigDecimal("150000").compareTo(refund.getAmount()), refund.getAmount().toPlainString());
        assertEquals(Refund.Status.PENDING, refund.getStatus());
        assertEquals("RETURN", refund.getReason());
        assertEquals(1L, refund.getReturnId());
        assertEquals(STAFF, refund.getCreatedBy());
        assertEquals(payment, refund.getPayment());
        assertEquals(order, refund.getOrder());
        assertNull(refund.getProviderTransactionId());
    }

    @Test
    void toRefunded_partialReturn_doesNotShareOutDiscountOrShipping() {
        // the payment is 410000 (items minus a discount plus shipping): only the item prices come back
        returnIn(ReturnStatus.RETURNED);
        returned(2, 0); // 2 x 100000 = 200000, units 2 of 5
        f.payment(order, Payment.Status.SUCCESS, "410000");

        f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF);

        assertEquals(0, new BigDecimal("200000").compareTo(refundSaved().getAmount()));
    }

    @Test
    void toRefunded_allUnitsReturned_refundsThePaymentAmount() {
        returnIn(ReturnStatus.RETURNED);
        returned(2, 3); // every ordered unit; items 350000, the payment also holds shipping and a discount
        f.payment(order, Payment.Status.SUCCESS, "362000.50");

        f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF);

        assertEquals(0, new BigDecimal("362000.50").compareTo(refundSaved().getAmount()));
    }

    @Test
    void toRefunded_amountIsNeverMoreThanThePayment() {
        returnIn(ReturnStatus.RETURNED);
        returned(2, 2); // 200000 + 100000 = 300000 of 5 units, but only 120000 was paid (large discount)
        f.payment(order, Payment.Status.SUCCESS, "120000");

        f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF);

        assertEquals(0, new BigDecimal("120000").compareTo(refundSaved().getAmount()));
    }

    @Test
    void toRefunded_paymentNotSuccess_createsNoRefundRow() {
        for (Payment.Status status : List.of(Payment.Status.PENDING, Payment.Status.FAILED, Payment.Status.CANCELLED,
                Payment.Status.REFUNDED)) {
            ReturnServiceFixture fixture = new ReturnServiceFixture();
            Order o = fixture.order(7L, 5L, OrderStatus.RETURN_REQUESTED);
            OrderItem item = fixture.orderItem(o, 101L, 10L, 2, "100000");
            fixture.withOrderItems(o, item);
            Return r = fixture.returnRow(1L, o, ReturnStatus.RETURNED);
            fixture.withReturnItems(ReturnServiceFixture.returnItem(r, item, 1));
            fixture.payment(o, status, "200000"); // for example cash on delivery that was never collected online

            ReturnDto dto = fixture.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF);

            verify(fixture.refunds, never()).save(any(Refund.class));
            verify(fixture.orderService).transitionStatus(eq(7L), eq(OrderStatus.REFUNDED), eq(STAFF),
                    contains("no online payment"));
            assertEquals("REFUNDED", dto.status, status.name());
            assertNull(dto.refund);
            assertEquals(ReturnDto.NO_REFUND_MESSAGE, dto.refundMessage);
        }
    }

    @Test
    void toRefunded_withoutAnyPaymentRow_createsNoRefundRow() {
        returnIn(ReturnStatus.RETURNED);
        returned(1, 0);
        when(f.payments.findByOrderIdForUpdate(7L)).thenReturn(Optional.empty());

        ReturnDto dto = f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF);

        verify(f.refunds, never()).save(any(Refund.class));
        assertEquals("REFUNDED", dto.status);
    }

    @Test
    void toRefunded_movesOrderToRefunded() {
        returnIn(ReturnStatus.RETURNED);
        returned(1, 0);
        f.payment(order, Payment.Status.SUCCESS, "200000");

        f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF);

        verify(f.orderService).transitionStatus(eq(7L), eq(OrderStatus.REFUNDED), eq(STAFF), contains("PENDING"));
        assertEquals(ReturnStatus.REFUNDED, request.getStatus());
        verifyNoInteractions(f.inventory); // stock was put back at RETURNED, never again
    }

    @Test
    void toRefunded_whenTheOrderAlreadyHasARefund_isRefusedAndChangesNothing() {
        returnIn(ReturnStatus.RETURNED);
        returned(1, 0);
        f.payment(order, Payment.Status.SUCCESS, "200000");
        when(f.refunds.existsByOrderIdAndStatusNot(7L, Refund.Status.FAILED)).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class,
                () -> f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF));

        verifyNoInteractions(f.orderService);
        verify(f.refunds, never()).save(any(Refund.class));
        verify(f.returns, never()).save(any(Return.class));
    }

    @Test
    void toRefunded_legacyReturnWithoutItems_isRefused() {
        returnIn(ReturnStatus.RETURNED);
        f.withReturnItems();
        f.payment(order, Payment.Status.SUCCESS, "200000");

        assertThrows(BusinessRuleViolationException.class,
                () -> f.service.updateStatus(1L, ReturnStatus.REFUNDED, null, STAFF));
        verifyNoSideEffects(f);
    }

    // ---- the order transition fails ----

    @Test
    void whenOrderTransitionFails_everythingRollsBack() {
        Object[][] cases = {
                {ReturnStatus.PENDING, ReturnStatus.REJECTED},
                {ReturnStatus.APPROVED, ReturnStatus.RETURNED},
                {ReturnStatus.RETURNED, ReturnStatus.REFUNDED},
        };
        for (Object[] c : cases) {
            ReturnStatus from = (ReturnStatus) c[0];
            ReturnStatus to = (ReturnStatus) c[1];
            ReturnServiceFixture fixture = new ReturnServiceFixture();
            Order o = fixture.order(7L, 5L, OrderStatus.RETURN_REQUESTED);
            OrderItem item = fixture.orderItem(o, 101L, 10L, 2, "100000");
            fixture.withOrderItems(o, item);
            Return r = fixture.returnRow(1L, o, from);
            fixture.withReturnItems(ReturnServiceFixture.returnItem(r, item, 1));
            fixture.payment(o, Payment.Status.SUCCESS, "200000");
            when(fixture.orderService.transitionStatus(anyLong(), any(OrderStatus.class), any(), any()))
                    .thenThrow(new BusinessRuleViolationException("không thể chuyển đơn hàng"));

            assertThrows(BusinessRuleViolationException.class,
                    () -> fixture.service.updateStatus(1L, to, "ghi chú", STAFF), from + " -> " + to);

            // The order is the first thing that fails, so the stock, the refund and the return row are never written; a
            // failure after a partial write is undone by the surrounding transaction (see the next test).
            verifyNoInteractions(fixture.inventory);
            verify(fixture.refunds, never()).save(any(Refund.class));
            verify(fixture.returns, never()).save(any(Return.class));
            verify(fixture.events, never()).publishEvent(any(Object.class));
            assertEquals(from, r.getStatus(), "the return keeps its status");
        }
    }

    @Test
    void updateStatus_runsInOneTransaction() throws Exception {
        assertTrue(ReturnService.class.getMethod("updateStatus", Long.class, ReturnStatus.class, String.class, Long.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }

    // ---- events ----

    @Test
    void everyChange_publishesOneStatusChangedEvent() {
        returnIn(ReturnStatus.PENDING);
        returned(1, 0);

        f.service.updateStatus(1L, ReturnStatus.APPROVED, null, STAFF);

        verify(f.events, times(1)).publishEvent(any(Object.class));
        verify(f.events).publishEvent((Object) new ReturnStatusChangedEvent(1L, 7L, ReturnStatus.PENDING, ReturnStatus.APPROVED));
    }

    @Test
    void aRefusedChange_publishesNothing() {
        returnIn(ReturnStatus.REFUNDED);

        assertThrows(BusinessRuleViolationException.class,
                () -> f.service.updateStatus(1L, ReturnStatus.APPROVED, "x", STAFF));

        verify(f.events, never()).publishEvent(any(Object.class));
    }

    // ---- queries ----

    @Test
    void getReturn_ofAnotherCustomer_isForbidden() {
        returnIn(ReturnStatus.PENDING);

        assertThrows(ForbiddenException.class,
                () -> f.service.getReturn(1L, new ReturnViewer(6L, false, false)));
    }

    @Test
    void getReturn_ownerAndStaffWithReturnView_areAllowed() {
        returnIn(ReturnStatus.APPROVED);
        returned(1, 0);

        assertEquals(1L, f.service.getReturn(1L, new ReturnViewer(5L, false, false)).id);
        assertEquals(1L, f.service.getReturn(1L, new ReturnViewer(77L, true, false)).id);
    }

    @Test
    void getReturn_unknown_isNotFound() {
        when(f.returns.findByIdWithUser(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> f.service.getReturn(99L, new ReturnViewer(5L, true, true)));
    }

    @Test
    void allowedNextStatuses_areServerDriven_andOnlyForStaffWhoCanProcess() {
        returnIn(ReturnStatus.PENDING);
        returned(1, 0);

        assertEquals(List.of("INFO_REQUESTED", "APPROVED", "REJECTED"),
                f.service.getReturn(1L, new ReturnViewer(77L, true, true)).allowedNextStatuses);
        assertEquals(List.of(), f.service.getReturn(1L, new ReturnViewer(77L, true, false)).allowedNextStatuses);
        assertEquals(List.of(), f.service.getReturn(1L, new ReturnViewer(5L, false, false)).allowedNextStatuses);
    }

    @Test
    void allowedNextStatuses_forAReturnWithoutItems_neverOfferReceivedOrRefunded() {
        returnIn(ReturnStatus.APPROVED);
        f.withReturnItems();

        assertEquals(List.of(), f.service.getReturn(1L, new ReturnViewer(77L, true, true)).allowedNextStatuses);
        assertEquals(List.of(), f.service.getReturn(1L, new ReturnViewer(77L, true, true)).items);
    }

    @Test
    void getReturn_showsItsRefund() {
        returnIn(ReturnStatus.REFUNDED);
        returned(1, 0);
        Refund refund = new Refund();
        refund.setAmount(new BigDecimal("100000"));
        refund.setStatus(Refund.Status.PENDING);
        refund.setReturnId(1L);
        when(f.refunds.findByReturnIdIn(any())).thenReturn(List.of(refund));

        ReturnDto dto = f.service.getReturn(1L, new ReturnViewer(5L, false, false));

        assertEquals("PENDING", dto.refund.status);
        assertEquals(0, new BigDecimal("100000").compareTo(dto.refund.amount));
        assertNull(dto.refundMessage);
    }
}
