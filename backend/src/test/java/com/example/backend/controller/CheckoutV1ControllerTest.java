package com.example.backend.controller;

import com.example.backend.dto.CheckoutPreviewResponse;
import com.example.backend.dto.CheckoutRequest;
import com.example.backend.dto.OrderResponse;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.CheckoutService;
import com.example.backend.service.OrderService;
import com.example.backend.service.OrderViewer;
import com.example.backend.service.inventory.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B04: POST /api/v1/checkout and /checkout/preview through standalone MockMvc (real controller, real GlobalExceptionHandler,
 * mocked services): who the order is placed for, what the client can and cannot send, the status codes of every refusal.
 */
class CheckoutV1ControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final long CALLER = 42L;

    private CheckoutService checkoutService;
    private OrderService orderService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        checkoutService = mock(CheckoutService.class);
        orderService = mock(OrderService.class);
        CheckoutV1Controller controller = new CheckoutV1Controller();
        ReflectionTestUtils.setField(controller, "checkoutService", checkoutService);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();

        Order placed = new Order();
        placed.setId(100L);
        placed.setStatus(OrderStatus.PENDING_PAYMENT);
        when(checkoutService.checkout(anyLong(), any(CheckoutRequest.class))).thenReturn(placed);
        OrderResponse view = OrderResponse.from(placed, List.of());
        view.subtotal = new BigDecimal("250000.00");
        view.discountAmount = new BigDecimal("25000.00");
        view.shippingFee = new BigDecimal("30000.00");
        view.totalPrice = new BigDecimal("255000.00");
        view.discountCode = "SALE10";
        when(orderService.getOrderView(eq(100L), any(OrderViewer.class))).thenReturn(view);
    }

    private static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(CALLER, List.of("CUSTOMER"), List.of()));
    }

    // ---- who may call ----

    @Test
    void checkoutAndPreview_withoutToken_are401() throws Exception {
        mvc.perform(post("/api/v1/checkout").contentType(JSON).content("{\"paymentMethod\":\"COD\",\"addressId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/checkout/preview").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(checkoutService, orderService);
    }

    // ---- POST /checkout ----

    @Test
    void checkout_is201_withTheOrderInTheEnvelope() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON)
                        .content("{\"addressId\":8,\"discountCode\":\"SALE10\",\"paymentMethod\":\"MOMO\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.subtotal").value(250000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(25000.00))
                .andExpect(jsonPath("$.data.shippingFee").value(30000.00))
                .andExpect(jsonPath("$.data.totalPrice").value(255000.00))
                .andExpect(jsonPath("$.data.discountCode").value("SALE10"));
    }

    @Test
    void checkout_ignoresClientSuppliedPriceTotalDiscountShippingStatusAndUserId() throws Exception {
        String sneaky = "{\"addressId\":8,\"paymentMethod\":\"COD\",\"discountCode\":\"SALE10\","
                + "\"userId\":999,\"price\":1,\"unitPrice\":1,\"subtotal\":1,\"totalPrice\":1,\"total\":1,"
                + "\"discountAmount\":99999,\"shippingFee\":0,\"status\":\"PAID\",\"items\":[{\"productId\":1,\"price\":1}]}";

        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON).content(sneaky)))
                .andExpect(status().isCreated());

        // the order is placed for the user of the token, and only the typed fields reached the service
        ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(checkoutService).checkout(eq(CALLER), request.capture());
        assertEquals(new CheckoutRequest(8L, null, null, null, "SALE10", "COD"), request.getValue());
        // and the order is read back as its owner (never as staff)
        ArgumentCaptor<OrderViewer> viewer = ArgumentCaptor.forClass(OrderViewer.class);
        verify(orderService).getOrderView(eq(100L), viewer.capture());
        assertEquals(CALLER, viewer.getValue().userId());
    }

    @Test
    void checkout_withTheTextAddress_passesTheThreeFields() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON).content(
                        "{\"customerName\":\"An\",\"customerPhone\":\"0901234567\",\"shippingAddress\":\"1 Lê Lợi\",\"paymentMethod\":\"COD\"}")))
                .andExpect(status().isCreated());

        ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(checkoutService).checkout(eq(CALLER), request.capture());
        assertNull(request.getValue().addressId());
        assertEquals("An", request.getValue().customerName());
        assertEquals("0901234567", request.getValue().customerPhone());
        assertEquals("1 Lê Lợi", request.getValue().shippingAddress());
        assertNull(request.getValue().discountCode());
    }

    @Test
    void checkout_withoutAPaymentMethod_is400ValidationError() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON).content("{\"addressId\":8}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.paymentMethod").exists());
        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON).content("{\"addressId\":8,\"paymentMethod\":\"  \"}")))
                .andExpect(status().isBadRequest());

        verify(checkoutService, never()).checkout(anyLong(), any());
    }

    @Test
    void checkout_overlongFields_are400() throws Exception {
        String longCode = "X".repeat(31);

        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON)
                        .content("{\"addressId\":8,\"paymentMethod\":\"COD\",\"discountCode\":\"" + longCode + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.discountCode").exists());
        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON)
                        .content("{\"paymentMethod\":\"COD\",\"customerName\":\"" + "A".repeat(101)
                                + "\",\"customerPhone\":\"0901234567\",\"shippingAddress\":\"x\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.customerName").exists());

        verify(checkoutService, never()).checkout(anyLong(), any());
    }

    @Test
    void checkout_emptyCart_is400BusinessRuleViolation() throws Exception {
        when(checkoutService.checkout(anyLong(), any(CheckoutRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Giỏ hàng của bạn đang trống."));

        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON).content("{\"addressId\":8,\"paymentMethod\":\"COD\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value("Giỏ hàng của bạn đang trống."));
    }

    @Test
    void checkout_insufficientStock_is400_namingTheProduct() throws Exception {
        when(checkoutService.checkout(anyLong(), any(CheckoutRequest.class)))
                .thenThrow(new InsufficientStockException(10L, 3, "Bình nước"));

        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON).content("{\"addressId\":8,\"paymentMethod\":\"COD\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value("Sản phẩm \"Bình nước\" không đủ số lượng tồn kho."));
    }

    @Test
    void checkout_invalidDiscountCode_is400_withTheGenericMessage() throws Exception {
        when(checkoutService.checkout(anyLong(), any(CheckoutRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Mã giảm giá không hợp lệ hoặc đã hết hạn."));

        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON)
                        .content("{\"addressId\":8,\"paymentMethod\":\"COD\",\"discountCode\":\"NOPE\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value("Mã giảm giá không hợp lệ hoặc đã hết hạn."));
    }

    @Test
    void checkout_addressOfAnotherUser_is403() throws Exception {
        when(checkoutService.checkout(anyLong(), any(CheckoutRequest.class)))
                .thenThrow(new ForbiddenException("Bạn không có quyền truy cập địa chỉ này."));

        mvc.perform(asCustomer(post("/api/v1/checkout").contentType(JSON).content("{\"addressId\":77,\"paymentMethod\":\"COD\"}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ---- POST /checkout/preview ----

    private static CheckoutPreviewResponse preview() {
        return new CheckoutPreviewResponse(
                List.of(new CheckoutPreviewResponse.Line(10L, "Bình nước", new BigDecimal("100000.00"), 2,
                        new BigDecimal("200000.00"), 5)),
                new BigDecimal("200000.00"), new BigDecimal("20000.00"), new BigDecimal("30000.00"),
                new BigDecimal("210000.00"),
                List.of(new CheckoutPreviewResponse.Issue("INVALID_DISCOUNT", null, "Mã giảm giá không hợp lệ hoặc đã hết hạn.")));
    }

    @Test
    void preview_returnsTheServersNumbers_inTheEnvelope() throws Exception {
        when(checkoutService.preview(CALLER, "SALE10")).thenReturn(preview());

        mvc.perform(asCustomer(post("/api/v1/checkout/preview").contentType(JSON).content("{\"discountCode\":\"SALE10\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value(10))
                .andExpect(jsonPath("$.data.items[0].name").value("Bình nước"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(100000.00))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(200000.00))
                .andExpect(jsonPath("$.data.items[0].available").value(5))
                .andExpect(jsonPath("$.data.subtotal").value(200000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(20000.00))
                .andExpect(jsonPath("$.data.shippingFee").value(30000.00))
                .andExpect(jsonPath("$.data.total").value(210000.00))
                .andExpect(jsonPath("$.data.issues[0].code").value("INVALID_DISCOUNT"));
    }

    @Test
    void preview_hasNoSideEffects_atTheEndpoint_itOnlyReads() throws Exception {
        when(checkoutService.preview(anyLong(), any())).thenReturn(preview());

        mvc.perform(asCustomer(post("/api/v1/checkout/preview").contentType(JSON).content("{}"))).andExpect(status().isOk());
        mvc.perform(asCustomer(post("/api/v1/checkout/preview").contentType(JSON).content(""))).andExpect(status().isOk());

        // the code is optional (null) and the user is the token's; the placing method is never touched
        verify(checkoutService, org.mockito.Mockito.times(2)).preview(CALLER, null);
        verify(checkoutService, never()).checkout(anyLong(), any());
        verifyNoInteractions(orderService);
    }

    @Test
    void preview_overlongDiscountCode_is400() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/checkout/preview").contentType(JSON)
                        .content("{\"discountCode\":\"" + "X".repeat(31) + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.discountCode").exists());
    }
}
