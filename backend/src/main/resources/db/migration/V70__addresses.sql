-- B08: customer address book (one row per saved address, at most 10 per user, exactly one default per non-empty book).
--
-- LEGACY DATA IMPACT:        None. New table; no existing table or row is changed. Old order addresses (orders.shipping_address,
--                            free text) are deliberately NOT parsed into the book: customers add their addresses themselves.
-- NULLABILITY:               New table, so there is nothing to backfill before NOT NULL. user_id, recipient_name, phone, line1,
--                            ward, district, province, is_default, created_at, updated_at are all NOT NULL (the API requires
--                            every field). is_default defaults to false.
-- CONSTRAINT ORDER:          (1) create the table, (2) make the defaults identical for a table that ddl-auto=update created
--                            first, (3) add the foreign key to users (guarded), (4) create the indexes. The partial unique index
--                            is created last, when the table is fully constrained. Nothing is inserted, so there is no data to
--                            violate the unique index.
-- DATA-LOSS / ROLLBACK RISK: Dropping the table loses every address the customers entered (nothing else references it yet; B04
--                            will copy an address into the order snapshot, not reference it).
--                            Manual rollback:  DROP TABLE IF EXISTS addresses;
--                            Deleting a user deletes that user's addresses (ON DELETE CASCADE).
-- EMPTY DATABASE:            Fine - creates the empty table and its indexes (requires the users table).
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS everywhere and a guarded foreign key, so re-running changes
--                            nothing. If the application already ran with ddl-auto=update, Hibernate created the table
--                            WITHOUT the foreign key, the default and both indexes (the entity maps user_id as a plain
--                            column); this script adds what is missing. Existing rows (none in practice) are not touched, but
--                            creating the unique index fails if a user already has two default rows - see the check below.
--
-- NOT EXECUTED: this file is applied by hand with psql (Flyway is not active). Apply it AFTER V2-V6 and before relying on the
-- one-default guarantee; it is not required for B08 to start, but without it the database does not enforce the guarantee.
--
-- INDEXES (each is backed by a real query):
--   idx_addresses_user_id     (user_id)                       <- address list WHERE user_id = ?, countByUserId, deleteAllByUser
--   uq_addresses_one_default  UNIQUE (user_id) WHERE is_default <- at most ONE default per user (backstop of AddressService)
--
-- VERIFICATION (read-only):
--   -- expected columns (expect 11 rows)
--   SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'addresses' ORDER BY ordinal_position;
--   -- expected constraints (expect 2 rows: addresses_pkey (p) and fk_addresses_user (f))
--   SELECT conname, contype FROM pg_constraint WHERE conrelid = 'addresses'::regclass ORDER BY conname;
--   -- expected indexes (expect 3 rows: addresses_pkey, idx_addresses_user_id, uq_addresses_one_default)
--   SELECT indexname FROM pg_indexes WHERE tablename = 'addresses' ORDER BY indexname;
--   -- the unique index is partial (expect indisunique = true and "WHERE is_default" in the definition)
--   SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_addresses_one_default';
--   -- BEFORE applying on a database that already has rows: users with more than one default (expect 0 rows)
--   SELECT user_id, count(*) FROM addresses WHERE is_default GROUP BY user_id HAVING count(*) > 1;
--   -- users with addresses but no default (expect 0 rows)
--   SELECT user_id FROM addresses GROUP BY user_id HAVING count(*) FILTER (WHERE is_default) = 0;

CREATE TABLE IF NOT EXISTS addresses (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    line1          VARCHAR(255) NOT NULL,
    ward           VARCHAR(100) NOT NULL,
    district       VARCHAR(100) NOT NULL,
    province       VARCHAR(100) NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A table created earlier by ddl-auto=update has no defaults; make both paths identical.
ALTER TABLE addresses ALTER COLUMN is_default SET DEFAULT false;
ALTER TABLE addresses ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE addresses ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_addresses_user' AND conrelid = 'addresses'::regclass) THEN
        ALTER TABLE addresses ADD CONSTRAINT fk_addresses_user
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_addresses_user_id ON addresses (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_addresses_one_default ON addresses (user_id) WHERE is_default;
