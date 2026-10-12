-- B03: inventory table (on-hand / reserved model) and CONVERSION of the legacy products.stock_quantity data into it.
--
-- LEGACY DATA IMPACT:        THIS SCRIPT CONVERTS EXISTING DATA. One inventory row is created for every existing product:
--                              reserved_quantity = SUM(order_items.quantity) of the orders that are in PENDING_PAYMENT
--                              quantity_on_hand  = products.stock_quantity + that same sum
--                            Reason: the legacy model (and B05's adapter) already subtracted stock when an unpaid order was
--                            created, so on-hand must be rebuilt to include goods that are merely reserved; otherwise the later
--                            COMMIT of such an order would deduct them a second time. Orders in committed states (PAID ...
--                            DELIVERED, legacy-mapped PROCESSING) need no adjustment. products.stock_quantity itself is NOT
--                            changed: it stays as a frozen historical value that nothing reads or writes after B03.
--                            Available stock (= on_hand - reserved) is never stored; it is computed when read.
--                            HISTORICAL LEAK, NOT FIXED HERE: orders cancelled BEFORE B05's cutover never restored their stock.
--                            This script never restocks them (whether the stock was corrected by hand is unknowable). Use the
--                            read-only DIAGNOSTIC below and correct stock through the admin "adjust" operation if needed.
-- NULLABILITY:               All four columns are NOT NULL with defaults (0 / CURRENT_TIMESTAMP); the table is new, so there is
--                            nothing to backfill before NOT NULL. If ddl-auto=update already created the table from the entity it
--                            has no defaults - the ALTER ... SET DEFAULT statements below add them.
-- CONSTRAINT ORDER:          (1) create the table (primary key only), (2) set defaults, (3) CONVERT the data, (4) verify the data
--                            satisfies the invariants and abort if not, (5) add the CHECKs, (6) add the foreign key. Every
--                            constraint is added only if its name is not yet in pg_constraint, so re-running is safe.
-- DATA-LOSS / ROLLBACK RISK: Dropping the table loses the reservation counters (and, with V31 applied, the ledger loses its
--                            reference rows - drop V31's table first). The legacy value can be re-derived before dropping:
--                              UPDATE products p SET stock_quantity = i.quantity_on_hand - i.reserved_quantity
--                                FROM inventory i WHERE i.product_id = p.id;
--                              DROP TABLE IF EXISTS inventory;     -- after DROP TABLE IF EXISTS inventory_transactions;
--                            (Run the UPDATE only if nothing but B03 changed stock since V30; otherwise the frozen column is
--                            already out of date for products whose stock changed through B03.)
-- EMPTY DATABASE:            Fine - creates the empty table and its constraints; the conversion inserts nothing.
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS on the table, ON CONFLICT DO NOTHING on the conversion (a row that
--                            already exists is never overwritten), guarded constraints.
-- Requires V20 (status value PENDING_PAYMENT). Apply order: V20 -> V21 -> V22 -> V30 -> V31.
--
-- APPLY RULE (important): the entities Inventory and InventoryTransaction map product_id as a plain column, so with
-- ddl-auto=update alone Hibernate would create these tables WITHOUT the foreign keys, CHECKs and the unique partial index of V31.
-- Therefore apply V30 and then V31, in this order, with the application STOPPED and BEFORE its first start with B03 code.
-- Applying V30 while the application is running would convert stock that keeps changing underneath the conversion.
--
-- DIAGNOSTIC (read-only, NOT executed by this script) - items of orders cancelled before B05's cutover. A cancelled order that
-- went through B05 has a history row with a non-NULL old_status; one that was only backfilled by V21 does not:
--   SELECT o.id AS order_id, o.created_at, oi.product_id, p.name, oi.quantity
--     FROM orders o
--     JOIN order_items oi ON oi.order_id = o.id
--     JOIN products p     ON p.id = oi.product_id
--    WHERE o.status = 'CANCELLED'
--      AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = o.id AND h.old_status IS NOT NULL)
--    ORDER BY o.id, oi.product_id;
--
-- VERIFICATION (read-only):
--   -- one inventory row per product (expect 0 rows)
--   SELECT p.id FROM products p WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.product_id = p.id);
--   -- invariants (expect 0 rows)
--   SELECT product_id FROM inventory WHERE quantity_on_hand < 0 OR reserved_quantity < 0 OR reserved_quantity > quantity_on_hand;
--   -- conversion check: reserved equals the PENDING_PAYMENT demand, on_hand = frozen stock + reserved (expect 0 rows on a
--   -- database where only V30 has run; after B03 starts changing stock, differences are expected)
--   SELECT i.product_id FROM inventory i JOIN products p ON p.id = i.product_id
--    WHERE i.reserved_quantity <> COALESCE((SELECT SUM(oi.quantity) FROM order_items oi JOIN orders o ON o.id = oi.order_id
--                                            WHERE oi.product_id = i.product_id AND o.status = 'PENDING_PAYMENT'), 0)
--       OR i.quantity_on_hand <> p.stock_quantity + i.reserved_quantity;
--   -- expected constraints (expect exactly 5 rows: 3 checks, 1 foreign key, 1 primary key)
--   SELECT conname, contype FROM pg_constraint WHERE conrelid = 'inventory'::regclass ORDER BY conname;
--   --   chk_inventory_on_hand_nonneg (c), chk_inventory_reserved_le_on_hand (c), chk_inventory_reserved_nonneg (c),
--   --   fk_inventory_product (f), inventory_pkey (p)
--   -- expected defaults (expect 3 rows)
--   SELECT column_name, column_default FROM information_schema.columns
--    WHERE table_name = 'inventory' AND column_default IS NOT NULL ORDER BY column_name;

