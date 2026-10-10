-- B05: append-only order status history, backfilled with one row per existing order.
--
-- LEGACY DATA IMPACT:        No existing data is changed. One history row is ADDED for every existing order that has none:
--                            old_status NULL, new_status = the order's current (already V20-mapped) status, changed_by NULL,
--                            note 'Migrated from legacy', changed_at = orders.created_at. The original transition dates of
--                            legacy orders are unknown, so the backfill row only records "the order existed in this status".
-- NULLABILITY:               New table. new_status, order_id, changed_at are NOT NULL; old_status (no previous status for the
--                            first row), changed_by (system transitions and migrated rows) and note are nullable by design.
-- CONSTRAINT ORDER:          (1) create the table and its FKs (orders and users already exist), (2) create the index,
--                            (3) backfill last, so the table is fully constrained before data goes in.
-- DATA-LOSS / ROLLBACK RISK: DROP TABLE order_status_history loses the whole audit trail of status changes made after B05
--                            went live (the backfilled rows can be recreated by re-running this script before the drop).
--                            Manual rollback:  DROP TABLE IF EXISTS order_status_history;
--                            Deleting a user sets changed_by to NULL (ON DELETE SET NULL) and keeps the history row;
--                            deleting an order deletes its history (ON DELETE CASCADE, same as order_items and payments).
-- EMPTY DATABASE:            Fine - creates the empty table; the backfill inserts nothing.
-- EXISTING DATABASE:         Fine and idempotent - IF NOT EXISTS on table and index, and the backfill is guarded by NOT EXISTS,
--                            so re-running never duplicates rows and never touches orders that already have history.
-- Requires V20 (so that new_status holds a value of the 10-value enum). Apply order: V20 -> V21 -> V22.
-- Schema note: the Hibernate entity maps order_id as a relation and changed_by as a plain id column, so with ddl-auto=update
-- alone the FK on changed_by (and the ON DELETE rules) exist only if this script is applied.
--
-- VERIFICATION (read-only):
--   -- every order has at least one history row (expect 0 rows)
--   SELECT o.id FROM orders o WHERE NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = o.id);
--   -- backfill count equals order count on a database that had no history before (compare the two numbers)
--   SELECT (SELECT count(*) FROM orders) AS orders, (SELECT count(*) FROM order_status_history) AS history_rows;
--   -- last history status matches the order status (expect 0 rows on a freshly migrated database)
--   SELECT o.id, o.status FROM orders o
--    WHERE o.status <> (SELECT h.new_status FROM order_status_history h WHERE h.order_id = o.id
--                        ORDER BY h.changed_at DESC, h.id DESC LIMIT 1);

CREATE TABLE IF NOT EXISTS order_status_history (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT       NOT NULL,
    old_status  VARCHAR(20),
    new_status  VARCHAR(20)  NOT NULL,
    changed_by  BIGINT,
    note        VARCHAR(500),
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_user  FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Backs GET /api/v1/orders/{id}/history (WHERE order_id = ? ORDER BY changed_at).
CREATE INDEX IF NOT EXISTS idx_order_status_history_order_changed ON order_status_history (order_id, changed_at);

INSERT INTO order_status_history (order_id, old_status, new_status, changed_by, note, changed_at)
SELECT o.id, NULL, o.status, NULL, 'Migrated from legacy', o.created_at
  FROM orders o
 WHERE NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = o.id);
