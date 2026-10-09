-- B02-Catalog: category tree (parent_id) and slug.
--
-- LEGACY DATA IMPACT:        Every existing category gets slug = 'category-' || id and parent_id = NULL (a root), so the flat
--                            list behaves exactly as before. Names, descriptions and product links are untouched.
-- NULLABILITY:               slug is added NULLABLE, backfilled, and only then set NOT NULL + unique. parent_id stays nullable
--                            (NULL = root category).
-- CONSTRAINT ORDER:          1 add columns -> 2 backfill slug -> 3 slug NOT NULL -> 4 unique slug index -> 5 parent FK
--                            (ON DELETE RESTRICT, so removing a subtree is always explicit) -> 6 parent index.
-- DATA-LOSS / ROLLBACK RISK: None on apply. Rollback (manual; loses only the tree/slug data):
--                              ALTER TABLE categories DROP CONSTRAINT IF EXISTS fk_categories_parent;
--                              DROP INDEX IF EXISTS uq_categories_slug, idx_categories_parent_id;
--                              ALTER TABLE categories DROP COLUMN IF EXISTS parent_id, DROP COLUMN IF EXISTS slug;
-- EMPTY DATABASE:            Yes - requires "categories" to exist first.
-- EXISTING DATABASE:         Yes - idempotent. Safe if ddl-auto=update already added nullable columns from the Category entity.
--                            Load database/seed-data.sql BEFORE this migration (the seed does not set a slug).
-- Manual application order: V10 -> V11 -> V12.

ALTER TABLE categories ADD COLUMN IF NOT EXISTS parent_id BIGINT;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS slug      VARCHAR(150);

UPDATE categories SET slug = 'category-' || id WHERE slug IS NULL;

ALTER TABLE categories ALTER COLUMN slug SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_slug ON categories (slug);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'categories'::regclass AND conname = 'fk_categories_parent') THEN
        ALTER TABLE categories
            ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories (parent_id);
