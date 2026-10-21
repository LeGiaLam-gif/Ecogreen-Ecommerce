-- B09: analytics_events. One row per business event worth counting later (order created, payment succeeded, order cancelled,
-- return created / status changed). B09 owns this table and only ever INSERTS into it; it never writes another module's table.
--
-- LEGACY DATA IMPACT:        None. New table; no existing table or row is changed. Orders and returns that happened BEFORE
--                            this module existed have no analytics row (events are recorded as they happen, not back-filled).
-- NULLABILITY:               New table. id, event_type and created_at are NOT NULL. user_id is nullable (guest or system
--                            events, and ON DELETE SET NULL when a user is deleted); session_id and payload are nullable.
--                            Nothing needs a back-fill before a NOT NULL, because the table is empty.
-- CONSTRAINT ORDER:          (1) create the table, (2) add the foreign key to users, the event_type format CHECK and the
--                            payload CHECK, each guarded by a pg_constraint lookup, (3) create the index last. If
--                            ddl-auto=update created the table first, the table may already hold rows: all rows written by
--                            the application satisfy the CHECKs (lowercase snake_case type, flat JSON object payload).
-- DATA-LOSS / ROLLBACK RISK: Dropping the table loses every recorded analytics event (history cannot be rebuilt). Manual
--                            rollback:
--                              DROP TABLE IF EXISTS analytics_events;
-- EMPTY DATABASE:            Fine - needs users (created by init-postgres.sql); creates the empty table.
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS and guarded constraints. If ddl-auto=update created the
--                            table from the AnalyticsEvent entity first, Hibernate did not add the foreign key or the two
--                            CHECKs: this script adds them. The payload column is jsonb in both paths.
--
-- NOT EXECUTED against any real database: this file is applied by hand with psql (Flyway is not active), after V6.
--
-- WHY NO CHECK ON THE EVENT TYPE LIST: the list of valid types lives in one place, AnalyticsEventTypes (Java), so a new event
-- needs no DDL. The database only enforces the FORMAT (lowercase snake_case).
--
-- INDEXES (each is backed by a real query):
--   idx_analytics_events_type_created  (event_type, created_at)  <- GET /api/v1/admin/analytics/events?type=&from=&to=
--
-- VERIFICATION (read-only):
--   SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'analytics_events' ORDER BY ordinal_position;
--   SELECT conname, contype FROM pg_constraint WHERE conrelid = 'analytics_events'::regclass ORDER BY conname;
--   SELECT indexdef FROM pg_indexes WHERE tablename = 'analytics_events';

CREATE TABLE IF NOT EXISTS analytics_events (
    id         BIGSERIAL   PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id    BIGINT,
    session_id VARCHAR(100),
    payload    JSONB,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'analytics_events'::regclass AND conname = 'fk_analytics_events_user') THEN
        ALTER TABLE analytics_events
            ADD CONSTRAINT fk_analytics_events_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'analytics_events'::regclass AND conname = 'chk_analytics_events_type_format') THEN
        ALTER TABLE analytics_events
            ADD CONSTRAINT chk_analytics_events_type_format CHECK (event_type ~ '^[a-z][a-z0-9_]*$');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'analytics_events'::regclass AND conname = 'chk_analytics_events_payload_object') THEN
        ALTER TABLE analytics_events
            ADD CONSTRAINT chk_analytics_events_payload_object CHECK (payload IS NULL OR jsonb_typeof(payload) = 'object');
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_analytics_events_type_created ON analytics_events (event_type, created_at);
