-- B02-Catalog: product_images table and a lossless copy of the legacy products.image value.
--
-- LEGACY DATA IMPACT:        New table. For every product whose legacy products.image is non-blank and that has no image row
--                            yet, one row (sort_order = 0) is copied. products.image is KEPT and not rewritten (bare filenames
--                            and URLs are still resolved by the frontend imageResolver). The column drop is a B13 task.
-- NULLABILITY:               image_url and sort_order are NOT NULL (new table, nothing to backfill). storage_key and alt_text
--                            are nullable (legacy rows have no storage key).
-- CONSTRAINT ORDER:          table (FK to products ON DELETE CASCADE) -> index -> guarded copy.
-- DATA-LOSS / ROLLBACK RISK: None on apply. Rollback is lossless for legacy images because products.image is kept:
--                              DROP TABLE IF EXISTS product_images;
--                            (uploaded images created after this migration live only in this table and in the upload directory.)
-- EMPTY DATABASE:            Yes - requires "products" to exist first.
-- EXISTING DATABASE:         Yes - idempotent: CREATE ... IF NOT EXISTS, and the copy is guarded by NOT EXISTS so re-running
--                            never duplicates; safe if ddl-auto=update already created the table from the ProductImage entity.
-- Manual application order: V10 -> V11 -> V12.

CREATE TABLE IF NOT EXISTS product_images (
    id          BIGSERIAL    PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url   VARCHAR(500) NOT NULL,
    storage_key VARCHAR(255),
    alt_text    VARCHAR(255),
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON product_images (product_id);

INSERT INTO product_images (product_id, image_url, sort_order)
SELECT p.id, p.image, 0
  FROM products p
 WHERE p.image IS NOT NULL
   AND btrim(p.image) <> ''
   AND NOT EXISTS (SELECT 1 FROM product_images pi WHERE pi.product_id = p.id);
