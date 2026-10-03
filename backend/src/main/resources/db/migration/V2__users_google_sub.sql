-- B01-P1: Google login security hotfix - store the verified Google account identifier (OIDC "sub").
--
-- LEGACY DATA IMPACT:        Existing users get google_sub = NULL. Nothing is changed for them. A legacy
--                            Google-created user is linked on their next successful Google login
--                            (matched by the verified email from the Google ID token).
-- NULLABILITY:               New column is NULLABLE; no NOT NULL constraint, so no backfill is needed.
-- CONSTRAINT ORDER:          column first, then the partial UNIQUE index (no data can violate it: all NULL).
-- DATA-LOSS / ROLLBACK RISK: No data is lost by applying. Rollback (loses only the Google links):
--                              DROP INDEX IF EXISTS uq_users_google_sub;
--                              ALTER TABLE users DROP COLUMN IF EXISTS google_sub;
-- EMPTY DATABASE:            Yes - requires the "users" table (database/init-postgres.sql or ddl-auto) to exist first.
-- EXISTING DATABASE:         Yes - idempotent (IF NOT EXISTS); safe if ddl-auto=update already created the column.

ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;
