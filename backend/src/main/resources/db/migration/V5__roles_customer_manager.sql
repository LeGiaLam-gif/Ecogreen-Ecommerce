-- B01-P3: roles become CUSTOMER (renamed from USER), MANAGER, ADMIN.
--
-- LEGACY DATA IMPACT:        Case A (only USER exists): the row is RENAMED in place, so the role id and every user_roles link
--                            are preserved. Case B (USER and CUSTOMER both exist, e.g. the new DataLoader ran first): every
--                            user_roles link of USER is copied to CUSTOMER (duplicates skipped), USER's links are removed and
--                            the USER role is deleted - no user loses the role. Case C (neither exists): CUSTOMER is created.
--                            MANAGER is created if missing. ADMIN is untouched. After this script role name 'USER' is gone from
--                            the API (roles: ["CUSTOMER"]).
-- NULLABILITY:               No columns added, nothing to backfill.
-- CONSTRAINT ORDER:          rename/move links first, delete USER last (user_roles.role_id FK is ON DELETE CASCADE, so links must
--                            be moved BEFORE the delete); MANAGER insert after.
-- DATA-LOSS / ROLLBACK RISK: Case A rollback:  UPDATE roles SET name = 'USER' WHERE name = 'CUSTOMER';
--                            (only valid if no new CUSTOMER-only data must be kept; MANAGER can stay or be removed with
--                             DELETE FROM roles WHERE name = 'MANAGER'; this cascades to user_roles / role_permissions).
--                            Case B cannot be split back (users who had USER and CUSTOMER are now indistinguishable) -
--                            take a backup of roles/user_roles first if that matters.
-- EMPTY DATABASE:            Yes - init-postgres.sql seeds USER+ADMIN, which this renames (Case A).
-- EXISTING DATABASE:         Yes - idempotent: a second run finds no USER and an existing CUSTOMER and does nothing.
-- Manual application order: V4 -> V5 -> V6 (Flyway is not active). Requires roles and user_roles to exist.

DO $$
DECLARE
    v_user_id     BIGINT;
    v_customer_id BIGINT;
BEGIN
    SELECT id INTO v_user_id     FROM roles WHERE name = 'USER';
    SELECT id INTO v_customer_id FROM roles WHERE name = 'CUSTOMER';

    IF v_user_id IS NOT NULL AND v_customer_id IS NULL THEN
        UPDATE roles SET name = 'CUSTOMER' WHERE id = v_user_id;
    ELSIF v_user_id IS NOT NULL AND v_customer_id IS NOT NULL THEN
        INSERT INTO user_roles (user_id, role_id)
        SELECT user_id, v_customer_id FROM user_roles WHERE role_id = v_user_id
        ON CONFLICT DO NOTHING;
        DELETE FROM user_roles WHERE role_id = v_user_id;
        DELETE FROM roles WHERE id = v_user_id;
    ELSIF v_user_id IS NULL AND v_customer_id IS NULL THEN
        INSERT INTO roles (name) VALUES ('CUSTOMER');
    END IF;
END $$;

INSERT INTO roles (name) VALUES ('MANAGER') ON CONFLICT (name) DO NOTHING;
