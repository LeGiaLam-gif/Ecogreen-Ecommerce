package com.example.backend.api;

import java.util.Map;

/** Error envelope: {@code {"error": {"code": ..., "message": ..., "fields": {...}}}}. */
public record ApiError(ApiErrorBody error) {

    public static ApiError of(ApiErrorCode code, String message) {
        return new ApiError(new ApiErrorBody(code, message, null, null));
    }

    public static ApiError of(ApiErrorCode code, String message, Map<String, String> fields) {
        return new ApiError(new ApiErrorBody(code, message, fields, null));
    }

    public static ApiError internal(String message, String traceId) {
        return new ApiError(new ApiErrorBody(ApiErrorCode.INTERNAL_ERROR, message, null, traceId));
    }
}
