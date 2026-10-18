package com.example.backend.dto;

/** Where to send the browser to pay, and which provider handles the attempt. */
public record InitiatePaymentResponse(String redirectUrl, String provider) {
}
