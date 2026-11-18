package com.example.backend.service;

import com.example.backend.entity.AuditLog;
import com.example.backend.entity.OrderStatus;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogServiceTest {

    @Test
    void adminMutationRecordsActorResourceMethodAndResultWithoutRequestBody() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository);

        service.recordAdminMutation(7L, "PATCH", "/api/v1/admin/products/42", 200);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        AuditLog log = saved.getValue();
        assertEquals(7L, log.getActorUserId());
        assertEquals("ADMIN_API_PATCH", log.getAction());
        assertEquals("products", log.getResourceType());
        assertEquals("42", log.getResourceId());
        assertEquals(200, log.getResponseStatus());
        assertNull(log.getDetailsJson());
    }

    @Test
    void orderStatusAuditPreservesBeforeAndAfterValues() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository);

        service.recordOrderStatusChange(new OrderStatusChangedEvent(
                42L, OrderStatus.PACKED, OrderStatus.SHIPPED, 7L));

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        AuditLog log = saved.getValue();
        assertEquals("ORDER_STATUS_CHANGED", log.getAction());
        assertEquals("orders", log.getResourceType());
        assertEquals("42", log.getResourceId());
        assertTrue(log.getDetailsJson().contains("\"oldStatus\":\"PACKED\""));
        assertTrue(log.getDetailsJson().contains("\"newStatus\":\"SHIPPED\""));
    }
}
