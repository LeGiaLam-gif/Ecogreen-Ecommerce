-- B07: returns with items and several images, the 6-value return status, and the link from a refund to its return.
--
-- LEGACY DATA IMPACT:        return_requests is RENAMED to returns (no row is copied or deleted in the normal case). Every legacy row
--                            keeps its id, status (PENDING / APPROVED / REJECTED, all valid in the new CHECK), reason, description,
--                            admin_note and timestamps. The legacy single image_url is KEPT (column not dropped, still nullable, now
--                            deprecated) and is also copied once into return_images. Legacy returns covered a whole order and have NO
--                            return_items: the API shows them with an empty items list, and the application cannot move them to
--                            RETURNED or REFUNDED (nothing to restock or refund) - staff can still approve, reject or ask for info.
--                            If the application already started on the new code BEFORE this script ran, Hibernate (ddl-auto=update)
--                            created an empty returns table next to return_requests: in that case the legacy rows are copied into
--                            returns (only while it is empty) and return_requests is left in place for the owner to drop later.
--                            The only change to a B06 table is refunds.return_id (nullable, new).
-- NULLABILITY:               returns keeps its columns. return_items: return_id, order_item_id, quantity NOT NULL. return_images:
--                            return_id, image_url, sort_order NOT NULL. refunds.return_id is NULLABLE (a cancellation refund has no
--                            return) and every existing refund keeps NULL, so there is nothing to back-fill.
-- CONSTRAINT ORDER:          (1) rename / copy legacy table, (2) drop EVERY CHECK on returns.status (init-postgres.sql
--                            chk_returns_status and any Hibernate-generated one, found in pg_constraint, never guessed) then add
--                            the 6-value chk_returns_status (existing rows already satisfy it), (3) create return_items and
--                            return_images, (4) (re)create their foreign keys with ON DELETE CASCADE on return_id, quantity CHECK and
--                            the unique (return_id, order_item_id), (5) back-fill return_images from image_url (guarded),
--                            (6) add refunds.return_id with its FK (ON DELETE SET NULL) and index, (7) indexes last.
-- DATA-LOSS / ROLLBACK RISK: Dropping return_items / return_images loses every per-item and per-image record created after this
--                            script (the legacy image stays in returns.image_url). Narrowing the status CHECK back fails while any
--                            INFO_REQUESTED / RETURNED / REFUNDED row exists. Manual rollback, in this order:
--                              ALTER TABLE refunds DROP COLUMN IF EXISTS return_id;
--                              DROP TABLE IF EXISTS return_status_history;
--                              DROP TABLE IF EXISTS return_images;
--                              DROP TABLE IF EXISTS return_items;
--                              ALTER TABLE returns DROP CONSTRAINT IF EXISTS chk_returns_status;
--                              ALTER TABLE returns ADD CONSTRAINT chk_returns_status
--                                CHECK (status IN ('PENDING','APPROVED','REJECTED'));
--                              ALTER TABLE returns RENAME TO return_requests;
--                            Take a backup of returns and refunds before applying.
-- EMPTY DATABASE:            Fine - needs orders, order_items, users (database/init-postgres.sql) and refunds (V51). With no
--                            return_requests and no returns table it fails fast with a clear message; once Hibernate has created
--                            returns it only adds the missing constraints, tables and indexes.
-- EXISTING DATABASE:         Fine and idempotent - the rename and the copy are guarded, constraints are looked up before they are
--                            added, the back-fill adds nothing when a return already has an image row, indexes use IF NOT EXISTS.
--
-- Applied by hand with psql (Flyway is not active), after V51:  psql -v ON_ERROR_STOP=1 -1 -f V60__returns_full.sql
-- Stop the old application version first: the old code reads return_requests.
--
-- INDEXES (each is backed by a real query):
--   idx_returns_order_status      (order_id, status)        <- "is another return of this order still open?" on create
--   idx_returns_user_created      (user_id, created_at DESC) <- GET /returns/my
--   idx_returns_status_created    (status, created_at DESC)  <- GET /admin/returns?status=
--   uq_return_items_return_order_item UNIQUE (return_id, order_item_id) <- one line per order item; also serves return_id lookups
--   idx_return_items_order_item   (order_item_id)           <- foreign key lookups
--   idx_return_images_return      (return_id, sort_order)   <- images of a page of returns
--   idx_refunds_return_id         (return_id)               <- the refund of a page of returns
--   idx_return_status_history_return_changed (return_id, changed_at) <- the timeline of one return
--
-- VERIFICATION (read-only; run BEFORE and AFTER and compare):
--   -- 1. legacy rows survive with their statuses (counts must be identical; the table is called returns afterwards)
--   SELECT status, count(*) FROM returns GROUP BY status ORDER BY status;
--   -- 2. exactly one CHECK on status, chk_returns_status, with the 6 values
--   SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
--    WHERE conrelid = 'public.returns'::regclass AND contype = 'c';
--   -- 3. legacy single images copied once (expect 0 rows: a legacy image not yet in return_images)
--   SELECT r.id FROM returns r WHERE r.image_url IS NOT NULL AND btrim(r.image_url) <> ''
--      AND NOT EXISTS (SELECT 1 FROM return_images i WHERE i.return_id = r.id);
--   -- 4. legacy returns without items (they cannot reach RETURNED / REFUNDED)
--   SELECT count(*) FROM returns r WHERE NOT EXISTS (SELECT 1 FROM return_items i WHERE i.return_id = r.id);
--   -- 5. the new refund column and its foreign key
--   SELECT column_name, is_nullable FROM information_schema.columns WHERE table_name = 'refunds' AND column_name = 'return_id';
--   SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'public.refunds'::regclass AND contype = 'f';

