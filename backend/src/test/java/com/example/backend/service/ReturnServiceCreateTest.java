package com.example.backend.service;

import com.example.backend.dto.ReturnDto;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Return;
import com.example.backend.entity.ReturnImage;
import com.example.backend.entity.ReturnItem;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.event.ReturnCreatedEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B07: ReturnService.create with mocked repositories (no database). Order 7 belongs to user 5 and has two lines. */
class ReturnServiceCreateTest {

    private ReturnServiceFixture f;
    private Order order;
    private OrderItem line1; // id 101, product 10, ordered 2 x 100000
    private OrderItem line2; // id 102, product 20, ordered 3 x 50000

    @BeforeEach
    void setUp() {
        f = new ReturnServiceFixture();
        order = f.order(7L, 5L, OrderStatus.DELIVERED);
        line1 = f.orderItem(order, 101L, 10L, 2, "100000");
        line2 = f.orderItem(order, 102L, 20L, 3, "50000");
        f.withOrderItems(order, line1, line2);
    }

    private ReturnDto create(long userId, List<byte[]> images, List<ReturnItemDraft> items) {
        return f.service.create(userId, 7L, "Sản phẩm bị lỗi", "Bị vỡ khi nhận", images, items);
    }

    private static List<byte[]> oneImage() {
        return List.of(ReturnServiceFixture.jpeg());
    }

    private static List<ReturnItemDraft> oneItem() {
        return List.of(new ReturnItemDraft(101L, 1));
    }

    private void verifyNothingWasWritten() {
        verify(f.returns, never()).save(any(Return.class));
        verify(f.storage, never()).store(any(byte[].class), anyString());
        verifyNoInteractions(f.orderService);
        verify(f.events, never()).publishEvent(any(Object.class));
    }

