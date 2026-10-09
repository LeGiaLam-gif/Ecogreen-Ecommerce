-- B02-Catalog: extend products (slug, compare_price, sku, brand), widen the status CHECK, add list indexes.
--
-- LEGACY DATA IMPACT:        Every existing row gets slug = 'product-' || id (deterministic, always contains a letter, so it
--                            can never collide with a numeric id). compare_price, sku and brand stay NULL. products.image,
--                            stock_quantity and all other columns are untouched. Existing status values ACTIVE/INACTIVE
--                            stay valid.
-- NULLABILITY:               slug is added NULLABLE, backfilled, and only then set NOT NULL. compare_price, sku and brand
--                            remain nullable by design.
-- CONSTRAINT ORDER:          1 add columns -> 2 backfill slug -> 3 slug NOT NULL -> 4 unique indexes -> 5 drop EVERY status
--                            CHECK (chk_products_status from init-postgres.sql AND the Hibernate-generated products_status_check,
--                            found through pg_constraint, never by guessed name) -> 6 add the 5-value status CHECK ->
--                            7 compare_price CHECK, only if no existing row violates it -> 8 list indexes.
-- DATA-LOSS / ROLLBACK RISK: None on apply (additive). Rollback (manual; loses only the new column data):
--                              ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_compare_price;
--                              ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_status;
--                              -- only valid while no row uses DRAFT / OUT_OF_STOCK / ARCHIVED:
--                              ALTER TABLE products ADD CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE','INACTIVE'));
--                              DROP INDEX IF EXISTS uq_products_slug, uq_products_sku, idx_products_status_category, idx_products_created_at;
--                              ALTER TABLE products DROP COLUMN IF EXISTS slug, DROP COLUMN IF EXISTS compare_price,
--                                                                   DROP COLUMN IF EXISTS sku, DROP COLUMN IF EXISTS brand;
-- EMPTY DATABASE:            Yes - requires "products" (database/init-postgres.sql or ddl-auto) to exist first.
-- EXISTING DATABASE:         Yes - idempotent. Safe if ddl-auto=update already added nullable columns from the Product entity.
--                            NOTE: database/seed-data.sql inserts products/categories WITHOUT a slug, so load the seed BEFORE
--                            V10/V12 (the backfills then cover the seeded rows); the seed fails once slug is NOT NULL.
-- Manual application order: V10 -> V11 -> V12 (V11 reads products.image; V12 is independent of V10/V11).

ALTER TABLE products ADD COLUMN IF NOT EXISTS slug          VARCHAR(180);
ALTER TABLE products ADD COLUMN IF NOT EXISTS compare_price DECIMAL(12,2);
ALTER TABLE products ADD COLUMN IF NOT EXISTS sku           VARCHAR(64);
ALTER TABLE products ADD COLUMN IF NOT EXISTS brand         VARCHAR(100);

UPDATE products SET slug = 'product-' || id WHERE slug IS NULL;

ALTER TABLE products ALTER COLUMN slug SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_products_slug ON products (slug);
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_sku  ON products (sku) WHERE sku IS NOT NULL;

-- Drop every CHECK that mentions "status" (the name is looked up, not guessed), then add the 5-value CHECK.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'products'::regclass
           AND contype = 'c'
           AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE products DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE products
    ADD CONSTRAINT chk_products_status
    CHECK (status IN ('DRAFT','ACTIVE','OUT_OF_STOCK','INACTIVE','ARCHIVED'));

-- compare_price >= price: added only when no existing row violates it (otherwise the service enforces it).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'products'::regclass AND conname = 'chk_products_compare_price')
       AND NOT EXISTS (SELECT 1 FROM products WHERE compare_price IS NOT NULL AND compare_price < price) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_compare_price CHECK (compare_price IS NULL OR compare_price >= price);
    END IF;
END $$;

-- Indexes exist only because the catalogue list filters/sorts by them.
CREATE INDEX IF NOT EXISTS idx_products_status_category ON products (status, category_id);
CREATE INDEX IF NOT EXISTS idx_products_created_at      ON products (created_at DESC);
