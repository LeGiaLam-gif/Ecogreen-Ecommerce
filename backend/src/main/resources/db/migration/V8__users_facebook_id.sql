-- B01: Facebook login - store the Facebook user id that the server verified through the Graph API.
--
-- LEGACY DATA IMPACT:        Existing users get facebook_id = NULL. Nothing is changed for them.
-- NULLABILITY:               New column is NULLABLE; no NOT NULL constraint, so no backfill is needed.
-- CONSTRAINT ORDER:          column first, then the partial UNIQUE index (no data can violate it: all NULL).
-- DATA-LOSS / ROLLBACK RISK: No data is lost by applying. Rollback (loses only the Facebook links):
--                              DROP INDEX IF EXISTS uq_users_facebook_id;
--                              ALTER TABLE users DROP COLUMN IF EXISTS facebook_id;
-- EMPTY DATABASE:            Yes - requires the "users" table (database/init-postgres.sql or ddl-auto) to exist first.
-- EXISTING DATABASE:         Yes - idempotent (IF NOT EXISTS); safe if ddl-auto=update already created the column.

ALTER TABLE users ADD COLUMN IF NOT EXISTS facebook_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_facebook_id ON users (facebook_id) WHERE facebook_id IS NOT NULL;
