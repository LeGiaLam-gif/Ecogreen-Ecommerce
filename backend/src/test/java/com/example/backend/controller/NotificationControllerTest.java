package com.example.backend.controller;

import com.example.backend.entity.Notification;
import com.example.backend.entity.NotificationChannel;
import com.example.backend.entity.NotificationStatus;
import com.example.backend.entity.NotificationType;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.repository.NotificationRepository;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.notification.NotificationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B10 endpoints through the real GlobalExceptionHandler and AuthGuard, over the REAL NotificationQueryService with a mocked
 * repository (standalone MockMvc). The request attribute stands in for what AuthInterceptor sets from a verified token.
 */
class NotificationControllerTest {

    private NotificationRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        NotificationQueryService queries = new NotificationQueryService(repository);

        NotificationV1Controller mine = new NotificationV1Controller();
        ReflectionTestUtils.setField(mine, "notificationQueryService", queries);
        AdminNotificationV1Controller admin = new AdminNotificationV1Controller();
        ReflectionTestUtils.setField(admin, "notificationQueryService", queries);
        ReflectionTestUtils.setField(admin, "authGuard", new AuthGuard());

        mvc = MockMvcBuilders.standaloneSetup(mine, admin).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static Notification row(Long id, Long userId, NotificationStatus status, String reason) {
        Notification n = new Notification(userId, NotificationType.PAYMENT_SUCCESS, NotificationChannel.EMAIL,
                "EcoGreen: Thanh toán thành công", "Xin chào lan,\n\nĐã nhận thanh toán.", status, reason, "order:5:PAID");
        ReflectionTestUtils.setField(n, "id", id);
        ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.of(2026, 10, 21, 12, 0));
        return n;
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long userId, String... permissions) {
        return b.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(userId, List.of("CUSTOMER"), List.of(permissions)));
    }

    // ── /users/me/notifications ──────────────────────────────────────────────

    @Test
    void myNotifications_returnsOnlyOwn() throws Exception {
        when(repository.findByUserId(eq(42L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row(1L, 42L, NotificationStatus.SENT, null))));

        mvc.perform(as(get("/api/v1/users/me/notifications"), 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].type").value("PAYMENT_SUCCESS"))
                .andExpect(jsonPath("$.data[0].title").value("EcoGreen: Thanh toán thành công"))
                .andExpect(jsonPath("$.data[0].status").value("SENT"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        verify(repository).findByUserId(eq(42L), any(Pageable.class));
        verify(repository, never()).findAll(any(Pageable.class));
        verify(repository, never()).findByStatus(any(), any());
    }

    @Test
    void myNotifications_ignoresAUserIdInTheQueryString() throws Exception {
        when(repository.findByUserId(eq(42L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(as(get("/api/v1/users/me/notifications").param("userId", "7").param("user_id", "7"), 42L))
                .andExpect(status().isOk());

        verify(repository).findByUserId(eq(42L), any(Pageable.class));
        verify(repository, never()).findByUserId(eq(7L), any(Pageable.class));
    }

    @Test
    void myNotifications_neverExposesInternalFailureDetails() throws Exception {
        when(repository.findByUserId(eq(42L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row(2L, 42L, NotificationStatus.FAILED, "SEND_ERROR"))));

        mvc.perform(as(get("/api/v1/users/me/notifications"), 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].failureReason").doesNotExist())
                .andExpect(jsonPath("$.data[0].dedupeKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].userId").doesNotExist());
    }

    @Test
    void myNotifications_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/users/me/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        verify(repository, never()).findByUserId(any(), any());
    }

    @Test
    void myNotifications_defaultsTo20NewestFirst_clampsSize_andRejectsAnUnknownSort() throws Exception {
        when(repository.findByUserId(eq(42L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(as(get("/api/v1/users/me/notifications"), 42L)).andExpect(status().isOk());
        mvc.perform(as(get("/api/v1/users/me/notifications").param("size", "5000"), 42L)).andExpect(status().isOk());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2)).findByUserId(eq(42L), pageable.capture());
        assertEquals(20, pageable.getAllValues().get(0).getPageSize());
        assertEquals(100, pageable.getAllValues().get(1).getPageSize());
        assertEquals("createdAt: DESC", pageable.getAllValues().get(0).getSort().getOrderFor("createdAt").toString());

        mvc.perform(as(get("/api/v1/users/me/notifications").param("sort", "body,asc"), 42L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── /admin/notifications ─────────────────────────────────────────────────

    @Test
    void adminNotifications_requiresAnalyticsView() throws Exception {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        String path = "/api/v1/admin/notifications";

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(as(get(path), 42L, Permissions.ORDER_VIEW)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(as(get(path), 42L, Permissions.INVENTORY_VIEW)).andExpect(status().isForbidden());
        verify(repository, never()).findAll(any(Pageable.class));

        mvc.perform(as(get(path), 42L, Permissions.ANALYTICS_VIEW)).andExpect(status().isOk());
        verify(repository).findAll(any(Pageable.class));
    }

    @Test
    void adminNotifications_invalidStatus_returns400() throws Exception {
        mvc.perform(as(get("/api/v1/admin/notifications").param("status", "DELIVERED"), 42L, Permissions.ANALYTICS_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(repository, never()).findAll(any(Pageable.class));
        verify(repository, never()).findByStatus(any(), any());
    }

    @Test
    void adminNotifications_filtersByStatus_andShowsTheFailureCodeButNoBody() throws Exception {
        when(repository.findByStatus(eq(NotificationStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row(3L, 7L, NotificationStatus.FAILED, "SEND_ERROR"))));

        mvc.perform(as(get("/api/v1/admin/notifications").param("status", "failed"), 42L, Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].userId").value(7))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].failureReason").value("SEND_ERROR"))
                .andExpect(jsonPath("$.data[0].body").doesNotExist())
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        verify(repository).findByStatus(eq(NotificationStatus.FAILED), any(Pageable.class));
    }

    @Test
    void adminNotifications_sortWhitelist() throws Exception {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(as(get("/api/v1/admin/notifications").param("sort", "status,asc"), 42L, Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/v1/admin/notifications").param("sort", "body,asc"), 42L, Permissions.ANALYTICS_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
