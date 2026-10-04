-- B01-P2: persisted refresh tokens (only the SHA-256 hash of the opaque token is stored).
--
-- LEGACY DATA IMPACT:        New table, no existing rows. Existing sessions (random UUID tokens held in memory by the
--                            removed AuthTokenStore) become invalid: every user must log in once after deployment.
-- NULLABILITY / CONSTRAINTS: token_hash is UNIQUE (hex SHA-256, 64 chars); user_id references users(id) ON DELETE CASCADE.
-- DATA-LOSS / ROLLBACK RISK: Rollback = DROP TABLE refresh_tokens (logs everybody out; no business data lost).
-- EMPTY DATABASE:            Yes - requires "users" to exist first.
-- EXISTING DATABASE:         Yes - idempotent (IF NOT EXISTS); safe if ddl-auto=update already created the table.

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    family_id      VARCHAR(36) NOT NULL,
    expires_at     TIMESTAMP   NOT NULL,
    revoked        BOOLEAN     NOT NULL DEFAULT false,
    replaced_by_id BIGINT,
    created_at     TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens (family_id);