-- 1. Table name -------------------------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL OR to_regclass('public.order_items') IS NULL THEN
        RAISE EXCEPTION 'V60 requires orders and order_items: apply database/init-postgres.sql first.';
    END IF;
    IF to_regclass('public.refunds') IS NULL THEN
        RAISE EXCEPTION 'V60 requires the refunds table: apply V51__refunds.sql first.';
    END IF;

    IF to_regclass('public.return_requests') IS NOT NULL AND to_regclass('public.returns') IS NULL THEN
        ALTER TABLE public.return_requests RENAME TO returns;
    ELSIF to_regclass('public.return_requests') IS NOT NULL AND to_regclass('public.returns') IS NOT NULL THEN
        -- Hibernate created an empty returns table first. Move the legacy rows over, but only into an empty table, so a row
        -- created by the new code is never overwritten or skipped.
        IF NOT EXISTS (SELECT 1 FROM public.returns) THEN
            INSERT INTO public.returns (id, order_id, user_id, reason, description, image_url, status, admin_note,
                                        created_at, updated_at)
            SELECT id, order_id, user_id, reason, description, image_url, status, admin_note, created_at, updated_at
              FROM public.return_requests;
            PERFORM setval(pg_get_serial_sequence('public.returns', 'id'),
                           (SELECT COALESCE(MAX(id), 1) FROM public.returns));
        ELSIF EXISTS (SELECT 1 FROM public.return_requests r
                       WHERE NOT EXISTS (SELECT 1 FROM public.returns x WHERE x.id = r.id)) THEN
            RAISE WARNING 'V60: both return_requests and a non-empty returns exist; legacy rows were NOT copied. Merge them by hand.';
        END IF;
    ELSIF to_regclass('public.returns') IS NULL THEN
        RAISE EXCEPTION 'V60 requires return_requests (database/init-postgres.sql) or returns.';
    END IF;
END
$$;

-- image_url stays (data-loss risk) but is deprecated and optional; new returns store images in return_images.
ALTER TABLE returns ALTER COLUMN image_url DROP NOT NULL;
COMMENT ON COLUMN returns.image_url IS 'DEPRECATED (B07): legacy single image link, kept for old rows. New returns use return_images. Drop in B13.';

-- 2. Status CHECK -----------------------------------------------------------------------------------------------------------
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname
          FROM pg_constraint con
          JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
         WHERE con.conrelid = 'public.returns'::regclass
           AND con.contype = 'c'
           AND att.attname = 'status'
    LOOP
        EXECUTE format('ALTER TABLE public.returns DROP CONSTRAINT %I', c.conname);
    END LOOP;
END
$$;

ALTER TABLE returns ADD CONSTRAINT chk_returns_status
    CHECK (status IN ('PENDING', 'INFO_REQUESTED', 'APPROVED', 'REJECTED', 'RETURNED', 'REFUNDED'));

