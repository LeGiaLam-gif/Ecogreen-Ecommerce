-- B01-P3: seed the permission catalogue and the role -> permission mapping.
--
-- LEGACY DATA IMPACT:        Inserts catalogue rows and role_permissions rows only; no existing row is changed or deleted.
--                            After this script ADMIN holds all 27 permissions, MANAGER 24 (all except system:configure,
--                            audit:view, user:disable - owner decision D-6), CUSTOMER none. Users receive them in their NEXT
--                            access token (existing tokens keep their old claims for up to 15 minutes).
-- NULLABILITY:               permissions.description is filled for every row; no NOT NULL column is added.
-- CONSTRAINT ORDER:          roles (ensure ADMIN/MANAGER exist) -> permissions -> role_permissions (FKs satisfied in order).
-- DATA-LOSS / ROLLBACK RISK: None on apply. Re-running only ADDS missing rows; it never removes a mapping an operator added or
--                            deleted by hand beyond re-inserting the documented defaults. Rollback:
--                              DELETE FROM role_permissions;  DELETE FROM permissions;
-- EMPTY DATABASE:            Yes - needs V4 (tables) and works with or without V5 (it creates ADMIN/MANAGER if absent).
-- EXISTING DATABASE:         Yes - idempotent (ON CONFLICT DO NOTHING everywhere).
-- Manual application order: V4 -> V5 -> V6 (Flyway is not active).
-- The Java source of truth is security/Permissions.java; PermissionsCatalogueTest fails if this file drifts from it.

INSERT INTO roles (name) VALUES ('ADMIN'), ('MANAGER')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (code, description) VALUES
    ('product:view', 'View products'),
    ('product:create', 'Create products'),
    ('product:update', 'Update products'),
    ('product:delete', 'Delete products'),
    ('product:publish', 'Publish / unpublish products'),
    ('category:view', 'View categories'),
    ('category:create', 'Create categories'),
    ('category:update', 'Update categories'),
    ('category:delete', 'Delete categories'),
    ('inventory:view', 'View inventory'),
    ('inventory:update', 'Update inventory'),
    ('inventory:adjust', 'Adjust stock levels'),
    ('order:view', 'View own orders'),
    ('order:view_all', 'View all orders'),
    ('order:update', 'Update orders'),
    ('order:cancel', 'Cancel orders'),
    ('payment:view', 'View payments'),
    ('payment:confirm', 'Confirm payments'),
    ('payment:refund', 'Refund payments'),
    ('return:view', 'View return requests'),
    ('return:process', 'Process return requests'),
    ('user:view', 'View users'),
    ('user:update', 'Update users'),
    ('user:disable', 'Disable users'),
    ('analytics:view', 'View analytics and reports'),
    ('audit:view', 'View audit log'),
    ('system:configure', 'Configure the system')
ON CONFLICT (code) DO NOTHING;

-- ADMIN: everything.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- MANAGER: everything except system:configure, audit:view, user:disable.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.code NOT IN ('system:configure', 'audit:view', 'user:disable')
ON CONFLICT DO NOTHING;

-- CUSTOMER: no role_permissions rows (customer rights are ownership-based, not permission-based).
