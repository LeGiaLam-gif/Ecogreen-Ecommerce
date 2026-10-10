package com.example.backend.service;

import com.example.backend.dto.OrderResponse;
import com.example.backend.dto.OrderStatusHistoryResponse;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.OrderStatusHistory;
import com.example.backend.entity.Payment;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.example.backend.entity.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B05: order lists, detail, history and the server-computed flags, with mocked repositories. */
class OrderServiceQueryTest {

    private static final OrderViewer CUSTOMER = new OrderViewer(5L, false, false, false);
    private static final OrderViewer STAFF = new OrderViewer(99L, true, true, true);
    private static final OrderViewer STAFF_NO_CANCEL = new OrderViewer(99L, true, true, false);

    private OrderServiceFixture f;

    @BeforeEach
    void setUp() {
        f = new OrderServiceFixture();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pageOf(List<Order> orders) {
        Page<Order> page = new PageImpl<>(orders, PageRequest.of(0, 20), orders.size());
        when(f.orders.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    }

    private List<Order> ordersWithItems(int count, OrderStatus status) {
        List<Order> orders = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Order order = f.order(i, 5L, status);
            orders.add(order);
            items.add(f.item(order, 10L + i, 1, "100000"));
            items.add(f.item(order, 50L + i, 2, "50000"));
        }
        when(f.items.findByOrderIdInWithProduct(any())).thenReturn(items);
        return orders;
    }

    // ---- the N+1 fix ----

    @Test
    void listMyOrders_paged_repositoryCallsIndependentOfRows() {
        for (int rows : new int[] {1, 20}) {
            f = new OrderServiceFixture();
            pageOf(ordersWithItems(rows, PENDING_PAYMENT));

            Page<OrderResponse> result = f.service.listMyOrders(5L, null, PageRequest.of(0, 20));

            assertEquals(rows, result.getContent().size());
            assertEquals(2, result.getContent().get(0).items.size());
            // one page query, ONE items-with-products query for all rows, nothing per order
            verify(f.orders, times(1)).findAll(any(Specification.class), any(Pageable.class));
            verify(f.items, times(1)).findByOrderIdInWithProduct(any());
            verify(f.items, never()).findByOrderIdWithProduct(org.mockito.ArgumentMatchers.anyLong());
            verifyNoInteractions(f.payments);
        }
    }

    @Test
    void listMyOrders_emptyPage_skipsTheItemsQuery() {
        pageOf(List.of());

        Page<OrderResponse> result = f.service.listMyOrders(5L, null, PageRequest.of(0, 20));

        assertTrue(result.isEmpty());
        verify(f.items, never()).findByOrderIdInWithProduct(any());
    }

    @Test
    void listMyOrders_ownerSeesCanCancelOnlyWhilePendingPayment_andNoStaffOptions() {
        Order pending = f.order(1L, 5L, PENDING_PAYMENT);
        Order paid = f.order(2L, 5L, PAID);
        when(f.items.findByOrderIdInWithProduct(any())).thenReturn(List.of());
        pageOf(List.of(pending, paid));

        List<OrderResponse> rows = f.service.listMyOrders(5L, null, PageRequest.of(0, 20)).getContent();

        assertTrue(rows.get(0).canCancel);
        assertFalse(rows.get(1).canCancel);
        assertTrue(rows.get(0).allowedNextStatuses.isEmpty(), "a customer never gets staff options");
        assertEquals(5L, rows.get(0).userId);
    }

    // ---- admin list ----

    @Test
    void listAdminOrders_staff_getsAllowedNextStatuses_withOneBatchPaymentQuery() {
        Order cod = f.order(1L, 5L, PENDING_PAYMENT);
        Order momo = f.order(2L, 6L, PENDING_PAYMENT);
        Payment codPayment = f.payment(cod, "COD", Payment.Status.PENDING);
        Payment momoPayment = f.payment(momo, "MOMO", Payment.Status.PENDING);
        when(f.payments.findByOrderIdIn(any())).thenReturn(List.of(codPayment, momoPayment));
        when(f.items.findByOrderIdInWithProduct(any())).thenReturn(List.of());
        pageOf(List.of(cod, momo));

        List<OrderResponse> rows = f.service
                .listAdminOrders(null, null, null, null, PageRequest.of(0, 20), STAFF).getContent();

        assertEquals(List.of("PAID", "PROCESSING", "CANCELLED"), rows.get(0).allowedNextStatuses);
        assertEquals(List.of("PAID", "CANCELLED"), rows.get(1).allowedNextStatuses, "PROCESSING is COD only");
        verify(f.payments, times(1)).findByOrderIdIn(any());
        verify(f.payments, never()).findByOrderId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void listAdminOrders_viewerWithoutUpdate_getsNoOptions_andNoPaymentQuery() {
        Order order = f.order(1L, 5L, PAID);
        when(f.items.findByOrderIdInWithProduct(any())).thenReturn(List.of());
        pageOf(List.of(order));

        List<OrderResponse> rows = f.service.listAdminOrders(null, null, null, null, PageRequest.of(0, 20),
                new OrderViewer(99L, true, false, false)).getContent();

        assertTrue(rows.get(0).allowedNextStatuses.isEmpty());
        verifyNoInteractions(f.payments);
    }

    @Test
    void listAdminOrders_fromAfterTo_isRejected() {
        assertThrows(BadRequestException.class, () -> f.service.listAdminOrders(null, null,
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 1), PageRequest.of(0, 20), STAFF));
        verifyNoInteractions(f.orders);
    }

    // ---- allowedNextFor (pure) ----

    @Test
    void allowedNextFor_hidesCancelledWithoutOrderCancel() {
        assertEquals(List.of("PROCESSING"), OrderService.allowedNextFor(PAID, false, STAFF_NO_CANCEL));
        assertEquals(List.of("PROCESSING", "CANCELLED"), OrderService.allowedNextFor(PAID, false, STAFF));
    }

    @Test
    void allowedNextFor_neverOffersAnythingFromATerminalStatus() {
        assertTrue(OrderService.allowedNextFor(CANCELLED, true, STAFF).isEmpty());
        assertTrue(OrderService.allowedNextFor(REFUNDED, true, STAFF).isEmpty());
    }

    @Test
    void allowedNextFor_everyOfferedStatus_isInTheTransitionTable() {
        for (OrderStatus status : OrderStatus.values()) {
            for (boolean cod : new boolean[] {true, false}) {
                for (String offered : OrderService.allowedNextFor(status, cod, STAFF)) {
                    assertTrue(status.canTransitionTo(OrderStatus.valueOf(offered)), status + " -> " + offered);
                }
            }
        }
    }

    // ---- detail and history ----

    @Test
    void getOrderView_owner_ok_nonOwnerWithoutView_forbidden_staffWithView_ok() {
        Order order = f.order(7L, 5L, PENDING_PAYMENT);
        f.withItems(order, f.item(order, 10L, 2, "100000"));

        OrderResponse own = f.service.getOrderView(7L, CUSTOMER);
        assertTrue(own.canCancel);
        assertEquals(1, own.items.size());

        assertThrows(ForbiddenException.class, () -> f.service.getOrderView(7L, new OrderViewer(6L, false, false, false)));

        OrderResponse staff = f.service.getOrderView(7L, STAFF);
        assertFalse(staff.canCancel, "canCancel is the owner's flag");
        assertEquals(List.of("PAID", "CANCELLED"), staff.allowedNextStatuses);
    }

    @Test
    void getOrderView_unknownOrder_isNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> f.service.getOrderView(404L, STAFF));
    }

    @Test
    void getHistory_staffSeesInternalFields_ownerDoesNot_strangerIsForbidden() {
        Order order = f.order(7L, 5L, CANCELLED);
        OrderStatusHistory row = new OrderStatusHistory();
        row.setOrder(order);
        row.setOldStatus(PAID);
        row.setNewStatus(CANCELLED);
        row.setChangedBy(99L);
        row.setNote("REFUND_REQUIRED");
        when(f.history.findByOrderIdOrderByChangedAtAscIdAsc(7L)).thenReturn(List.of(row));

        OrderStatusHistoryResponse asStaff = f.service.getHistory(7L, STAFF).get(0);
        assertEquals(99L, asStaff.changedBy());
        assertEquals("REFUND_REQUIRED", asStaff.note());

        OrderStatusHistoryResponse asOwner = f.service.getHistory(7L, CUSTOMER).get(0);
        assertNull(asOwner.changedBy());
        assertNull(asOwner.note());
        assertEquals("PAID", asOwner.oldStatus());
        assertEquals("CANCELLED", asOwner.newStatus());

        assertThrows(ForbiddenException.class, () -> f.service.getHistory(7L, new OrderViewer(6L, false, false, false)));
    }
}
