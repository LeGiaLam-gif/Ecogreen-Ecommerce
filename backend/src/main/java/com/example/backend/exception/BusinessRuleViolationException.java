package com.example.backend.exception;

/**
 * A request is well-formed but violates a business rule (e.g. cannot cancel a delivered order).
 * 400 {@code BUSINESS_RULE_VIOLATION} on /api/v1; plain 400 {@code {message}} on legacy paths.
 */
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) { super(message); }
}
