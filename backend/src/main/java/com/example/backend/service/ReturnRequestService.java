package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.ReturnRequest;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.ReturnRequestRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReturnRequestService {

    @Autowired private ReturnRequestRepository returnRepo;
    @Autowired private OrderRepository orderRepo;
    @Autowired private UserRepository userRepo;

    /** Khách hàng tạo yêu cầu đổi/trả. Chỉ cho phép khi đơn hàng đã PAID. */
    @Transactional
    public ReturnRequest create(Long userId, Long orderId, String reason, String description, String imageUrl) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền thực hiện thao tác này.");
        }
        if (order.getStatus() != Order.Status.PAID) {
            throw new BadRequestException("Chỉ có thể yêu cầu đổi/trả cho đơn hàng đã thanh toán.");
        }

        // Kiểm tra đã có yêu cầu đổi/trả pending chưa
        List<ReturnRequest> existing = returnRepo.findByOrderId(orderId);
        boolean hasPending = existing.stream().anyMatch(r -> r.getStatus() == ReturnRequest.Status.PENDING);
        if (hasPending) {
            throw new BadRequestException("Đơn hàng này đã có yêu cầu đổi/trả đang chờ xử lý.");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng."));

        ReturnRequest rr = new ReturnRequest();
        rr.setOrder(order);
        rr.setUser(user);
        rr.setReason(reason);
        rr.setDescription(description);
        rr.setImageUrl(imageUrl);
        rr.setStatus(ReturnRequest.Status.PENDING);
        return returnRepo.save(rr);
    }

    /** Khách hàng xem đơn đổi/trả của mình. */
    public List<ReturnRequest> getByUser(Long userId) {
        return returnRepo.findByUserId(userId);
    }

    /** Admin xem tất cả đơn đổi/trả. */
    public List<ReturnRequest> getAll() {
        return returnRepo.findAllByOrderByCreatedAtDesc();
    }

    /** Admin phê duyệt hoặc từ chối đơn đổi/trả. */
    @Transactional
    public ReturnRequest updateStatus(Long returnId, String statusStr, String adminNote) {
        ReturnRequest rr = returnRepo.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi/trả #" + returnId));

        ReturnRequest.Status newStatus;
        try {
            newStatus = ReturnRequest.Status.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Trạng thái không hợp lệ: " + statusStr);
        }

        rr.setStatus(newStatus);
        if (adminNote != null && !adminNote.isBlank()) {
            rr.setAdminNote(adminNote);
        }
        return returnRepo.save(rr);
    }
}
