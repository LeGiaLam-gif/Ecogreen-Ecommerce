package com.example.backend.entity;

/** Delivery channel. Only EMAIL has a sender today; SMS and PUSH are valid in the CHECK constraint but nothing sends them. */
public enum NotificationChannel {
    EMAIL, SMS, PUSH
}
