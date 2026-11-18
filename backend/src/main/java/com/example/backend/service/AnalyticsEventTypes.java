package com.example.backend.service;

import java.util.List;

/**
 * The whitelist of {@code analytics_events.event_type} values (B09): lowercase snake_case, one constant per event that
 * something in the system really publishes today. {@code AnalyticsEventService.track} ignores any other value and the
 * events endpoint rejects it with 400, so a typo can never create a new "type". Adding a type needs no DDL (V80 only
 * checks the format), but it does need a publisher; see the contract for the events nobody publishes yet.
 */
public final class AnalyticsEventTypes {

    public static final String ORDER_CREATED = "order_created";
    public static final String PAYMENT_SUCCESS = "payment_success";
    public static final String ORDER_CANCELLED = "order_cancelled";
    public static final String RETURN_CREATED = "return_created";
    public static final String RETURN_STATUS_CHANGED = "return_status_changed";
    public static final String PRODUCT_VIEW = "product_view";
    public static final String PRODUCT_SEARCH = "product_search";
    public static final String ADD_TO_CART = "add_to_cart";
    public static final String REMOVE_FROM_CART = "remove_from_cart";
    public static final String CHECKOUT_STARTED = "checkout_started";

    public static final List<String> ALL = List.of(
            ORDER_CREATED, PAYMENT_SUCCESS, ORDER_CANCELLED, RETURN_CREATED, RETURN_STATUS_CHANGED,
            PRODUCT_VIEW, PRODUCT_SEARCH, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT_STARTED);

    public static final List<String> CLIENT_TRACKABLE = List.of(
            PRODUCT_VIEW, PRODUCT_SEARCH, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT_STARTED);

    private AnalyticsEventTypes() {
    }

    public static boolean isKnown(String type) {
        return type != null && ALL.contains(type);
    }
}
