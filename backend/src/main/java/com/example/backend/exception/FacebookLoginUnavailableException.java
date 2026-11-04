package com.example.backend.exception;

/** Facebook login cannot be performed safely (not configured / Graph API unreachable). Maps to HTTP 503. */
public class FacebookLoginUnavailableException extends RuntimeException {
    public FacebookLoginUnavailableException(String message) { super(message); }
}
