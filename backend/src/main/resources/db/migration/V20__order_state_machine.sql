-- B05: order state machine - widen orders.status from 4 to 10 values and map the legacy values.
--
-- LEGACY DATA IMPACT:        STORED DATA IS CHANGED. Legacy rows are re-labelled in place (row count never changes):
--                              PENDING   -> PENDING_PAYMENT
--                              CONFIRMED -> PROCESSING   (the legacy UI describes CONFIRMED as "preparing goods")
--                              PAID      -> PAID         (no guess about delivery - decision D-4: legacy PAID orders stay PAID
--                                                         and staff must advance them)
--                              CANCELLED -> CANCELLED    (unchanged)
--                            REVENUE SIDE EFFECT: before B05 only PAID counted as revenue. After B05 PROCESSING counts as
--                            revenue (OrderStatus.countsAsRevenue), so every legacy CONFIRMED order (COD, cash not yet
--                            collected) starts to appear in revenue reports. Count them first:
--                              SELECT count(*) FROM orders WHERE status = 'CONFIRMED';
--                            Optional, NOT run automatically (D-4): advance old legacy PAID orders to DELIVERED. Run it BEFORE
--                            V21 so the history backfill records DELIVERED, and note that it bypasses OrderService:
--                              -- UPDATE orders SET status = 'DELIVERED' WHERE status = 'PAID' AND created_at < now() - interval '30 days';
-- NULLABILITY:               No columns added. orders.status stays NOT NULL; only its DEFAULT changes.
-- CONSTRAINT ORDER:          (1) drop EVERY CHECK on orders that mentions status - both chk_orders_status (init-postgres.sql) and
--                                any Hibernate-generated orders_status_check, looked up in pg_constraint, never guessed;
--                            (2) map the legacy rows;
--                            (3) add the 10-value CHECK (named chk_orders_status);
--                            (4) set the default to PENDING_PAYMENT.
--                            The old CHECK must go first, otherwise step (2) violates it; the new CHECK goes last so it
--                            validates the already-mapped rows.
-- DATA-LOSS / ROLLBACK RISK: HIGH once the new code has run. New-only statuses (PACKED, SHIPPED, DELIVERED, RETURN_REQUESTED,
--                            RETURNED, REFUNDED) have NO legacy equivalent, so rolling back after they exist loses information.
--                            Manual rollback, only valid while no new-only status exists (check first:
--                            SELECT status, count(*) FROM orders GROUP BY status;):
--                              ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_status;
--                              UPDATE orders SET status = 'PENDING'   WHERE status = 'PENDING_PAYMENT';
--                              UPDATE orders SET status = 'CONFIRMED' WHERE status = 'PROCESSING';
--                              ALTER TABLE orders ADD CONSTRAINT chk_orders_status
--                                CHECK (status IN ('PENDING','CONFIRMED','PAID','CANCELLED'));
--                              ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';
--                            Note the rollback cannot tell a legacy CONFIRMED order from a new PROCESSING one. Take a backup of
--                            the orders table before applying.
-- EMPTY DATABASE:            Fine - nothing to map; the constraint and default are replaced. Requires the orders table
--                            (database/init-postgres.sql) and fails fast with a clear message if it is missing.
-- EXISTING DATABASE:         Fine and idempotent - a second run finds no PENDING/CONFIRMED rows, drops and re-adds the same CHECK.
-- DEPLOYMENT:                STOP THE OLD APPLICATION VERSION BEFORE RUNNING THIS (the old code cannot read the new statuses)
--                            and start the new version afterwards (the new code cannot write PENDING_PAYMENT until this ran,
--                            because ddl-auto=update never changes an existing CHECK).
-- Manual application order: V20 -> V21 -> V22 (Flyway is not active). Apply in ONE transaction so a failure leaves the table
-- untouched:  psql -v ON_ERROR_STOP=1 -1 -f V20__order_state_machine.sql
--
-- VERIFICATION (read-only; run BEFORE and AFTER and compare):
--   -- 1. counts by status. After: PENDING_PAYMENT = old PENDING, PROCESSING = old CONFIRMED, PAID and CANCELLED unchanged.
--   SELECT status, count(*) FROM orders GROUP BY status ORDER BY status;
--   -- 2. total rows (must be identical before and after)
--   SELECT count(*) AS total_orders FROM orders;
--   -- 3. no legacy value left (expect 0)
--   SELECT count(*) AS legacy_status_rows FROM orders WHERE status IN ('PENDING', 'CONFIRMED');
--   -- 4. exactly one CHECK on status, named chk_orders_status, listing the 10 values
--   SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
--    WHERE conrelid = 'public.orders'::regclass AND contype = 'c';
--   -- 5. default (expect 'PENDING_PAYMENT')
--   SELECT column_default FROM information_schema.columns WHERE table_name = 'orders' AND column_name = 'status';

DO $$
DECLARE
    c RECORD;
BEGIN
    IF to_regclass('public.orders') IS NULL THEN
        RAISE EXCEPTION 'V20 requires the orders table: apply database/init-postgres.sql first.';
    END IF;

    FOR c IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'public.orders'::regclass
           AND contype = 'c'
           AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE public.orders DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

UPDATE orders SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';
UPDATE orders SET status = 'PROCESSING'      WHERE status = 'CONFIRMED';

ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (status IN (
    'PENDING_PAYMENT', 'PAID', 'PROCESSING', 'PACKED', 'SHIPPED', 'DELIVERED',
    'CANCELLED', 'RETURN_REQUESTED', 'RETURNED', 'REFUNDED'));

ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING_PAYMENT';
