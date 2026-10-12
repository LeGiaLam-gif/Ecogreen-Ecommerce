-- B03: append-only inventory ledger (one row per stock change) plus one opening-balance row per existing inventory row.
--
-- LEGACY DATA IMPACT:        No existing table is changed. One ledger row is ADDED for every inventory row that has no ledger row
--                            yet: change_type 'ADJUST', quantity = on_hand at conversion time, on_hand_after / reserved_after =
--                            the converted values of V30, reason 'Opening balance (V30)', actor NULL. That row is the starting
--                            point of the history ("why is stock 3?"). Legacy reserved units (unpaid orders created before B03)
--                            get NO RESERVE rows: their later COMMIT / RELEASE rows carry the normal references.
--                            LEDGER RECONCILIATION RULE: the ledger is reconciled through the *_after columns, NOT by summing
--                            quantities. The last row of a product (highest id) must show on_hand_after / reserved_after equal
--                            to that product's inventory row (query below). Summing deltas is wrong because of the legacy
--                            reserved units and because quantity means different things per change_type.
-- NULLABILITY:               New table. product_id, change_type, quantity, on_hand_after, reserved_after, created_at are NOT NULL
--                            (stricter than "INT": the application always writes both after-values and reconciliation relies on
--                            them). reference_type / reference_id (both set or both NULL), reason, actor_user_id are nullable by
--                            design: initial stock and admin adjustments have no order, system movements have no actor.
-- CONSTRAINT ORDER:          (1) create the table, (2) set defaults, (3) add CHECKs and foreign keys (the referenced tables
--                            products and users already exist), (4) create the indexes, (5) write the opening rows last, so the
--                            table is fully constrained before data goes in. Constraints and indexes are only created if absent.
-- DATA-LOSS / ROLLBACK RISK: Dropping the table loses the whole stock audit trail written after B03 went live (the opening rows
--                            can be recreated by re-running this script after the drop, but with the CURRENT inventory values).
--                            Manual rollback:  DROP TABLE IF EXISTS inventory_transactions;
--                            Deleting a user sets actor_user_id to NULL (ON DELETE SET NULL) and keeps the row. Deleting a
--                            product is RESTRICTED while it has ledger rows (the ledger is an audit trail; the application never
--                            hard-deletes products - only database/seed-data.sql does, and it clears the ledger first).
-- EMPTY DATABASE:            Fine - creates the empty table; the opening-row insert adds nothing.
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS / guarded constraints, and the opening rows are guarded by
--                            NOT EXISTS (a product that already has any ledger row is skipped), so re-running never duplicates.
-- Requires V30 (inventory rows) and the users table. Apply order: V30 -> V31.
--
-- APPLY RULE: apply V30 then V31, in this order, with the application STOPPED and BEFORE its first start with B03 code (see V30).
-- With ddl-auto=update alone the table would exist WITHOUT these CHECKs, foreign keys and the unique partial index.
--
-- INDEXES (each is backed by a real query):
--   idx_inventory_tx_product_created (product_id, created_at DESC, id DESC) <- GET /api/v1/admin/inventory/{id}/transactions
--                                                                              (WHERE product_id = ? ORDER BY created_at DESC, id DESC)
--   uq_inventory_tx_idempotency      UNIQUE (product_id, change_type, reference_type, reference_id)
--                                    WHERE change_type <> 'ADJUST' AND reference_type IS NOT NULL AND reference_id IS NOT NULL
--                                    <- a RESERVE / RELEASE / COMMIT / RESTOCK for the same product and reference can only be
--                                       applied once (retries and webhook replays are logged no-ops in the service).
--                                       ADJUST rows are excluded (an admin may adjust the same product many times); rows without a
--                                       reference (initial stock, seed) are excluded because they have nothing to repeat.
--
-- VERIFICATION (read-only):
--   -- expected constraints (expect exactly 7 rows: 4 checks, 2 foreign keys, 1 primary key):
--   SELECT conname, contype FROM pg_constraint WHERE conrelid = 'inventory_transactions'::regclass ORDER BY conname;
--   --   chk_inventory_tx_after_values (c), chk_inventory_tx_quantity (c), chk_inventory_tx_reference_pair (c),
--   --   chk_inventory_tx_type (c), fk_inventory_tx_actor (f), fk_inventory_tx_product (f), inventory_transactions_pkey (p)
--   -- expected indexes (expect 3 rows: the primary key and the two below)
--   SELECT indexname FROM pg_indexes WHERE tablename = 'inventory_transactions' ORDER BY indexname;
--   --   idx_inventory_tx_product_created, inventory_transactions_pkey, uq_inventory_tx_idempotency
--   -- the idempotency index is partial and unique (expect indisunique = true and the WHERE text in the definition)
--   SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_inventory_tx_idempotency';
--   -- every inventory row has at least one ledger row (expect 0 rows)
--   SELECT i.product_id FROM inventory i WHERE NOT EXISTS (SELECT 1 FROM inventory_transactions t WHERE t.product_id = i.product_id);
--   -- RECONCILIATION through the *_after columns: the last ledger row equals the inventory row (expect 0 rows)
--   SELECT i.product_id, i.quantity_on_hand, i.reserved_quantity, l.on_hand_after, l.reserved_after
--     FROM inventory i
--     LEFT JOIN LATERAL (SELECT t.on_hand_after, t.reserved_after FROM inventory_transactions t
--                         WHERE t.product_id = i.product_id ORDER BY t.id DESC LIMIT 1) l ON TRUE
--    WHERE l.on_hand_after  IS DISTINCT FROM i.quantity_on_hand
--       OR l.reserved_after IS DISTINCT FROM i.reserved_quantity;

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id             BIGSERIAL    PRIMARY KEY,
    product_id     BIGINT       NOT NULL,
    change_type    VARCHAR(10)  NOT NULL,
    quantity       INT          NOT NULL,
    on_hand_after  INT          NOT NULL,
    reserved_after INT          NOT NULL,
    reference_type VARCHAR(30),
    reference_id   BIGINT,
    reason         VARCHAR(255),
    actor_user_id  BIGINT,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A table created earlier by ddl-auto=update has no default; make both paths identical.