-- 3. return_items and return_images -----------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS return_items (
    id            BIGSERIAL PRIMARY KEY,
    return_id     BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity      INT    NOT NULL
);

CREATE TABLE IF NOT EXISTS return_images (
    id         BIGSERIAL    PRIMARY KEY,
    return_id  BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0
);

-- 4. Constraints. If Hibernate created the tables first they have plain foreign keys without ON DELETE CASCADE (and no CHECK /
--    unique): replace every FK on return_id so both paths end with the same schema.
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT con.conrelid::regclass AS tbl, con.conname
          FROM pg_constraint con
          JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
         WHERE con.conrelid IN ('public.return_items'::regclass, 'public.return_images'::regclass)
           AND con.contype = 'f'
           AND att.attname = 'return_id'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.tbl, c.conname);
    END LOOP;

    ALTER TABLE public.return_items
        ADD CONSTRAINT fk_return_items_return FOREIGN KEY (return_id) REFERENCES public.returns (id) ON DELETE CASCADE;
    ALTER TABLE public.return_images
        ADD CONSTRAINT fk_return_images_return FOREIGN KEY (return_id) REFERENCES public.returns (id) ON DELETE CASCADE;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'public.return_items'::regclass AND contype = 'f'
                      AND confrelid = 'public.order_items'::regclass) THEN
        ALTER TABLE public.return_items
            ADD CONSTRAINT fk_return_items_order_item FOREIGN KEY (order_item_id) REFERENCES public.order_items (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'public.return_items'::regclass AND conname = 'chk_return_items_quantity') THEN
        ALTER TABLE public.return_items
            ADD CONSTRAINT chk_return_items_quantity CHECK (quantity > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'public.return_items'::regclass AND conname = 'uq_return_items_return_order_item') THEN
        ALTER TABLE public.return_items
            ADD CONSTRAINT uq_return_items_return_order_item UNIQUE (return_id, order_item_id);
    END IF;
END
$$;

-- 5. Back-fill return_images from the legacy single image (adds nothing on a re-run: a return that has an image row is skipped).
INSERT INTO return_images (return_id, image_url, sort_order)
SELECT r.id, r.image_url, 0
  FROM returns r
 WHERE r.image_url IS NOT NULL
   AND btrim(r.image_url) <> ''
   AND NOT EXISTS (SELECT 1 FROM return_images i WHERE i.return_id = r.id);

-- 5b. Status history (requirement 4.10: the complete request and decision history is preserved). Append-only; one first row per
--     legacy return with its current status (no actor, no note), guarded so a re-run adds nothing.
CREATE TABLE IF NOT EXISTS return_status_history (
    id         BIGSERIAL   PRIMARY KEY,
    return_id  BIGINT      NOT NULL REFERENCES returns (id) ON DELETE CASCADE,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by BIGINT,
    note       TEXT,
    changed_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO return_status_history (return_id, old_status, new_status, changed_by, note, changed_at)
SELECT r.id, NULL, r.status, NULL, r.admin_note, COALESCE(r.created_at, CURRENT_TIMESTAMP)
  FROM returns r
 WHERE NOT EXISTS (SELECT 1 FROM return_status_history h WHERE h.return_id = r.id);

-- 6. refunds.return_id (the ONLY change to a B06 table) ---------------------------------------------------------------------
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS return_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'public.refunds'::regclass AND contype = 'f'
                      AND confrelid = 'public.returns'::regclass) THEN
        ALTER TABLE public.refunds
            ADD CONSTRAINT fk_refunds_return FOREIGN KEY (return_id) REFERENCES public.returns (id) ON DELETE SET NULL;
    END IF;
END
$$;

-- 7. Indexes -----------------------------------------------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_returns_order_status   ON returns (order_id, status);
CREATE INDEX IF NOT EXISTS idx_returns_user_created   ON returns (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_returns_status_created ON returns (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_return_items_order_item ON return_items (order_item_id);
CREATE INDEX IF NOT EXISTS idx_return_images_return   ON return_images (return_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_refunds_return_id      ON refunds (return_id);
CREATE INDEX IF NOT EXISTS idx_return_status_history_return_changed ON return_status_history (return_id, changed_at);
