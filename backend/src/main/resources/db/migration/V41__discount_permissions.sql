-- B04: permission codes for the discount admin API, and their role mapping (same rules as V6, owner decision D-6).
--
-- LEGACY DATA IMPACT:        Inserts two catalogue rows and role_permissions rows only; no existing row is changed or deleted.
--                            After this script ADMIN holds 29 permissions and MANAGER 26 (all except system:configure,
--                            audit:view, user:disable). CUSTOMER still holds none. Users receive the new permissions in their
--                            NEXT access token (existing tokens keep their old claims for up to 15 minutes).
-- NULLABILITY:               permissions.description is filled for both rows; no NOT NULL column is added.
-- CONSTRAINT ORDER:          permissions -> role_permissions (the foreign keys are satisfied in that order). Needs V4-V6 first.
-- DATA-LOSS / ROLLBACK RISK: None on apply. Re-running only ADDS missing rows. Manual rollback:
--                              DELETE FROM role_permissions WHERE permission_id IN
--                                  (SELECT id FROM permissions WHERE code IN ('discount:view', 'discount:manage'));
--                              DELETE FROM permissions WHERE code IN ('discount:view', 'discount:manage');
-- EMPTY DATABASE:            Yes - apply after V4, V5 and V6, in that order.
-- EXISTING DATABASE:         Yes - idempotent (ON CONFLICT DO NOTHING everywhere).
-- Manual application order: V4 -> V5 -> V6 -> V40 -> V41 (Flyway is not active). V41 is independent of V40 (a permission row
-- does not reference the discounts table), but the admin API it unlocks needs both.
-- NOT EXECUTED against any real database.
-- The Java source of truth is security/Permissions.java; PermissionsCatalogueTest fails if V6 + this file drift from it.

INSERT INTO permissions (code, description) VALUES
    ('discount:view', 'View discount codes'),
    ('discount:manage', 'Create, update and delete discount codes')
ON CONFLICT (code) DO NOTHING;

-- ADMIN: everything, so also the two new codes.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.code IN ('discount:view', 'discount:manage')
ON CONFLICT DO NOTHING;

-- MANAGER: everything except system:configure, audit:view, user:disable - so also the two new codes.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.code IN ('discount:view', 'discount:manage')
ON CONFLICT DO NOTHING;

-- CUSTOMER: no role_permissions rows (customer rights are ownership-based, not permission-based).
