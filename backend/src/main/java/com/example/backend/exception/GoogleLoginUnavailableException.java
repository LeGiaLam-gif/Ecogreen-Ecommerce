package com.example.backend.exception;

/** Google login cannot be performed safely (not configured / Google keys unreachable). Maps to HTTP 503. */
public class GoogleLoginUnavailableException extends RuntimeException {
    public GoogleLoginUnavailableException(String message) { super(message); }
}
