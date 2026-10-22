package com.example.backend.service.notification;

import com.example.backend.api.ApiPaging;
import com.example.backend.entity.Notification;
import com.example.backend.entity.NotificationStatus;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Read side of B10: a customer's own notifications and the admin delivery log. Always paged, never {@code findAll()}. */
@Service
public class NotificationQueryService {

    private static final Map<String, String> MY_SORT = Map.of("createdAt", "createdAt", "id", "id");
    private static final Map<String, String> ADMIN_SORT = Map.of("createdAt", "createdAt", "id", "id", "status", "status", "type", "type");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationRepository repository;

    public NotificationQueryService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Size default 20, max 100; {@code sort} whitelisted (createdAt, id); default newest first. */
    public static Pageable myPageable(Integer page, Integer size, String sort) {
        return ApiPaging.of(page, size, sort, MY_SORT, DEFAULT_SORT);
    }

    /** Same limits; {@code sort} whitelisted (createdAt, id, status, type). */
    public static Pageable adminPageable(Integer page, Integer size, String sort) {
        return ApiPaging.of(page, size, sort, ADMIN_SORT, DEFAULT_SORT);
    }

    /** Only rows of {@code userId}; the caller passes the id from the verified token, never from the request. */
    @Transactional(readOnly = true)
    public Page<Notification> listMine(Long userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable);
    }

    /**
     * Delivery log, optionally one status. A blank {@code status} means all.
     *
     * @throws BadRequestException (VALIDATION_ERROR) for a value that is not a {@link NotificationStatus}
     */
    @Transactional(readOnly = true)
    public Page<Notification> listAll(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return repository.findAll(pageable);
        }
        NotificationStatus wanted;
        try {
            wanted = NotificationStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Trạng thái thông báo không hợp lệ.");
        }
        return repository.findByStatus(wanted, pageable);
    }
}
