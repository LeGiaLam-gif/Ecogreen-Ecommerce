package com.example.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.ReturnResponse;
import com.example.backend.entity.ReturnRequest;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.ReturnRequestService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/returns")
public class ReturnRequestController {

    @Autowired
    private ReturnRequestService returnService;

    @Autowired
    private AuthGuard authGuard;

    /**
     * Khách hàng tạo yêu cầu đổi/trả hàng.
     */
    @PostMapping
    public ResponseEntity<ReturnResponse> create(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        User user = authGuard.requireUser(request);

        if (body == null || body.get("orderId") == null || body.get("orderId").isBlank()) {
            throw new BadRequestException("Mã đơn hàng không được để trống.");
        }
        if (body.get("reason") == null || body.get("reason").trim().isEmpty()) {
            throw new BadRequestException("Vui lòng cung cấp lý do đổi/trả hàng.");
        }

        Long orderId;
        try {
            orderId = Long.valueOf(body.get("orderId").trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Mã đơn hàng không hợp lệ: " + body.get("orderId"));
        }

        ReturnRequest rr = returnService.create(
                user.getId(),
                orderId,
                body.get("reason").trim(),
                body.get("description"),
                body.get("imageUrl")
        );

        return ResponseEntity.status(201).body(ReturnResponse.from(rr));
    }

    /**
     * Khách hàng xem đơn đổi/trả của mình.
     */
    @GetMapping("/my")
    public List<ReturnResponse> myReturns(HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        return returnService.getByUser(user.getId()).stream()
                .map(ReturnResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Admin xem tất cả yêu cầu đổi/trả.
     */
    @GetMapping
    public List<ReturnResponse> allReturns(HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return returnService.getAll().stream()
                .map(ReturnResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Admin phê duyệt hoặc từ chối yêu cầu đổi/trả.
     */
    @PutMapping("/{id}/status")
    public ReturnResponse updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return ReturnResponse.from(returnService.updateStatus(id, body.get("status"), body.get("adminNote")));
    }
}
