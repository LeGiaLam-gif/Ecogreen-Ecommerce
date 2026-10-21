package com.example.backend.service;

import com.example.backend.dto.ReturnDto;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Refund;
import com.example.backend.entity.Return;
import com.example.backend.entity.ReturnImage;
import com.example.backend.entity.ReturnItem;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.entity.ReturnStatusHistory;
import com.example.backend.event.ReturnCreatedEvent;
import com.example.backend.event.ReturnStatusChangedEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
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
import com.example.backend.storage.ImageSniffer;
import com.example.backend.storage.ObjectStorageClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Return and refund of delivered goods (B07).
 *
 * <p><b>Order status.</b> This class never writes {@code orders.status}: every change goes through
 * {@link OrderService#transitionStatus} (DELIVERED to RETURN_REQUESTED on create; back to DELIVERED on reject; RETURNED;
 * REFUNDED). <b>Stock.</b> B07 is the only place that restocks returned goods ({@code OrderInventoryPolicy} gives RETURNED and
 * REFUNDED no effect), through {@link InventoryGateway#restock} with reference type {@code RETURN} and the return id, so B03's
 * idempotency rule makes a repeated call a no-op. <b>Money.</b> A return never completes a refund: it records ONE
 * {@code PENDING} refund and staff finish it with B06's endpoint.
 *
 * <p>Every method that changes data runs in one transaction: the return, the order, the stock and the refund commit or roll
 * back together. Side effects run first and the return row is saved and announced last, so a failing order transition leaves
 * nothing behind.
 */
@Service
public class ReturnService {

    /** Reference type written to the stock ledger for returned goods; the reference id is the return id. */
    public static final String INVENTORY_REF_TYPE = "RETURN";
    /** Reason stored on the refund row. */
    public static final String REFUND_REASON = "RETURN";
    public static final int MIN_IMAGES = 1;
    public static final int MAX_IMAGES = 5;

    private static final int REASON_MAX = 100;
    private static final int DESCRIPTION_MAX = 2000;
    private static final int NOTE_MAX = 2000;

    @Autowired private ReturnRepository returnRepository;
    @Autowired private ReturnItemRepository returnItemRepository;
    @Autowired private ReturnImageRepository returnImageRepository;
    @Autowired private ReturnStatusHistoryRepository returnHistoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderService orderService;
    @Autowired private InventoryGateway inventoryGateway;
    @Autowired private ObjectStorageClient storage;
    @Autowired private ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------------------------------------------------

    /**
     * A customer returns some items of one of their DELIVERED orders. Rules: the caller owns the order; the order is
     * DELIVERED; at least one item, each from this order, unique, with 1 up to its ordered quantity; no other return of the
     * order is PENDING, INFO_REQUESTED or APPROVED; 1 to {@value #MAX_IMAGES} images, each checked by size and magic bytes
     * (the same rules and storage as the catalogue images). In ONE transaction the return is saved and the order moves
     * DELIVERED to RETURN_REQUESTED.
     *
     * <p>A legacy PAID order is refused: the order state machine has no PAID to RETURN_REQUESTED step and decision D-4 keeps
     * legacy PAID orders PAID until staff advance them to DELIVERED.
     *
     * @param images the raw bytes of each uploaded file (the controller reads them from the multipart request)
     */
    @Transactional
    public ReturnDto create(Long userId, Long orderId, String reason, String description,
                            List<byte[]> images, List<ReturnItemDraft> items) {
        if (userId == null || orderId == null) {
            throw new BadRequestException("Thiếu thông tin đơn hàng cần đổi/trả.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BadRequestException("Vui lòng cung cấp lý do đổi/trả hàng.");
        }
        if (cleanReason.length() > REASON_MAX) {
            throw new BadRequestException("Lý do đổi/trả tối đa " + REASON_MAX + " ký tự.");
        }
        String cleanDescription = description == null || description.isBlank() ? null : description.trim();
        if (cleanDescription != null && cleanDescription.length() > DESCRIPTION_MAX) {
            throw new BadRequestException("Mô tả tối đa " + DESCRIPTION_MAX + " ký tự.");
        }
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ít nhất một sản phẩm cần trả.");
        }
        List<ImageSniffer.Detected> types = validateImages(images);

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn hàng này.");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleViolationException("Chỉ có thể yêu cầu đổi/trả cho đơn hàng đã giao thành công.");
        }
        if (returnRepository.existsOpenReturn(orderId, ReturnStatus.openStatuses())) {
            throw new BusinessRuleViolationException("Đơn hàng này đã có yêu cầu đổi/trả đang được xử lý.");
        }

        Map<Long, OrderItem> orderItems = new HashMap<>();
        for (OrderItem orderItem : orderItemRepository.findByOrderIdWithProduct(orderId)) {
            orderItems.put(orderItem.getId(), orderItem);
        }
        Set<Long> seen = new HashSet<>();
        for (ReturnItemDraft draft : items) {
            if (draft == null || draft.orderItemId() == null) {
                throw new BadRequestException("Danh sách sản phẩm cần trả không hợp lệ.");
            }
            OrderItem orderItem = orderItems.get(draft.orderItemId());
            if (orderItem == null) {
                throw new BadRequestException("Sản phẩm cần trả không thuộc đơn hàng này.");
            }
            if (!seen.add(draft.orderItemId())) {
                throw new BadRequestException("Mỗi sản phẩm chỉ được chọn một lần.");
            }
            if (draft.quantity() < 1 || draft.quantity() > orderItem.getQuantity()) {
                throw new BadRequestException("Số lượng trả phải từ 1 đến " + orderItem.getQuantity()
                        + " cho sản phẩm \"" + orderItem.getProduct().getName() + "\".");
            }
        }

        List<String> storedKeys = new ArrayList<>();
        try {
            Return request = new Return();
            request.setOrder(order);
            request.setUser(userRepository.getReferenceById(userId));
            request.setReason(cleanReason);
            request.setDescription(cleanDescription);
            request.setStatus(ReturnStatus.PENDING);
            request = returnRepository.save(request);
            writeHistory(request, null, ReturnStatus.PENDING, userId, cleanReason);

            List<ReturnItem> savedItems = new ArrayList<>();
            for (ReturnItemDraft draft : items) {
                ReturnItem item = new ReturnItem();
                item.setReturnRequest(request);
                item.setOrderItem(orderItems.get(draft.orderItemId()));
                item.setQuantity(draft.quantity());
                savedItems.add(item);
            }
            savedItems = returnItemRepository.saveAll(savedItems);

            List<ReturnImage> savedImages = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                String key = storage.store(images.get(i), types.get(i).extension());
                storedKeys.add(key);
                ReturnImage image = new ReturnImage();
                image.setReturnRequest(request);
                image.setImageUrl(ProductImageService.FILE_URL_PREFIX + key);
                image.setSortOrder(i);
                savedImages.add(image);
            }
            savedImages = returnImageRepository.saveAll(savedImages);

            orderService.transitionStatus(orderId, OrderStatus.RETURN_REQUESTED, userId, "Return requested");

            eventPublisher.publishEvent(new ReturnCreatedEvent(request.getId(), orderId, userId));
            return ReturnDto.of(request, savedItems, savedImages, null, List.of());
        } catch (RuntimeException e) {
            // Do not leave files behind a return that was not saved (the transaction rolls the rows back).
            for (String key : storedKeys) {
                try {
                    storage.delete(key);
                } catch (RuntimeException ignored) {
                    // best effort: the original failure is what matters
                }
            }
            throw e;
        }
    }

    /** 1 to {@value #MAX_IMAGES} images, each non-empty, within the size limit and a real JPEG, PNG or WebP. */
    private static List<ImageSniffer.Detected> validateImages(List<byte[]> images) {
        if (images == null || images.size() < MIN_IMAGES) {
            throw new BadRequestException("Vui lòng đính kèm từ " + MIN_IMAGES + " đến " + MAX_IMAGES + " ảnh minh chứng.");
        }
        if (images.size() > MAX_IMAGES) {
            throw new BadRequestException("Chỉ được đính kèm tối đa " + MAX_IMAGES + " ảnh.");
        }
        List<ImageSniffer.Detected> types = new ArrayList<>();
        for (byte[] content : images) {
            if (content == null || content.length == 0) {
                throw new BadRequestException("Vui lòng chọn một tệp ảnh.");
            }
            if (content.length > ProductImageService.MAX_BYTES) {
                throw new BadRequestException("Ảnh vượt quá dung lượng cho phép (tối đa 5 MB).");
            }
            ImageSniffer.Detected type = ImageSniffer.detect(content);
            if (type == null) {
                throw new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP hợp lệ.");
            }
            types.add(type);
        }
        return types;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Status changes (staff)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Staff move a return to {@code newStatus}. The return row is locked, the transition table in {@link ReturnStatus} is
     * enforced (anything else is a 400 {@code BUSINESS_RULE_VIOLATION}), and the side effects run in the same transaction:
     *
     * <ul>
     *   <li>REJECTED: the order goes back to DELIVERED (returns created before B07 never moved the order: no change).</li>
     *   <li>RETURNED: the order goes to RETURNED and every returned unit is restocked once, products in ascending id order
     *       (B03 lock-ordering rule).</li>
     *   <li>REFUNDED: ONE PENDING refund is recorded for the returned items (see {@link #refundAmount}) when the payment was
     *       collected online, then the order goes to REFUNDED. Staff finish the refund with B06's endpoint.</li>
     * </ul>
     *
     * <p>A return created before B07 has no items, so it cannot be moved to RETURNED or REFUNDED.
     *
     * @param adminNote required for REJECTED and INFO_REQUESTED
     */
    @Transactional
    public ReturnDto updateStatus(Long returnId, ReturnStatus newStatus, String adminNote, Long actorUserId) {
        if (newStatus == null) {
            throw new BadRequestException("Trạng thái mới là bắt buộc.");
        }
        String note = adminNote == null || adminNote.isBlank() ? null : adminNote.trim();
        if (note != null && note.length() > NOTE_MAX) {
            throw new BadRequestException("Ghi chú tối đa " + NOTE_MAX + " ký tự.");
        }

        Return request = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi/trả #" + returnId));
        ReturnStatus oldStatus = request.getStatus();
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new BusinessRuleViolationException(
                    "Không thể chuyển yêu cầu đổi/trả từ " + oldStatus + " sang " + newStatus + ".");
        }
        if (newStatus.requiresAdminNote() && note == null) {
            throw new BadRequestException("Vui lòng nhập ghi chú phản hồi cho khách hàng.");
        }

        Long orderId = request.getOrderId();
        List<ReturnItem> items = returnItemRepository.findByReturnIdInWithProduct(List.of(returnId));
        switch (newStatus) {
            case REJECTED -> {
                if (!items.isEmpty()) {
                    orderService.transitionStatus(orderId, OrderStatus.DELIVERED, actorUserId, "Return rejected");
                }
            }
            case RETURNED -> {
                requireItems(items);
                // The order is locked and checked first; stock is touched only when the order really moves.
                orderService.transitionStatus(orderId, OrderStatus.RETURNED, actorUserId, "Return received");
                restock(items, returnId);
            }
            case REFUNDED -> {
                requireItems(items);
                refund(request, items, actorUserId);
            }
            default -> {
                // INFO_REQUESTED and APPROVED change the return only.
            }
        }

        request.setStatus(newStatus);
        if (note != null) {
            request.setAdminNote(note);
        }
        returnRepository.save(request);
        writeHistory(request, oldStatus, newStatus, actorUserId, note);
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, orderId, oldStatus, newStatus));
        return withHistory(assemble(List.of(request), new ReturnViewer(actorUserId, true, true)).get(0));
    }

    private static void requireItems(List<ReturnItem> items) {
        if (items.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Yêu cầu đổi/trả cũ không có danh sách sản phẩm nên không thể nhập kho hoặc hoàn tiền.");
        }
    }

    /** Puts every returned unit back, one call per product in ascending product id (units of one product are added up). */
    private void restock(List<ReturnItem> items, Long returnId) {
        Map<Long, Integer> perProduct = new TreeMap<>();
        for (ReturnItem item : items) {
            perProduct.merge(item.getOrderItem().getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> line : perProduct.entrySet()) {
            inventoryGateway.restock(line.getKey(), line.getValue(), INVENTORY_REF_TYPE, returnId);
        }
    }

    /**
     * Records the refund of a return and moves the order to REFUNDED. The payment row is locked first, like the payment
     * webhook does, so the two never wait for each other in opposite orders. No refund row is created when the payment
     * was not collected online (for example cash on delivery): there is no money to send back, and the order history note
     * says so.
     */
    private void refund(Return request, List<ReturnItem> items, Long actorUserId) {
        Long orderId = request.getOrderId();
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);
        BigDecimal amount = null;
        if (payment != null && payment.getStatus() == Payment.Status.SUCCESS) {
            List<OrderItem> orderItems = orderItemRepository.findByOrderIdWithProduct(orderId);
            amount = refundAmount(items, orderItems, payment.getAmount());
            if (amount.signum() <= 0) {
                amount = null;
            }
        }
        boolean refundable = amount != null;
        if (refundable && refundRepository.existsByOrderIdAndStatusNot(orderId, Refund.Status.FAILED)) {
            throw new BusinessRuleViolationException("Đơn hàng này đã có khoản hoàn tiền.");
        }

        orderService.transitionStatus(orderId, OrderStatus.REFUNDED, actorUserId, refundable
                ? "Return refunded (refund recorded as PENDING)"
                : "Return refunded - no online payment to refund");

        if (refundable) {
            Refund refund = new Refund();
            refund.setPayment(payment);
            refund.setOrder(request.getOrder());
            refund.setAmount(amount);
            refund.setStatus(Refund.Status.PENDING);
            refund.setReason(REFUND_REASON);
            refund.setCreatedBy(actorUserId);
            refund.setReturnId(request.getId());
            refundRepository.save(refund);
        }
    }

    /**
     * The money a return gives back: the sum of {@code order_item.price x returned quantity}, never more than the payment.
     * When every unit of the order is returned it is exactly the payment amount (so the discount and the shipping fee come
     * back too). For a partial return the order's discount and shipping fee are NOT shared out: the customer is refunded
     * the item prices only (open decision, see the contract).
     */
    static BigDecimal refundAmount(List<ReturnItem> returned, List<OrderItem> orderItems, BigDecimal paymentAmount) {
        long returnedUnits = 0;
        BigDecimal itemsAmount = BigDecimal.ZERO;
        for (ReturnItem item : returned) {
            returnedUnits += item.getQuantity();
            itemsAmount = itemsAmount.add(item.getOrderItem().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        long orderedUnits = orderItems.stream().mapToLong(OrderItem::getQuantity).sum();
        if (orderedUnits > 0 && returnedUnits == orderedUnits) {
            return paymentAmount;
        }
        return itemsAmount.min(paymentAmount);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------------------

    /** The caller's own returns, paged. Five queries for any page size: page, count, then the items, images and refunds of the whole page. */
    @Transactional(readOnly = true)
    public Page<ReturnDto> listMyReturns(Long userId, Pageable pageable) {
        Page<Return> page = returnRepository.findByUser(userId, pageable);
        return new PageImpl<>(assemble(page.getContent(), new ReturnViewer(userId, false, false)),
                pageable, page.getTotalElements());
    }

    /** Staff: every return, optionally in one status, paged. */
    @Transactional(readOnly = true)
    public Page<ReturnDto> listAdminReturns(ReturnStatus status, Pageable pageable, ReturnViewer viewer) {
        Page<Return> page = status == null
                ? returnRepository.findAllForAdmin(pageable)
                : returnRepository.findByStatusForAdmin(status, pageable);
        return new PageImpl<>(assemble(page.getContent(), viewer), pageable, page.getTotalElements());
    }

    /** One return: its owner, or staff with {@code return:view}. Anyone else gets 403. */
    @Transactional(readOnly = true)
    public ReturnDto getReturn(Long returnId, ReturnViewer viewer) {
        Return request = returnRepository.findByIdWithUser(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi/trả #" + returnId));
        if (!viewer.canViewAll() && !Objects.equals(request.getUserId(), viewer.userId())) {
            throw new ForbiddenException("Bạn không có quyền truy cập yêu cầu đổi/trả này.");
        }
        return withHistory(assemble(List.of(request), viewer).get(0));
    }

    /** Appends one history row (the whole decision trail; {@code returns.admin_note} keeps only the latest note). */
    private void writeHistory(Return request, ReturnStatus oldStatus, ReturnStatus newStatus, Long actorUserId, String note) {
        ReturnStatusHistory row = new ReturnStatusHistory();
        row.setReturnRequest(request);
        row.setOldStatus(oldStatus);
        row.setNewStatus(newStatus);
        row.setChangedBy(actorUserId);
        row.setNote(note);
        returnHistoryRepository.save(row);
    }

    /** Adds the timeline to a single-return view (lists do not carry it, to keep a page at five queries). */
    private ReturnDto withHistory(ReturnDto dto) {
        dto.history = returnHistoryRepository.findTimeline(dto.id).stream().map(ReturnDto.HistoryDto::from).toList();
        return dto;
    }

    /** Builds the DTOs of a page of returns with three batched queries (items, images, refunds) instead of three per row. */
    private List<ReturnDto> assemble(List<Return> returns, ReturnViewer viewer) {
        if (returns.isEmpty()) {
            return List.of();
        }
        List<Long> ids = returns.stream().map(Return::getId).toList();
        Map<Long, List<ReturnItem>> itemsByReturn = returnItemRepository.findByReturnIdInWithProduct(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getReturnRequest().getId()));
        Map<Long, List<ReturnImage>> imagesByReturn = returnImageRepository.findByReturnIdIn(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getReturnRequest().getId()));
        Map<Long, Refund> refundByReturn = new HashMap<>();
        for (Refund refund : refundRepository.findByReturnIdIn(ids)) {
            refundByReturn.putIfAbsent(refund.getReturnId(), refund);
        }

        List<ReturnDto> dtos = new ArrayList<>();
        for (Return request : returns) {
            List<ReturnItem> items = itemsByReturn.getOrDefault(request.getId(), List.of());
            dtos.add(ReturnDto.of(request, items,
                    imagesByReturn.getOrDefault(request.getId(), List.of()),
                    refundByReturn.get(request.getId()),
                    allowedNextStatuses(request, !items.isEmpty(), viewer)));
        }
        return dtos;
    }

    /** What staff may do next. A return without items (created before B07) cannot be received or refunded. */
    private static List<String> allowedNextStatuses(Return request, boolean hasItems, ReturnViewer viewer) {
        if (!viewer.canProcess()) {
            return List.of();
        }
        return request.getStatus().allowedNext().stream()
                .filter(next -> hasItems || (next != ReturnStatus.RETURNED && next != ReturnStatus.REFUNDED))
                .map(Enum::name)
                .toList();
    }
}
