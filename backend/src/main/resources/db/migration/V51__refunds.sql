-- B06: refunds. One row records the money that must go back to a customer for a payment (full amount). B06 owns this table;
-- B07 (Return & Refund) reuses it and must not create it again.
--
-- LEGACY DATA IMPACT:        None. New table; no existing table or row is changed. Orders cancelled BEFORE this module existed
--                            while their payment was SUCCESS have no refund row (they only carry the history note REFUND_REQUIRED).
--                            This script does not back-fill them; list them with:
--                              SELECT o.id FROM orders o JOIN payments p ON p.order_id = o.id
--                               WHERE o.status = 'CANCELLED' AND p.status = 'SUCCESS'
--                                 AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.order_id = o.id);
-- NULLABILITY:               New table. payment_id, order_id, amount, status and created_at are NOT NULL; reason,
--                            provider_transaction_id, created_by (system refunds have no user) and refunded_at are nullable.
-- CONSTRAINT ORDER:          (1) create the table (FKs to payments, orders, users inline), (2) add the status CHECK guarded by a
--                            pg_constraint lookup, (3) create the indexes last. The table is empty, so no row can violate them.
-- DATA-LOSS / ROLLBACK RISK: Dropping the table loses every refund record (the money trail). Manual rollback:
--                              DROP TABLE IF EXISTS refunds;
-- EMPTY DATABASE:            Fine - needs payments, orders and users (created by init-postgres.sql); creates the empty table.
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS and a guarded CHECK. If ddl-auto=update created the table first,
--                            Hibernate did not add the CHECK or the partial unique index: this script adds them.
--
-- NOT EXECUTED against any real database: this file is applied by hand with psql (Flyway is not active), after V50.
--
-- INDEXES (each is backed by a real query):
--   uq_refunds_order_active  UNIQUE (order_id) WHERE status <> 'FAILED'  <- an order cannot have two non-FAILED refunds
--                                                                           (the cancel listener and the staff endpoint)
--   idx_refunds_payment_id   (payment_id)                                  <- foreign key lookups
--
-- VERIFICATION (read-only):
--   SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'refunds' ORDER BY ordinal_position;
--   SELECT conname, contype FROM pg_constraint WHERE conrelid = 'refunds'::regclass ORDER BY conname;
--   SELECT indexdef FROM pg_indexes WHERE tablename = 'refunds';

CREATE TABLE IF NOT EXISTS refunds (
    id                      BIGSERIAL     PRIMARY KEY,
    payment_id              BIGINT        NOT NULL REFERENCES payments (id),
    order_id                BIGINT        NOT NULL REFERENCES orders (id),
    amount                  DECIMAL(12,2) NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reason                  VARCHAR(255),
    provider_transaction_id VARCHAR(100),
    created_by              BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    refunded_at             TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'refunds'::regclass AND conname = 'chk_refunds_status') THEN
        ALTER TABLE refunds
            ADD CONSTRAINT chk_refunds_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'));
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_refunds_order_active ON refunds (order_id) WHERE status <> 'FAILED';
CREATE INDEX IF NOT EXISTS idx_refunds_payment_id ON refunds (payment_id);