    @Test
    void orderNotDelivered_isRefused() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.DELIVERED) {
                continue;
            }
            ReturnServiceFixture other = new ReturnServiceFixture();
            Order wrongStatus = other.order(7L, 5L, status);
            other.withOrderItems(wrongStatus, other.orderItem(wrongStatus, 101L, 10L, 2, "100000"));

            assertThrows(BusinessRuleViolationException.class, () ->
                    other.service.create(5L, 7L, "lỗi", null, oneImage(), oneItem()), status.name());
            verify(other.returns, never()).save(any(Return.class));
            verifyNoInteractions(other.orderService);
        }
    }

    @Test
    void byNonOwner_forbidden() {
        assertThrows(ForbiddenException.class, () -> create(6L, oneImage(), oneItem()));
        verifyNothingWasWritten();
    }

    @Test
    void unknownOrder_isNotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                f.service.create(5L, 999L, "lỗi", null, oneImage(), oneItem()));
        verifyNothingWasWritten();
    }

    @Test
    void itemFromAnotherOrder_refused() {
        assertThrows(BadRequestException.class, () ->
                create(5L, oneImage(), List.of(new ReturnItemDraft(999L, 1))));
        verifyNothingWasWritten();
    }

    @Test
    void quantityAboveOrdered_refused() {
        assertThrows(BadRequestException.class, () ->
                create(5L, oneImage(), List.of(new ReturnItemDraft(101L, 3)))); // ordered 2
        verifyNothingWasWritten();
    }

    @Test
    void quantityZeroOrNegative_refused() {
        assertThrows(BadRequestException.class, () -> create(5L, oneImage(), List.of(new ReturnItemDraft(101L, 0))));
        assertThrows(BadRequestException.class, () -> create(5L, oneImage(), List.of(new ReturnItemDraft(101L, -1))));
        verifyNothingWasWritten();
    }

    @Test
    void sameOrderItemTwice_refused() {
        assertThrows(BadRequestException.class, () ->
                create(5L, oneImage(), List.of(new ReturnItemDraft(101L, 1), new ReturnItemDraft(101L, 1))));
        verifyNothingWasWritten();
    }

    @Test
    void emptyItems_refused() {
        assertThrows(BadRequestException.class, () -> create(5L, oneImage(), List.of()));
        assertThrows(BadRequestException.class, () -> create(5L, oneImage(), null));
        verifyNothingWasWritten();
    }

    @Test
    void blankReason_refused() {
        assertThrows(BadRequestException.class, () ->
                f.service.create(5L, 7L, "   ", null, oneImage(), oneItem()));
        verifyNothingWasWritten();
    }

    @Test
    void tooManyOrInvalidImages_refused() {
        // none, null, six
        assertThrows(BadRequestException.class, () -> create(5L, List.of(), oneItem()));
        assertThrows(BadRequestException.class, () -> create(5L, null, oneItem()));
        List<byte[]> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(ReturnServiceFixture.jpeg());
        }
        assertThrows(BadRequestException.class, () -> create(5L, six, oneItem()));

        // not an image (an SVG with a script, an executable, an empty file), whatever the client called it
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes();
        byte[] exe = new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        for (byte[] bad : List.of(svg, exe, new byte[0])) {
            assertThrows(BadRequestException.class, () -> create(5L, List.of(bad), oneItem()));
        }

        // one valid image next to an invalid one: nothing is stored
        assertThrows(BadRequestException.class, () -> create(5L, List.of(ReturnServiceFixture.jpeg(), svg), oneItem()));

        // over 5 MB
        byte[] big = new byte[(int) ProductImageService.MAX_BYTES + 1];
        System.arraycopy(ReturnServiceFixture.jpeg(), 0, big, 0, 4);
        assertThrows(BadRequestException.class, () -> create(5L, List.of(big), oneItem()));

        verifyNothingWasWritten();
    }

    @Test
    void whenAnotherReturnIsOpen_refused() {
        when(f.returns.existsOpenReturn(eq(7L), any())).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class, () -> create(5L, oneImage(), oneItem()));
        verifyNothingWasWritten();
    }

    @Test
    void openStatusesAreTheOnesThatBlockANewReturn() {
        ArgumentCaptor<java.util.Collection<ReturnStatus>> statuses = ArgumentCaptor.forClass(java.util.Collection.class);
        create(5L, oneImage(), oneItem());

        verify(f.returns).existsOpenReturn(eq(7L), statuses.capture());
        assertEquals(java.util.EnumSet.of(ReturnStatus.PENDING, ReturnStatus.INFO_REQUESTED, ReturnStatus.APPROVED),
                java.util.EnumSet.copyOf(statuses.getValue()));
    }

    @Test
    void success_movesOrderToReturnRequested() {
        ReturnDto dto = create(5L, List.of(ReturnServiceFixture.jpeg(), ReturnServiceFixture.png()),
                List.of(new ReturnItemDraft(101L, 2), new ReturnItemDraft(102L, 1)));

        verify(f.orderService).transitionStatus(7L, OrderStatus.RETURN_REQUESTED, 5L, "Return requested");
        verify(f.events, times(1)).publishEvent(any(Object.class));
        verify(f.events).publishEvent((Object) new ReturnCreatedEvent(50L, 7L, 5L));

        ArgumentCaptor<Return> saved = ArgumentCaptor.forClass(Return.class);
        verify(f.returns).save(saved.capture());
        assertEquals(ReturnStatus.PENDING, saved.getValue().getStatus());
        assertEquals(5L, saved.getValue().getUserId());
        assertEquals(7L, saved.getValue().getOrderId());
        assertEquals("Sản phẩm bị lỗi", saved.getValue().getReason());
        assertEquals("Bị vỡ khi nhận", saved.getValue().getDescription());

        assertEquals(50L, dto.id);
        assertEquals(7L, dto.orderId);
        assertEquals("PENDING", dto.status);
        assertEquals(2, dto.items.size());
        assertEquals(101L, dto.items.get(0).orderItemId);
        assertEquals(10L, dto.items.get(0).productId);
        assertEquals(2, dto.items.get(0).quantity);
        assertEquals(0, new BigDecimal("100000").compareTo(dto.items.get(0).unitPrice));
        assertEquals(2, dto.images.size());
        assertTrue(dto.images.get(0).startsWith("/api/v1/files/"), dto.images.get(0));
        assertEquals(List.of(), dto.allowedNextStatuses);
    }

    @Test
    void success_storesImagesThroughTheSharedStorage_withSortOrder() {
        create(5L, List.of(ReturnServiceFixture.jpeg(), ReturnServiceFixture.png()), oneItem());

        verify(f.storage).store(any(byte[].class), eq("jpg"));
        verify(f.storage).store(any(byte[].class), eq("png"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ReturnImage>> images = ArgumentCaptor.forClass(Iterable.class);
        verify(f.returnImages).saveAll(images.capture());
        List<ReturnImage> rows = new ArrayList<>();
        images.getValue().forEach(rows::add);
        assertEquals(2, rows.size());
        assertEquals(0, rows.get(0).getSortOrder());
        assertEquals(1, rows.get(1).getSortOrder());
    }

    @Test
    void success_savesOneReturnItemPerLine() {
        create(5L, oneImage(), List.of(new ReturnItemDraft(101L, 1), new ReturnItemDraft(102L, 3)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ReturnItem>> items = ArgumentCaptor.forClass(Iterable.class);
        verify(f.returnItems).saveAll(items.capture());
        List<ReturnItem> rows = new ArrayList<>();
        items.getValue().forEach(rows::add);
        assertEquals(2, rows.size());
        assertEquals(101L, rows.get(0).getOrderItem().getId());
        assertEquals(1, rows.get(0).getQuantity());
        assertEquals(102L, rows.get(1).getOrderItem().getId());
        assertEquals(3, rows.get(1).getQuantity());
    }

    @Test
    void whenTheOrderTransitionFails_storedFilesAreRemovedAndNothingIsAnnounced() {
        when(f.orderService.transitionStatus(anyLong(), any(OrderStatus.class), any(), any()))
                .thenThrow(new BusinessRuleViolationException("không thể chuyển"));

        assertThrows(BusinessRuleViolationException.class, () ->
                create(5L, List.of(ReturnServiceFixture.jpeg(), ReturnServiceFixture.png()), oneItem()));

        verify(f.storage, times(2)).store(any(byte[].class), anyString());
        verify(f.storage, times(2)).delete(anyString());
        verify(f.events, never()).publishEvent(any(Object.class));
    }

    @Test
    void create_runsInOneTransaction() throws Exception {
        assertTrue(ReturnService.class.getMethod("create", Long.class, Long.class, String.class, String.class,
                        List.class, List.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }
}