ALTER TABLE inventory_transactions ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_tx_type' AND conrelid = 'inventory_transactions'::regclass) THEN
        ALTER TABLE inventory_transactions ADD CONSTRAINT chk_inventory_tx_type
            CHECK (change_type IN ('RESERVE', 'RELEASE', 'COMMIT', 'RESTOCK', 'ADJUST'));
    END IF;
    -- quantity is the (positive) amount for RESERVE / RELEASE / COMMIT / RESTOCK and the signed delta for ADJUST
    -- (0 is allowed only for ADJUST, so an opening balance of 0 can be recorded).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_tx_quantity' AND conrelid = 'inventory_transactions'::regclass) THEN
        ALTER TABLE inventory_transactions ADD CONSTRAINT chk_inventory_tx_quantity
            CHECK (change_type = 'ADJUST' OR quantity > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_tx_after_values' AND conrelid = 'inventory_transactions'::regclass) THEN
        ALTER TABLE inventory_transactions ADD CONSTRAINT chk_inventory_tx_after_values
            CHECK (on_hand_after >= 0 AND reserved_after >= 0 AND reserved_after <= on_hand_after);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_tx_reference_pair' AND conrelid = 'inventory_transactions'::regclass) THEN
        ALTER TABLE inventory_transactions ADD CONSTRAINT chk_inventory_tx_reference_pair
            CHECK ((reference_type IS NULL) = (reference_id IS NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_tx_product' AND conrelid = 'inventory_transactions'::regclass) THEN
        ALTER TABLE inventory_transactions ADD CONSTRAINT fk_inventory_tx_product
            FOREIGN KEY (product_id) REFERENCES products(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_tx_actor' AND conrelid = 'inventory_transactions'::regclass) THEN
        ALTER TABLE inventory_transactions ADD CONSTRAINT fk_inventory_tx_actor
            FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inventory_tx_product_created
    ON inventory_transactions (product_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_tx_idempotency
    ON inventory_transactions (product_id, change_type, reference_type, reference_id)
    WHERE change_type <> 'ADJUST' AND reference_type IS NOT NULL AND reference_id IS NOT NULL;

-- Opening balance: the starting point of each product's history (see LEGACY DATA IMPACT). Written last, guarded per product.
INSERT INTO inventory_transactions (product_id, change_type, quantity, on_hand_after, reserved_after, reason)
SELECT i.product_id, 'ADJUST', i.quantity_on_hand, i.quantity_on_hand, i.reserved_quantity, 'Opening balance (V30)'
  FROM inventory i
 WHERE NOT EXISTS (SELECT 1 FROM inventory_transactions t WHERE t.product_id = i.product_id);
