package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.OrderStatusHistory;
import com.example.backend.entity.Payment;
import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static com.example.backend.entity.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** B05: OrderService.transitionStatus and the cancel rules, with mocked repositories (no database). */
class OrderServiceTransitionTest {

    private OrderServiceFixture f;

    @BeforeEach
    void setUp() {
        f = new OrderServiceFixture();
    }

    private Order orderWithOneItem(OrderStatus status) {
        Order order = f.order(7L, 5L, status);
        f.withItems(order, f.item(order, 10L, 2, "100000"));
        return order;
    }

    private void verifyNoStockMovement() {
        verifyNoInteractions(f.inventory);
    }

    private List<OrderStatusHistory> savedHistory(int expected) {
        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(f.history, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    // ---- inventory side effects (spec section 4) ----

    @Test
    void pendingPayment_toPaid_commitsInventory() {
        Order order = orderWithOneItem(PENDING_PAYMENT);

        f.service.transitionStatus(7L, PAID, null, "Mock payment");

        assertEquals(PAID, order.getStatus());
        verify(f.inventory).commit(10L, 2, "ORDER", 7L);
        verify(f.inventory, never()).release(anyLong(), anyInt(), anyString(), anyLong());
        verify(f.inventory, never()).restock(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void pendingPayment_toCancelled_releasesInventory() {
        Order order = orderWithOneItem(PENDING_PAYMENT);
        f.payment(order, "MOMO", Payment.Status.PENDING);

        f.service.transitionStatus(7L, CANCELLED, 5L, null);

        verify(f.inventory).release(10L, 2, "ORDER", 7L);
        verify(f.inventory, never()).restock(anyLong(), anyInt(), anyString(), anyLong());
        verify(f.inventory, never()).commit(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void paid_toCancelled_restocksNotReleases() {
        Order order = orderWithOneItem(PAID);
        f.payment(order, "MOMO", Payment.Status.SUCCESS);

        f.service.transitionStatus(7L, CANCELLED, 99L, "khách đổi ý");

        verify(f.inventory).restock(10L, 2, "ORDER", 7L);
        verify(f.inventory, never()).release(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void processing_toCancelled_restocksNotReleases() {
        Order order = orderWithOneItem(PROCESSING);
        f.payment(order, "COD", Payment.Status.PENDING);

        f.service.transitionStatus(7L, CANCELLED, 99L, null);

        verify(f.inventory).restock(10L, 2, "ORDER", 7L);
        verify(f.inventory, never()).release(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void transitionsWithoutStockEffect_neverTouchTheGateway() {
        orderWithOneItem(PAID);

        f.service.transitionStatus(7L, PROCESSING, 99L, null);

        verifyNoStockMovement();
    }

    // ---- COD rule ----

    @Test
    void cod_pendingPayment_toProcessing_allowed_andCommitsInventory() {
        Order order = orderWithOneItem(PENDING_PAYMENT);
        f.payment(order, "cod", Payment.Status.PENDING);

        f.service.transitionStatus(7L, PROCESSING, 99L, null);

        assertEquals(PROCESSING, order.getStatus());
        verify(f.inventory).commit(10L, 2, "ORDER", 7L);
    }

    @Test
    void nonCod_pendingPayment_toProcessing_isRejected_andNothingChanges() {
        Order order = orderWithOneItem(PENDING_PAYMENT);
        f.payment(order, "MOMO", Payment.Status.PENDING);

        assertThrows(BusinessRuleViolationException.class, () -> f.service.transitionStatus(7L, PROCESSING, 99L, null));

        assertEquals(PENDING_PAYMENT, order.getStatus());
        verifyNoStockMovement();
        verifyNoInteractions(f.history, f.events);
    }

    @Test
    void orderWithoutPayment_pendingPayment_toProcessing_isRejected() {
        orderWithOneItem(PENDING_PAYMENT);   // no payment row: the mock's findByOrderId answers Optional.empty()

        assertThrows(BusinessRuleViolationException.class, () -> f.service.transitionStatus(7L, PROCESSING, 99L, null));
    }

    // ---- validation ----

    @Test
    void transition_invalid_throwsBusinessRule_naming_fromAndTo() {
        f.order(7L, 5L, DELIVERED);
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> f.service.transitionStatus(7L, PAID, 99L, null));
        assertTrue(ex.getMessage().contains("DELIVERED") && ex.getMessage().contains("PAID"), ex.getMessage());

        f.order(8L, 5L, CANCELLED);
        assertThrows(BusinessRuleViolationException.class, () -> f.service.transitionStatus(8L, PAID, 99L, null));
        verifyNoInteractions(f.inventory, f.history, f.events);
    }

    @Test
    void transition_sameStatus_isRejected_neverASilentNoOp() {
        f.order(7L, 5L, PAID);

        assertThrows(BusinessRuleViolationException.class, () -> f.service.transitionStatus(7L, PAID, 99L, null));

        verifyNoInteractions(f.history, f.events, f.inventory);
    }

    @Test
    void transition_unknownOrder_isNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> f.service.transitionStatus(404L, PAID, null, null));
    }

    @Test
    void transition_nullStatus_isRejected() {
        assertThrows(BadRequestException.class, () -> f.service.transitionStatus(7L, null, null, null));
        verifyNoInteractions(f.orders);
    }

    // ---- locking ----

    @Test
    void transition_locksTheRowFirst_thenReReadsItBecauseOfOpenInView() {
        Order order = orderWithOneItem(PAID);

        f.service.transitionStatus(7L, PROCESSING, 99L, null);

        InOrder inOrder = inOrder(f.orders, f.entityManager);
        inOrder.verify(f.orders).findByIdForUpdate(7L);
        inOrder.verify(f.entityManager).refresh(order);
    }

    // ---- history and events ----

    @Test
    void transition_everyCall_writesHistoryRowWithActor() {
        Order order = orderWithOneItem(PENDING_PAYMENT);

        f.service.transitionStatus(7L, PAID, null, "Mock payment");   // system
        f.service.transitionStatus(7L, PROCESSING, 99L, "xác nhận");
        f.service.transitionStatus(7L, PACKED, 99L, null);

        List<OrderStatusHistory> rows = savedHistory(3);
        assertEquals(PENDING_PAYMENT, rows.get(0).getOldStatus());
        assertEquals(PAID, rows.get(0).getNewStatus());
        assertNull(rows.get(0).getChangedBy(), "a system transition has no actor");
        assertEquals("Mock payment", rows.get(0).getNote());
        assertEquals(PAID, rows.get(1).getOldStatus());
        assertEquals(PROCESSING, rows.get(1).getNewStatus());
        assertEquals(99L, rows.get(1).getChangedBy());
        assertEquals("xác nhận", rows.get(1).getNote());
        assertEquals(PACKED, rows.get(2).getNewStatus());
        assertEquals(order, rows.get(2).getOrder());
        assertEquals(PACKED, order.getStatus());
    }

    @Test
    void historyNote_isTruncatedTo500Characters() {
        orderWithOneItem(PAID);

        f.service.transitionStatus(7L, PROCESSING, 99L, "x".repeat(900));

        assertEquals(500, savedHistory(1).get(0).getNote().length());
    }

    @Test
    void transition_publishesStatusChangedEvent_withOldNewAndActor() {
        orderWithOneItem(PAID);

        f.service.transitionStatus(7L, PROCESSING, 99L, null);

        // The event is published inside the transaction; consumers listen AFTER_COMMIT (see OrderStatusChangedEvent).
        // That the listener only runs after commit is Spring's guarantee and is not provable without a transaction manager.
        verify(f.events).publishEvent((Object) new OrderStatusChangedEvent(7L, PAID, PROCESSING, 99L));
        verify(f.events, times(1)).publishEvent(any(Object.class));
    }

    // ---- cancellation ----

    @Test
    void cancel_paidOrder_writesRefundRequiredNote_andPublishesCancelledEvent() {
        Order order = orderWithOneItem(PAID);
        f.payment(order, "MOMO", Payment.Status.SUCCESS);

        f.service.transitionStatus(7L, CANCELLED, 99L, "khách yêu cầu");

        assertEquals("REFUND_REQUIRED: khách yêu cầu", savedHistory(1).get(0).getNote());
        verify(f.events).publishEvent((Object) new OrderCancelledEvent(7L, true));
        verify(f.events).publishEvent((Object) new OrderStatusChangedEvent(7L, PAID, CANCELLED, 99L));
    }

    @Test
    void cancel_paidOrder_withoutNote_stillMarksRefundRequired() {
        Order order = orderWithOneItem(PAID);
        f.payment(order, "MOMO", Payment.Status.SUCCESS);

        f.service.transitionStatus(7L, CANCELLED, 99L, null);

        assertEquals("REFUND_REQUIRED", savedHistory(1).get(0).getNote());
    }

    @Test
    void cancel_unpaidOrder_hasNoRefundMarker() {
        Order order = orderWithOneItem(PENDING_PAYMENT);
        f.payment(order, "MOMO", Payment.Status.PENDING);

        f.service.transitionStatus(7L, CANCELLED, 5L, "đổi ý");

        assertEquals("đổi ý", savedHistory(1).get(0).getNote());
        verify(f.events).publishEvent((Object) new OrderCancelledEvent(7L, false));
    }

    @Test
    void cancel_byOwner_whenPendingPayment_releasesStock() {
        Order order = orderWithOneItem(PENDING_PAYMENT);
        f.payment(order, "MOMO", Payment.Status.PENDING);

        f.service.cancelOwnOrder(7L, 5L, null);

        assertEquals(CANCELLED, order.getStatus());
        verify(f.inventory).release(10L, 2, "ORDER", 7L);
        assertEquals(5L, savedHistory(1).get(0).getChangedBy());
    }

    @Test
    void cancel_byNonOwner_isForbidden() {
        orderWithOneItem(PENDING_PAYMENT);

        assertThrows(ForbiddenException.class, () -> f.service.cancelOwnOrder(7L, 6L, null));

        verifyNoInteractions(f.inventory, f.history, f.events);
    }

    @Test
    void cancel_byOwnerWhenPaid_isRejected() {
        Order order = orderWithOneItem(PAID);

        assertThrows(BusinessRuleViolationException.class, () -> f.service.cancelOwnOrder(7L, 5L, null));

        assertEquals(PAID, order.getStatus());
        verifyNoInteractions(f.inventory, f.history, f.events);
    }
}
