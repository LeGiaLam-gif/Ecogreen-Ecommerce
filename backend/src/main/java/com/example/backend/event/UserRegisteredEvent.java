package com.example.backend.event;

/** Published inside the transaction that creates a customer account. */
public record UserRegisteredEvent(Long userId) {
}
