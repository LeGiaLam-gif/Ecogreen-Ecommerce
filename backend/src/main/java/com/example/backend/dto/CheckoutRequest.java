package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/checkout. The ONLY inputs a client may send: where to ship, an optional discount code and the payment method.
 * Prices, totals, discount value, shipping fee, status and user id are never read from the body (unknown JSON fields are
 * ignored). The caller is the user of the verified token.
 *
 * <p>Address: either {@code addressId} (a saved address of the caller; someone else's is a 403) or the three text fields.
 * When {@code addressId} is given the text fields are ignored. Either way the values are copied into the order as a snapshot.
 */
public record CheckoutRequest(
        Long addressId,
        @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự.")
        String customerName,
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
        String customerPhone,
        @Size(max = 500, message = "Địa chỉ giao hàng tối đa 500 ký tự.")
        String shippingAddress,
        @Size(max = 30, message = "Mã giảm giá tối đa 30 ký tự.")
        String discountCode,
        @NotBlank(message = "Vui lòng chọn phương thức thanh toán.")
        @Size(max = 20, message = "Phương thức thanh toán không hợp lệ.")
        String paymentMethod) {
}
