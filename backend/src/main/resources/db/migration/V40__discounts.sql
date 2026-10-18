-- B04: discount codes. A code gives either a percentage or a fixed amount off the items total of an order.
--
-- LEGACY DATA IMPACT:        None. New table; no existing table or row is changed. Orders already carry their own breakdown
--                            (subtotal, discount_amount, shipping_fee, discount_code - V22) and keep the totals that were stored
--                            when they were placed: this script never touches orders. orders.discount_code is plain text with
--                            NO foreign key, so deleting or editing a discount can never change or break an old order.
-- NULLABILITY:               New table, so there is nothing to backfill before NOT NULL. code, active and created_at are NOT NULL;
--                            exactly one of percent_off / amount_off is set (CHECK); min_order_amount, valid_from and valid_to
--                            are optional (NULL = no minimum / no start / no end).
-- CONSTRAINT ORDER:          (1) create the table, (2) make defaults identical for a table that ddl-auto=update created first,
--                            (3) add the CHECK constraints (each guarded by a pg_constraint lookup), (4) create the unique index
--                            last. The table is empty at this point, so no existing row can violate a constraint.
-- DATA-LOSS / ROLLBACK RISK: Dropping the table loses every discount code that was entered (historical orders keep the
--                            discount_code text and amounts). Manual rollback:
--                              DROP TABLE IF EXISTS discounts;
-- EMPTY DATABASE:            Fine - creates the empty table and its index (needs no other table).
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS everywhere and guarded constraints, so re-running changes
--                            nothing. If the application already ran with ddl-auto=update, Hibernate created the table WITHOUT
--                            the CHECK constraints and the unique index (the entity declares neither); this script adds them.
--                            Creating the unique index fails if two rows already share a code ignoring case - see the check below.
--
-- NOT EXECUTED against any real database: this file is applied by hand with psql (Flyway is not active), after V2-V6 and V22.
--
-- INDEXES (each is backed by a real query):
--   uq_discounts_code_upper  UNIQUE (upper(code))  <- the checkout lookup WHERE upper(code) = ? (codes are case-insensitive)
--                                                    and the duplicate check of the admin API. Codes are stored in upper case.
--
-- VERIFICATION (read-only):
--   -- columns (expect 9 rows)
--   SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'discounts' ORDER BY ordinal_position;
--   -- constraints (expect discounts_pkey (p) plus the 6 chk_discounts_* (c))
--   SELECT conname, contype FROM pg_constraint WHERE conrelid = 'discounts'::regclass ORDER BY conname;
--   -- the unique index is on upper(code) (expect indisunique = true)
--   SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_discounts_code_upper';
--   -- BEFORE applying on a database that already has rows: codes that collide ignoring case (expect 0 rows)
--   SELECT upper(code), count(*) FROM discounts GROUP BY upper(code) HAVING count(*) > 1;

CREATE TABLE IF NOT EXISTS discounts (
    id               BIGSERIAL     PRIMARY KEY,
    code             VARCHAR(30)   NOT NULL,
    percent_off      SMALLINT,
    amount_off       NUMERIC(12,2),
    min_order_amount NUMERIC(12,2),
    valid_from       TIMESTAMP,
    valid_to         TIMESTAMP,
    active           BOOLEAN       NOT NULL DEFAULT true,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A table created earlier by ddl-auto=update has no defaults; make both paths identical.
ALTER TABLE discounts ALTER COLUMN active SET DEFAULT true;
ALTER TABLE discounts ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_discounts_percent' AND conrelid = 'discounts'::regclass) THEN
        ALTER TABLE discounts ADD CONSTRAINT chk_discounts_percent
            CHECK (percent_off IS NULL OR (percent_off BETWEEN 1 AND 100));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_discounts_amount' AND conrelid = 'discounts'::regclass) THEN
        ALTER TABLE discounts ADD CONSTRAINT chk_discounts_amount
            CHECK (amount_off IS NULL OR amount_off > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_discounts_exactly_one' AND conrelid = 'discounts'::regclass) THEN
        ALTER TABLE discounts ADD CONSTRAINT chk_discounts_exactly_one
            CHECK ((percent_off IS NOT NULL AND amount_off IS NULL) OR (percent_off IS NULL AND amount_off IS NOT NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_discounts_min_order' AND conrelid = 'discounts'::regclass) THEN
        ALTER TABLE discounts ADD CONSTRAINT chk_discounts_min_order
            CHECK (min_order_amount IS NULL OR min_order_amount >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_discounts_window' AND conrelid = 'discounts'::regclass) THEN
        ALTER TABLE discounts ADD CONSTRAINT chk_discounts_window
            CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_discounts_code_upper' AND conrelid = 'discounts'::regclass) THEN
        ALTER TABLE discounts ADD CONSTRAINT chk_discounts_code_upper
            CHECK (code <> '' AND code = upper(code) AND code = btrim(code));
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_discounts_code_upper ON discounts (upper(code));
