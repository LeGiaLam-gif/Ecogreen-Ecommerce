package com.example.backend.service;

/**
 * Who is looking at an order, reduced to what OrderService needs. Built by the controller from the verified token
 * ({@code CurrentUser}); nothing here comes from the request body.
 *
 * @param userId        the caller's user id
 * @param canViewOthers holds {@code order:view}: may open any order and see the internal history fields
 * @param canUpdate     holds {@code order:update}: gets {@code allowedNextStatuses}
 * @param canCancel     holds {@code order:cancel}: CANCELLED is offered in {@code allowedNextStatuses}
 */
public record OrderViewer(Long userId, boolean canViewOthers, boolean canUpdate, boolean canCancel) {
}
