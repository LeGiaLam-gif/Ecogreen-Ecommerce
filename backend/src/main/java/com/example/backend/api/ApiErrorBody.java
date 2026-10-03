package com.example.backend.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Inner object of the error envelope. {@code fields} is present only for validation errors,
 * {@code traceId} only for INTERNAL_ERROR (correlates the response with the server log).
 * Never carries exception text, SQL, stack traces, class names, passwords or tokens.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(ApiErrorCode code, String message, Map<String, String> fields, String traceId) {
}
