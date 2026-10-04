-- B01-P3: permission tables (permissions, role_permissions).
--
-- LEGACY DATA IMPACT:        New tables only; no existing row is read or changed. Until V6 is applied the tables are empty,
--                            so every token carries permissions = [] (same as B01-P2) and requireAdmin() keeps working.
-- NULLABILITY:               permissions.code is NOT NULL UNIQUE (new table, nothing to backfill); description is nullable.
--                            role_permissions has a composite PK (role_id, permission_id), both NOT NULL.
-- CONSTRAINT ORDER:          permissions first, then role_permissions (it references permissions and roles), then the index.
-- DATA-LOSS / ROLLBACK RISK: None on apply. Rollback (loses only permission data; run BEFORE rolling back V5/V6 data):
--                              DROP TABLE IF EXISTS role_permissions;
--                              DROP TABLE IF EXISTS permissions;
-- EMPTY DATABASE:            Yes - requires "roles" (database/init-postgres.sql or ddl-auto) to exist first.
-- EXISTING DATABASE:         Yes - idempotent (IF NOT EXISTS); safe if ddl-auto=update already created the tables from the
--                            Permission entity / Role.permissions mapping (same names and types).
-- Manual application order: V4 -> V5 -> V6 (Flyway is not active).

CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id)       ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- The PK (role_id, permission_id) serves role -> permissions lookups (the application's join). This index serves the reverse
-- direction (which roles hold a permission) and the ON DELETE CASCADE from permissions.
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions (permission_id);
