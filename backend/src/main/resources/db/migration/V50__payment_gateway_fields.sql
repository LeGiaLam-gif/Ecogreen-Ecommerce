-- B06: payment gateway fields. A payment now records which provider handled it, when it was paid and the (write-only) raw
-- gateway answer, and its status gains CANCELLED and REFUNDED.
--
-- LEGACY DATA IMPACT:        Existing payment rows keep every value. Only provider is back-filled: rows created by the legacy mock
--                            ("pay now") have transaction_id 'MOCK-<uuid>' and get provider = 'MOCK'. Rows without a transaction
--                            (COD, VIETQR, MOMO still pending) keep provider = NULL: they have no gateway.
--                            paid_at is NOT back-filled (the legacy table never recorded it); it stays NULL for old rows.
-- NULLABILITY:               provider, paid_at and raw_response are nullable on purpose (COD / manual payments have no gateway).
--                            No NOT NULL is added, so there is nothing to back-fill first.
-- CONSTRAINT ORDER:          (1) add the three columns, (2) back-fill provider for legacy MOCK rows, (3) drop EVERY CHECK on
--                            payments that mentions status - chk_payments_status (init-postgres.sql) and any Hibernate-generated
--                            payments_status_check, found in pg_constraint, never guessed, (4) add the 5-value CHECK named
--                            chk_payments_status, (5) create the unique partial index last, after the back-fill.
-- DATA-LOSS / ROLLBACK RISK: Low while no row uses CANCELLED or REFUNDED. After the new code has run those values may exist and
--                            cannot be mapped back. Manual rollback (check first: SELECT status, count(*) FROM payments GROUP BY status;):
--                              DROP INDEX IF EXISTS uq_payments_provider_txn;
--                              ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_status;
--                              ALTER TABLE payments ADD CONSTRAINT chk_payments_status CHECK (status IN ('PENDING','SUCCESS','FAILED'));
--                              ALTER TABLE payments DROP COLUMN IF EXISTS provider, DROP COLUMN IF EXISTS paid_at, DROP COLUMN IF EXISTS raw_response;
-- EMPTY DATABASE:            Fine - the payments table exists after init-postgres.sql; the back-fill matches no row.
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS / guarded statements; re-running changes nothing. If the application
--                            already ran with ddl-auto=update, Hibernate added the three columns (the entity declares them) but
--                            did NOT widen the CHECK: this script does.
--
-- NOT EXECUTED against any real database: this file is applied by hand with psql (Flyway is not active), after V20-V22.
--
-- VERIFICATION (read-only):
--   SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'payments' ORDER BY ordinal_position;
--   SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'payments'::regclass AND contype = 'c';
--   SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_payments_provider_txn';
--   -- BEFORE applying on a database with rows: duplicate (provider, transaction_id) pairs after the back-fill (expect 0 rows)
--   SELECT transaction_id, count(*) FROM payments WHERE transaction_id LIKE 'MOCK-%' GROUP BY transaction_id HAVING count(*) > 1;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider     VARCHAR(30);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS paid_at      TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS raw_response TEXT;

UPDATE payments
   SET provider = 'MOCK'
 WHERE provider IS NULL
   AND transaction_id LIKE 'MOCK-%';

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'payments'::regclass
           AND contype = 'c'
           AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', c.conname);
    END LOOP;
END
$$;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED'));

-- One gateway transaction maps to exactly one payment. Rows without a transaction (COD, manual) are not constrained.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_provider_txn
    ON payments (provider, transaction_id)
    WHERE transaction_id IS NOT NULL;
