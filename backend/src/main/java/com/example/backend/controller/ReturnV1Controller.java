package com.example.backend.controller;

import com.example.backend.api.ApiPaging;
import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.ReturnDto;
import com.example.backend.dto.ReturnItemsJson;
import com.example.backend.dto.UpdateReturnStatusRequest;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.ReturnService;
import com.example.backend.service.ReturnViewer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Returns on the /api/v1 contract (B07). The caller is read from the verified token ({@link CurrentUser}); the owner of a
 * return, the order, prices and every status are decided by the server, never taken from the request. Permissions are
 * enforced here with {@code return:view} / {@code return:process}; ownership is enforced in {@link ReturnService}.
 */
@RestController
@RequestMapping("/api/v1")
public class ReturnV1Controller {

    /** API sort field -> entity property (the whitelist; client text never reaches JPQL). */
    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired private ReturnService returnService;
    @Autowired private AuthGuard authGuard;

    /**
     * A customer returns items of one of their DELIVERED orders. Multipart parts: {@code orderId}, {@code reason},
     * {@code description} (optional), {@code items} (JSON text: {@code [{"orderItemId":1,"quantity":2}]}) and 1 to 5
     * {@code images} files.
     */
    @PostMapping(value = "/returns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReturnDto>> create(
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "items", required = false) String items,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest request) throws IOException {
        CurrentUser current = requireCurrent(request);
        List<byte[]> contents = new ArrayList<>();
        if (images != null) {
            for (MultipartFile file : images) {
                contents.add(file.getBytes());
            }
        }
        ReturnDto created = returnService.create(current.getUserId(), orderId, reason, description, contents,
                ReturnItemsJson.parse(items));
        return ResponseEntity.status(201).body(ApiResponse.of(created));
    }

    /** The caller's own returns, paged. */
    @GetMapping("/returns/my")
    public ApiResponse<List<ReturnDto>> myReturns(@RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String sort,
                                                  HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        Pageable pageable = ApiPaging.of(page, size, sort, SORTS, DEFAULT_SORT);
        return PageResponse.of(returnService.listMyReturns(current.getUserId(), pageable));
    }

    /** One return: its owner, or staff with {@code return:view}. */
    @GetMapping("/returns/{id}")
    public ApiResponse<ReturnDto> getReturn(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.of(returnService.getReturn(id, viewerOf(requireCurrent(request))));
    }

    /** Staff: every return, optionally filtered by status, paged. */
    @GetMapping("/admin/returns")
    public ApiResponse<List<ReturnDto>> adminReturns(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     @RequestParam(required = false) String sort,
                                                     HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.RETURN_VIEW);
        CurrentUser current = requireCurrent(request);
        Pageable pageable = ApiPaging.of(page, size, sort, SORTS, DEFAULT_SORT);
        return PageResponse.of(returnService.listAdminReturns(parseStatusFilter(status), pageable, viewerOf(current)));
    }

    /**
     * Staff: move a return to another status. An invalid transition is a 400 BUSINESS_RULE_VIOLATION; {@code adminNote} is
     * required for REJECTED and INFO_REQUESTED.
     */
    @PatchMapping("/admin/returns/{id}/status")
    public ApiResponse<ReturnDto> updateStatus(@PathVariable Long id,
                                               @RequestBody @Valid UpdateReturnStatusRequest body,
                                               HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.RETURN_PROCESS);
        ReturnStatus newStatus = parseStatus(body.newStatus());
        if (newStatus.requiresAdminNote() && (body.adminNote() == null || body.adminNote().isBlank())) {
            throw new BadRequestException("Vui lòng nhập ghi chú phản hồi cho khách hàng.");
        }
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(returnService.updateStatus(id, newStatus, body.adminNote(), current.getUserId()));
    }

    private static CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        }
        return current;
    }

    private static ReturnViewer viewerOf(CurrentUser current) {
        return new ReturnViewer(current.getUserId(),
                current.hasPermission(Permissions.RETURN_VIEW),
                current.hasPermission(Permissions.RETURN_PROCESS));
    }

    private static ReturnStatus parseStatusFilter(String status) {
        return status == null || status.isBlank() ? null : parseStatus(status);
    }

    private static ReturnStatus parseStatus(String status) {
        try {
            return ReturnStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Trạng thái đổi/trả không hợp lệ: " + status);
        }
    }
}
