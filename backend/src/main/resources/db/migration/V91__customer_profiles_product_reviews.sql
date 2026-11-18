-- B12: customer profile phone number and verified-purchase product reviews.
--
-- LEGACY DATA IMPACT: None. Existing users receive a NULL phone; reviews start empty.
-- NULLABILITY: phone is optional for existing and future users. Every review has a user, product, rating, and timestamp.
-- CONSTRAINT ORDER: add the nullable profile field, create the empty review table, then add guarded foreign keys,
--                    rating check and unique user/product constraint before creating the query index.
-- DATA-LOSS / ROLLBACK RISK: dropping product_reviews loses all customer reviews. Manual rollback:
--                              DROP TABLE IF EXISTS product_reviews;
--                              ALTER TABLE users DROP COLUMN IF EXISTS phone;
-- EMPTY DATABASE: Fine after the existing users and products tables have been created.
-- EXISTING DATABASE: Fine and idempotent. No existing row is altered other than adding a nullable column.
-- NOT EXECUTED against any real database: apply manually with psql, following the existing V90 convention.
--
-- A review is unique per (user, product). The service additionally requires a currently DELIVERED order
-- belonging to that user which contains the product; a database constraint is not practical for that cross-table rule.

ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);

CREATE TABLE IF NOT EXISTS product_reviews (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    rating     SMALLINT NOT NULL,
    comment    TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'product_reviews'::regclass AND conname = 'fk_product_reviews_user') THEN
        ALTER TABLE product_reviews
            ADD CONSTRAINT fk_product_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'product_reviews'::regclass AND conname = 'fk_product_reviews_product') THEN
        ALTER TABLE product_reviews
            ADD CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'product_reviews'::regclass AND conname = 'chk_product_reviews_rating') THEN
        ALTER TABLE product_reviews
            ADD CONSTRAINT chk_product_reviews_rating CHECK (rating BETWEEN 1 AND 5);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'product_reviews'::regclass AND conname = 'uq_product_reviews_user_product') THEN
        ALTER TABLE product_reviews
            ADD CONSTRAINT uq_product_reviews_user_product UNIQUE (user_id, product_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_product_reviews_product_created
    ON product_reviews (product_id, created_at);
