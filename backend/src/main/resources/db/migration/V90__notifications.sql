-- B10: notifications. One row per notification sent (or attempted) to a user: an email today, SMS / PUSH reserved. B10 owns
-- this table. Written by NotificationService only; the customer and the admin READ it through /api/v1.
--
-- LEGACY DATA IMPACT:        None. New table; no existing table or row is changed. Events that happened BEFORE this module
--                            existed produced no notification (there is no back-fill).
-- NULLABILITY:               New table. user_id, type, channel, title, body, status and created_at are NOT NULL. sent_at,
--                            failure_reason and dedupe_key are nullable. Nothing needs a back-fill before a NOT NULL, because
--                            the table is empty.
-- CONSTRAINT ORDER:          (1) create the table, (2) add the foreign key, the three CHECKs and the unique constraint, each
--                            guarded by a pg_constraint lookup, (3) create the two indexes last. If ddl-auto=update created the
--                            table first it may already hold rows: every row the application writes satisfies the CHECKs.
-- DATA-LOSS / ROLLBACK RISK: Dropping the table loses the whole notification history and the de-duplication record, so after a
--                            rollback a repeated event could notify the customer again. Manual rollback:
--                              DROP TABLE IF EXISTS notifications;
-- EMPTY DATABASE:            Fine - needs users (created by init-postgres.sql); creates the empty table.
-- EXISTING DATABASE:         Fine and idempotent (IF NOT EXISTS, guarded constraints). If ddl-auto=update created the table from
--                            the Notification entity first, Hibernate did not add the foreign key or the CHECKs: this script
--                            adds them. The unique constraint and both indexes exist in both paths under the same names.
--
-- Migration NOT EXECUTED against any real database: this file is applied by hand with psql (Flyway is not active), after V80.
--
-- DEDUPE: (user_id, type, dedupe_key) is unique, so the same business event (e.g. dedupe_key 'order:123:PAID') can never be
-- notified twice. NULL dedupe_key rows (a direct send() without a key) are never considered duplicates of each other.
-- WHY NO CHECK ON THE TYPE LIST: the valid types live in one place, the NotificationType enum (Java). The database only
-- enforces the FORMAT (UPPER_SNAKE_CASE), so a new notification type needs a template and an event, not DDL.
-- failure_reason holds a SHORT CODE (USER_DISABLED, NO_EMAIL, SEND_ERROR, EMAIL_NOT_CONFIGURED), never an exception message.
--
-- INDEXES (each is backed by a real query):
--   idx_notifications_user_created   (user_id, created_at DESC)  <- GET /api/v1/users/me/notifications
--   idx_notifications_status_created (status, created_at)        <- GET /api/v1/admin/notifications?status=
--
-- VERIFICATION (read-only):
--   SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'notifications' ORDER BY ordinal_position;
--   SELECT conname, contype FROM pg_constraint WHERE conrelid = 'notifications'::regclass ORDER BY conname;
--   SELECT indexdef FROM pg_indexes WHERE tablename = 'notifications';

CREATE TABLE IF NOT EXISTS notifications (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    type           VARCHAR(50)  NOT NULL,
    channel        VARCHAR(20)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    body           TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(50),
    dedupe_key     VARCHAR(100),
    sent_at        TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'notifications'::regclass AND conname = 'fk_notifications_user') THEN
        ALTER TABLE notifications
            ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'notifications'::regclass AND conname = 'chk_notifications_channel') THEN
        ALTER TABLE notifications
            ADD CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'notifications'::regclass AND conname = 'chk_notifications_status') THEN
        ALTER TABLE notifications
            ADD CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'notifications'::regclass AND conname = 'chk_notifications_type_format') THEN
        ALTER TABLE notifications
            ADD CONSTRAINT chk_notifications_type_format CHECK (type ~ '^[A-Z][A-Z0-9_]*$');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'notifications'::regclass AND conname = 'uq_notifications_dedupe') THEN
        ALTER TABLE notifications
            ADD CONSTRAINT uq_notifications_dedupe UNIQUE (user_id, type, dedupe_key);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_status_created ON notifications (status, created_at);
