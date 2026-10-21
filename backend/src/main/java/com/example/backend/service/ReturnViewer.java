package com.example.backend.service;

/**
 * Who is looking at a return, reduced to what ReturnService needs. Built by the controller from the verified token.
 *
 * @param userId     the caller's user id
 * @param canViewAll holds {@code return:view}: may open any return
 * @param canProcess holds {@code return:process}: gets {@code allowedNextStatuses}
 */
public record ReturnViewer(Long userId, boolean canViewAll, boolean canProcess) {
}
