-- B01: one-time password reset links (only the SHA-256 hash of the token is stored, never the token itself).
--
-- LEGACY DATA IMPACT:        New table, no existing rows. Nothing about existing users or sessions changes.
-- NULLABILITY / CONSTRAINTS: user_id, token_hash, expires_at and created_at are NOT NULL; used_at stays NULL until the link is
--                            consumed or voided. token_hash is UNIQUE (hex SHA-256, 64 chars); user_id references users(id)
--                            ON DELETE CASCADE.
-- CONSTRAINT ORDER:          table first (with its constraints), then the indexes.
-- DATA-LOSS / ROLLBACK RISK: Rollback = DROP TABLE password_reset_tokens (open reset links stop working; no business data lost).
-- EMPTY DATABASE:            Yes - requires "users" to exist first.
-- EXISTING DATABASE:         Yes - idempotent (IF NOT EXISTS); safe if ddl-auto=update already created the table.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
