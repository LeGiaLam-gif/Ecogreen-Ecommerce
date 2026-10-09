package com.example.backend.exception;

import com.example.backend.api.ApiError;
import com.example.backend.api.ApiErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** B02: an oversized multipart upload is a 400 on both generations of the API, never a 500. */
class MaxUploadSizeHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5L * 1024 * 1024);

    @Test
    void v1_returns400_validationErrorEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/products/1/images");

        ResponseEntity<Object> response = handler.handleMaxUploadSize(ex, request);

        assertEquals(400, response.getStatusCode().value());
        ApiError body = assertInstanceOf(ApiError.class, response.getBody());
        assertEquals(ApiErrorCode.VALIDATION_ERROR, body.error().code());
    }

    @Test
    void legacy_returns400_messageBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/upload");

        ResponseEntity<Object> response = handler.handleMaxUploadSize(ex, request);

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(true, body.containsKey("message"));
    }
}
