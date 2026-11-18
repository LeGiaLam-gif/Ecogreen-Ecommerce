package com.example.backend.service;

import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditLogFilterTest {

    @Test
    void authenticatedAdminMutationIsRecordedAfterTheControllerCompletes() throws Exception {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditLogFilter filter = new AuditLogFilter(auditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/admin/products/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(AuthInterceptor.REQUEST_ATTR, new CurrentUser(7L, List.of("ADMIN"), List.of()));
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(200);

        filter.doFilter(request, response, chain);

        verify(auditLogService).recordAdminMutation(7L, "PATCH", "/api/v1/admin/products/42", 200);
    }

    @Test
    void readOnlyRequestsAreNotAuditedAsMutations() throws Exception {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditLogFilter filter = new AuditLogFilter(auditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        verifyNoInteractions(auditLogService);
    }
}
