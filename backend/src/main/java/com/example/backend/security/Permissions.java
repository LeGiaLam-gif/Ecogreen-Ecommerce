package com.example.backend.security;

import com.example.backend.entity.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * B01-P3: the ONLY place in Java application code where permission codes are defined ("&lt;resource&gt;:&lt;action&gt;", lowercase).
 * Other modules reference these constants (e.g. {@code authGuard.requirePermission(request, Permissions.PRODUCT_CREATE)}).
 * The database copy of the catalogue lives in migration V6__seed_permissions.sql; PermissionsCatalogueTest detects drift.
 */
public final class Permissions {

    private Permissions() {}

    public static final String PRODUCT_VIEW = "product:view";
    public static final String PRODUCT_CREATE = "product:create";
    public static final String PRODUCT_UPDATE = "product:update";
    public static final String PRODUCT_DELETE = "product:delete";
    public static final String PRODUCT_PUBLISH = "product:publish";

    public static final String CATEGORY_VIEW = "category:view";
    public static final String CATEGORY_CREATE = "category:create";
    public static final String CATEGORY_UPDATE = "category:update";
    public static final String CATEGORY_DELETE = "category:delete";

    public static final String INVENTORY_VIEW = "inventory:view";
    public static final String INVENTORY_UPDATE = "inventory:update";
    public static final String INVENTORY_ADJUST = "inventory:adjust";

    public static final String ORDER_VIEW = "order:view";
    public static final String ORDER_VIEW_ALL = "order:view_all";
    public static final String ORDER_UPDATE = "order:update";
    public static final String ORDER_CANCEL = "order:cancel";

    public static final String PAYMENT_VIEW = "payment:view";
    public static final String PAYMENT_CONFIRM = "payment:confirm";
    public static final String PAYMENT_REFUND = "payment:refund";

    public static final String RETURN_VIEW = "return:view";
    public static final String RETURN_PROCESS = "return:process";

    public static final String USER_VIEW = "user:view";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_DISABLE = "user:disable";

    public static final String ANALYTICS_VIEW = "analytics:view";
    public static final String AUDIT_VIEW = "audit:view";
    public static final String SYSTEM_CONFIGURE = "system:configure";

    /** Every permission code, in catalogue order. */
    public static final List<String> ALL = List.of(
            PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_DELETE, PRODUCT_PUBLISH,
            CATEGORY_VIEW, CATEGORY_CREATE, CATEGORY_UPDATE, CATEGORY_DELETE,
            INVENTORY_VIEW, INVENTORY_UPDATE, INVENTORY_ADJUST,
            ORDER_VIEW, ORDER_VIEW_ALL, ORDER_UPDATE, ORDER_CANCEL,
            PAYMENT_VIEW, PAYMENT_CONFIRM, PAYMENT_REFUND,
            RETURN_VIEW, RETURN_PROCESS,
            USER_VIEW, USER_UPDATE, USER_DISABLE,
            ANALYTICS_VIEW, AUDIT_VIEW, SYSTEM_CONFIGURE);

    /** Owner decision D-6: a MANAGER has everything except these. */
    public static final Set<String> MANAGER_EXCLUDED = Set.of(SYSTEM_CONFIGURE, AUDIT_VIEW, USER_DISABLE);

    /**
     * The intended role -> permission mapping (documentation + drift test against V6). Runtime authorization never uses
     * this method: it uses the permissions loaded from the database into the token.
     * ADMIN = all; MANAGER = all except {@link #MANAGER_EXCLUDED}; CUSTOMER (and anything else) = none.
     */
    public static List<String> forRole(String roleName) {
        if (Role.ADMIN.equals(roleName)) return ALL;
        if (Role.MANAGER.equals(roleName)) {
            List<String> codes = new ArrayList<>(ALL);
            codes.removeAll(MANAGER_EXCLUDED);
            return List.copyOf(codes);
        }
        return List.of();
    }
}
