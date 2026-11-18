-- B11: append-only audit history for administrator mutations and order status transitions.
-- Apply manually after V91; Flyway is not active in this project.
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id VARCHAR(100),
    http_method VARCHAR(10),
    request_path VARCHAR(500),
    response_status INTEGER,
    details_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created ON audit_logs (actor_user_id, created_at DESC);