CREATE TABLE IF NOT EXISTS inventory (
    product_id        BIGINT    PRIMARY KEY,
    quantity_on_hand  INT       NOT NULL DEFAULT 0,
    reserved_quantity INT       NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A table created earlier by ddl-auto=update has no defaults; make both paths identical.
ALTER TABLE inventory ALTER COLUMN quantity_on_hand  SET DEFAULT 0;
ALTER TABLE inventory ALTER COLUMN reserved_quantity SET DEFAULT 0;
ALTER TABLE inventory ALTER COLUMN updated_at        SET DEFAULT CURRENT_TIMESTAMP;

-- Legacy conversion (see LEGACY DATA IMPACT). A product that already has an inventory row is left exactly as it is.
INSERT INTO inventory (product_id, quantity_on_hand, reserved_quantity)
SELECT p.id,
       p.stock_quantity + COALESCE(r.reserved, 0),
       COALESCE(r.reserved, 0)
  FROM products p
  LEFT JOIN (SELECT oi.product_id, CAST(SUM(oi.quantity) AS INT) AS reserved
               FROM order_items oi
               JOIN orders o ON o.id = oi.order_id
              WHERE o.status = 'PENDING_PAYMENT'
              GROUP BY oi.product_id) r ON r.product_id = p.id
ON CONFLICT (product_id) DO NOTHING;

-- Verify BEFORE adding the CHECKs: if any row violates an invariant, stop here with a readable message instead of a bare
-- constraint error. (Cannot happen for converted rows because stock_quantity >= 0, but rows may come from elsewhere.)
DO $$
DECLARE
    bad INT;
BEGIN
    SELECT count(*) INTO bad FROM inventory
     WHERE quantity_on_hand < 0 OR reserved_quantity < 0 OR reserved_quantity > quantity_on_hand;
    IF bad > 0 THEN
        RAISE EXCEPTION 'V30 aborted: % inventory row(s) violate reserved <= on_hand (or a negative value). Inspect: SELECT * FROM inventory WHERE quantity_on_hand < 0 OR reserved_quantity < 0 OR reserved_quantity > quantity_on_hand;', bad;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_on_hand_nonneg' AND conrelid = 'inventory'::regclass) THEN
        ALTER TABLE inventory ADD CONSTRAINT chk_inventory_on_hand_nonneg CHECK (quantity_on_hand >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_reserved_nonneg' AND conrelid = 'inventory'::regclass) THEN
        ALTER TABLE inventory ADD CONSTRAINT chk_inventory_reserved_nonneg CHECK (reserved_quantity >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_reserved_le_on_hand' AND conrelid = 'inventory'::regclass) THEN
        ALTER TABLE inventory ADD CONSTRAINT chk_inventory_reserved_le_on_hand CHECK (reserved_quantity <= quantity_on_hand);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_product' AND conrelid = 'inventory'::regclass) THEN
        ALTER TABLE inventory ADD CONSTRAINT fk_inventory_product
            FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
    END IF;
END $$;
