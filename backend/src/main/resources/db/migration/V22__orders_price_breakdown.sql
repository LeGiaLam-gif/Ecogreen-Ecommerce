-- B05: price breakdown columns on orders (filled by B04 checkout) plus the two indexes the order lists need.
--
-- LEGACY DATA IMPACT:        Existing orders are backfilled: subtotal = total_price, discount_amount = 0, shipping_fee = 0,
--                            discount_code stays NULL. total_price itself is never changed, so every existing total and every
--                            revenue figure stays exactly as it was.
-- NULLABILITY:               The three money columns are added NULLABLE, backfilled, and only then set NOT NULL DEFAULT 0
--                            (adding NOT NULL first would fail on a table that already has rows). discount_code stays nullable
--                            and has NO foreign key, so deleting a discount can never break order history.
-- CONSTRAINT ORDER:          add nullable columns -> backfill -> set DEFAULT -> set NOT NULL -> create indexes.
-- DATA-LOSS / ROLLBACK RISK: Dropping the columns loses discount and shipping data once B04 writes them (before B04 they only
--                            hold the backfilled values, which are recomputable). Manual rollback:
--                              DROP INDEX IF EXISTS idx_orders_status_created;
--                              DROP INDEX IF EXISTS idx_orders_user_created;
--                              ALTER TABLE orders DROP COLUMN IF EXISTS discount_code,
--                                                 DROP COLUMN IF EXISTS shipping_fee,
--                                                 DROP COLUMN IF EXISTS discount_amount,
--                                                 DROP COLUMN IF EXISTS subtotal;
-- EMPTY DATABASE:            Fine - adds the columns and indexes to the empty table.
-- EXISTING DATABASE:         Fine and idempotent - ADD COLUMN / CREATE INDEX use IF NOT EXISTS, and each backfill only touches
--                            rows that are still NULL. With ddl-auto=update the entity (@ColumnDefault("0")) may already have
--                            added the columns with a default of 0 - then the backfill below still sets subtotal for old rows
--                            whose subtotal is 0 and total_price is not.
-- Apply order: V20 -> V21 -> V22.
--
-- INDEXES (each is backed by a real query):
--   idx_orders_user_created   (user_id, created_at DESC)   <- GET /api/v1/orders/my : WHERE user_id = ? ORDER BY created_at DESC
--   idx_orders_status_created (status,  created_at DESC)   <- GET /api/v1/admin/orders?status=... ORDER BY created_at DESC, and
--                                                             the revenue/report queries (status IN (...) AND created_at >= ?)
-- The baseline idx_orders_user_id (user_id) is now redundant with idx_orders_user_created but is deliberately NOT dropped here
-- (never drop what is not part of the task; report it for B13 cleanup).
--
-- VERIFICATION (read-only):
--   -- no NULLs left (expect 0 for each)
--   SELECT count(*) FROM orders WHERE subtotal IS NULL OR discount_amount IS NULL OR shipping_fee IS NULL;
--   -- legacy orders: subtotal equals total_price (expect 0 rows on a freshly migrated database)
--   SELECT id FROM orders WHERE discount_amount = 0 AND shipping_fee = 0 AND subtotal <> total_price;
--   -- indexes exist (expect 2 rows)
--   SELECT indexname FROM pg_indexes WHERE tablename = 'orders'
--      AND indexname IN ('idx_orders_user_created', 'idx_orders_status_created');

ALTER TABLE orders ADD COLUMN IF NOT EXISTS subtotal        NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_fee    NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_code   VARCHAR(50);

-- Rows that are NULL (script added the columns) or still carry the entity default 0 (ddl-auto added them) get the real subtotal.
UPDATE orders SET subtotal = total_price WHERE subtotal IS NULL OR (subtotal = 0 AND total_price <> 0);
UPDATE orders SET discount_amount = 0 WHERE discount_amount IS NULL;
UPDATE orders SET shipping_fee    = 0 WHERE shipping_fee IS NULL;

ALTER TABLE orders ALTER COLUMN subtotal        SET DEFAULT 0;
ALTER TABLE orders ALTER COLUMN discount_amount SET DEFAULT 0;
ALTER TABLE orders ALTER COLUMN shipping_fee    SET DEFAULT 0;

ALTER TABLE orders ALTER COLUMN subtotal        SET NOT NULL;
ALTER TABLE orders ALTER COLUMN discount_amount SET NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_fee    SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_user_created   ON orders (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status_created ON orders (status, created_at DESC);
