# Database reference

PostgreSQL 16, database `ecogreen`. This document describes the schema that exists today. How to create it is in the
[README](./README.md#database-setup); the rules for changing it are in [`CLAUDE.md`](./CLAUDE.md).

## Where the schema comes from

| Source | Contents |
|---|---|
| `database/init-postgres.sql` | baseline: `roles`, `users`, `user_roles`, `categories`, `products`, `carts`, `cart_items`, `orders`, `order_items`, `payments`, `return_requests` (renamed `returns` by `V60`); seeds the roles `USER` and `ADMIN` |
| `backend/src/main/resources/db/migration/V2` | `users.google_sub` (nullable, unique where not null) |
| `…/V3` | `refresh_tokens` |
| `…/V4` | `permissions`, `role_permissions` |
| `…/V5` | renames role `USER` → `CUSTOMER` (keeping every user's link), creates `MANAGER` |
| `…/V6` | seeds 27 permissions and the role → permission mapping (29 after `V41`) |
| `…/V10` | `products`: `slug`, `compare_price`, `sku`, `brand`, 5-value status CHECK, list indexes |
| `…/V11` | `product_images` (+ copy of the legacy `products.image`) |
| `…/V12` | `categories`: `parent_id` (tree) and `slug` |
| `…/V20` | `orders`: drops every status CHECK found in `pg_constraint`, renames `PENDING` → `PENDING_PAYMENT` and `CONFIRMED` → `PROCESSING`, adds `chk_orders_status` (10 values) and the default `PENDING_PAYMENT` |
| `…/V21` | `order_status_history` (+ one back-filled row per existing order) |
| `…/V22` | `orders`: `subtotal`, `discount_amount`, `shipping_fee` (NOT NULL, default 0, back-filled), `discount_code`; indexes `(user_id, created_at DESC)` and `(status, created_at DESC)` |
| `…/V30` | `inventory` (on hand / reserved, CHECKs) — **converts** `products.stock_quantity` + the stock held by unpaid orders |
| `…/V31` | `inventory_transactions` (stock ledger, unique partial idempotency index) + one opening row per product |
| `…/V40` | `discounts` (code unique on `upper(code)`, percent XOR amount, window and minimum CHECKs) |
| `…/V41` | seeds `discount:view` and `discount:manage` and maps them to `ADMIN` and `MANAGER` |
| `…/V50` | `payments`: `provider`, `paid_at`, `raw_response`; `chk_payments_status` widened to 5 values (every status CHECK on `payments` is looked up in `pg_constraint` and dropped first); unique partial index on `(provider, transaction_id)`; legacy `MOCK-…` rows back-filled with `provider = 'MOCK'` |
| `…/V51` | `refunds` (owned by B06): FKs to `payments`, `orders`, `users`; status CHECK; unique partial index so an order has at most one non-`FAILED` refund |
| `…/V60` | renames `return_requests` → `returns`, drops `NOT NULL` on `returns.image_url`, replaces the status CHECK (`chk_returns_status`, 6 values); creates `return_items`, `return_images` and `return_status_history`; adds nullable `refunds.return_id`; back-fills legacy images and history |
| `…/V70` | `addresses` (customer address book, FK to `users` `ON DELETE CASCADE`, partial unique index: one default per user) |
| `…/V80` | `analytics_events` (B09): FK to `users` `ON DELETE SET NULL`, `chk_analytics_events_type_format` and `chk_analytics_events_payload_object` guarded by `pg_constraint`, index `(event_type, created_at)`. New table, no data touched |
| `…/V90` | `notifications` (B10): FK to `users` `ON DELETE CASCADE`, CHECKs on `channel`, `status` and the `type` format, unique `(user_id, type, dedupe_key)` (one notification per business event), indexes `(user_id, created_at DESC)` and `(status, created_at)`. New table, no data touched |
| `DataLoader` (at startup) | makes sure the roles `CUSTOMER`, `MANAGER`, `ADMIN` exist |
| Hibernate `ddl-auto=update` | creates missing tables/columns from the JPA entities in development |

Flyway is **not** active: the `V*.sql` files are applied by hand with `psql`, in numeric order, and are idempotent. Load `database/seed-data.sql` **before** V10/V12 (the seed does not set the new NOT NULL `slug`). Apply **V30 then V31 with the application stopped and before its first start with B03 code**: the entities map `product_id` as a plain column, so `ddl-auto=update` alone would create the inventory tables without their foreign keys, CHECKs and the unique partial index. Keep each entity
and its SQL consistent (see `CLAUDE.md`).

## Relationships

```
roles ─┬─< user_roles >─┬─ users ─┬─< refresh_tokens
       │                │         ├─< carts ─< cart_items >─ products >─ categories
       └─< role_permissions >─ permissions
                        │         ├─< orders ─┬─< order_items >─ products
                        │         │           ├─< payments
                        │         │           └─< returns ─┬─< return_items >─ order_items
                        │         │                       └─< return_images
                        │         ├─< returns
                        │         └─< addresses (ON DELETE CASCADE)

products ── inventory (1:1, ON DELETE CASCADE)   products ─< inventory_transactions (RESTRICT) >─ users (actor, SET NULL)
users ─< analytics_events (SET NULL)
users ─< notifications (CASCADE)
```

## Tables

**Conventions:** plural `snake_case` tables, `BIGSERIAL` `id` keys, `created_at` / `updated_at` timestamps, statuses stored as
`VARCHAR` with a `CHECK` constraint, money as `DECIMAL(12,2)` (VND).

### Identity and access

| Table | Columns (key points) |
|---|---|
| `users` | `username` (unique, 50), `email` (unique, 100), `password` (BCrypt hash, never plaintext), `is_active`, `google_sub` (V2), timestamps |
| `roles` | `name` (unique, 50): `CUSTOMER`, `MANAGER`, `ADMIN` |
| `user_roles` | `(user_id, role_id)` primary key, both foreign keys `ON DELETE CASCADE` |
| `permissions` | `code` (unique, 100, e.g. `product:create`), `description`. The Java source of truth is `security/Permissions.java`; `PermissionsCatalogueTest` checks `V6` against it |
| `role_permissions` | `(role_id, permission_id)` primary key, foreign keys `ON DELETE CASCADE`, index on `permission_id`. `ADMIN` has all 29, `MANAGER` 26 (all except `system:configure`, `audit:view`, `user:disable`), `CUSTOMER` none |
| `refresh_tokens` | `user_id`, `token_hash` (unique SHA-256 of the opaque token; the token itself is never stored), `family_id`, `expires_at`, `revoked`, `replaced_by_id`, `created_at`. Expired rows are not purged yet |
| `addresses` | `user_id` (→ `users`, `ON DELETE CASCADE`), `recipient_name`, `phone`, `line1`, `ward`, `district`, `province` (all NOT NULL), `is_default`, timestamps. Index `(user_id)`; unique partial index `(user_id) WHERE is_default` = at most one default per user. At most 10 rows per user (service rule). Orders keep their own text snapshot and never reference this table |

### Catalogue and cart

| Table | Columns (key points) |
|---|---|
| `categories` | `name` (unique, 100), `slug` (unique), `parent_id` → `categories` (`ON DELETE RESTRICT`, NULL = root), `description` |
| `products` | `category_id` → `categories`, `name`, `slug` (unique, always contains a letter), `description`, `price` (≥ 0), `compare_price` (nullable, ≥ `price`), `sku` (nullable, unique when present), `brand`, `stock_quantity` (≥ 0), `image` (legacy: file name or URL, never binary; kept), `status` (`DRAFT`, `ACTIVE`, `OUT_OF_STOCK`, `INACTIVE`, `ARCHIVED`). "Deleting" a product sets `INACTIVE` so old order lines stay valid |
| `product_images` | `product_id` → `products` (`ON DELETE CASCADE`), `image_url` (≤ 500), `storage_key` (uploads only), `alt_text`, `sort_order`. Uploaded files live in the directory `ecogreen.upload.dir`, not in the database |
| `carts` | one per user (`user_id` unique, `ON DELETE CASCADE`) |
| `cart_items` | `cart_id`, `product_id`, `quantity` (> 0); unique `(cart_id, product_id)`. Name and price are always read from `products`, not copied |

### Inventory (B03)

| Table | Columns (key points) |
|---|---|
| `inventory` | `product_id` (PK, → `products`, `ON DELETE CASCADE`), `quantity_on_hand` (≥ 0), `reserved_quantity` (≥ 0 and ≤ on hand), `updated_at`. Available = on hand − reserved, never stored |
| `inventory_transactions` | append-only ledger: `product_id` (→ `products`, RESTRICT), `change_type` (`RESERVE`, `RELEASE`, `COMMIT`, `RESTOCK`, `ADJUST`), `quantity` (positive; signed delta for `ADJUST`), `on_hand_after`, `reserved_after`, `reference_type` + `reference_id` (both or neither), `reason`, `actor_user_id` (→ `users`, `ON DELETE SET NULL`), `created_at`. Unique partial index on `(product_id, change_type, reference_type, reference_id)` for everything except `ADJUST` makes a replayed movement a no-op. Reconcile with the `*_after` columns of the last row, not by summing quantities |

### Orders, payments and returns

| Table | Columns (key points) |
|---|---|
| `discounts` | `code` (stored upper-case; unique index on `upper(code)`), `percent_off` or `amount_off` (exactly one), `min_order_amount`, `valid_from`, `valid_to`, `active`, `created_at` (no `updated_at`). `orders.discount_code` copies the code as text, with no foreign key |
| `orders` | `user_id`, snapshot of `customer_name`, `customer_phone`, `shipping_address`, `subtotal`, `discount_amount`, `shipping_fee` (default 0), `discount_code`, `total_price`, `status` (10 values: `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `RETURN_REQUESTED`, `RETURNED`, `REFUNDED`; default `PENDING_PAYMENT`; `V20`). Indexes `(user_id, created_at DESC)` and `(status, created_at DESC)` (`V22`) |
| `order_status_history` | `order_id` (`ON DELETE CASCADE`), `old_status` (null for the first row), `new_status`, `changed_by` (`ON DELETE SET NULL`), `note`, `changed_at`; index `(order_id, changed_at)` (`V21`) |
| `order_items` | `order_id` (`ON DELETE CASCADE`), `product_id`, `quantity` (> 0), `price` = unit price **at purchase time** |
| `payments` | `order_id`, `payment_method` (`COD`, `VIETQR`, `MOMO`, `VNPAY`), `amount`, `status` (`PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `REFUNDED`; `chk_payments_status`, `V50`), `provider` (`VNPAY`, `MOCK`; null without a gateway), `transaction_id` (the merchant reference of the CURRENT gateway attempt; replaced by every new attempt), `paid_at`, `raw_response` (write-only gateway payload, never returned by the API), `created_at`, `updated_at`. Unique partial index on `(provider, transaction_id)` where `transaction_id` is not null. Legacy mock rows (`MOCK-…`) were back-filled with `provider = 'MOCK'` |
| `refunds` | `payment_id` (→ `payments`), `order_id` (→ `orders`), `return_id` (→ `returns`, `ON DELETE SET NULL`, nullable; `V60`; set for refunds born from a return), `amount` (`DECIMAL(12,2)`; the full payment, or the returned items' value for a partial return), `status` (`PENDING`, `SUCCESS`, `FAILED`), `reason`, `provider_transaction_id` (bank reference entered by staff), `created_by` (→ `users`, `ON DELETE SET NULL`; null for the system), `created_at`, `refunded_at`. Unique partial index on `(order_id)` where `status <> 'FAILED'`: an order has at most one live refund. Created `PENDING` when a paid order is cancelled (`V51`; owned by B06) |
| `returns` | Renamed from `return_requests` by `V60`. `order_id`, `user_id`, `reason` (100), `description`, `image_url` (**deprecated**, nullable, legacy single link), `status` (`PENDING`, `INFO_REQUESTED`, `APPROVED`, `REJECTED`, `RETURNED`, `REFUNDED`; `chk_returns_status`), `admin_note`, `created_at`, `updated_at`. Indexes `(order_id, status)`, `(user_id, created_at DESC)`, `(status, created_at DESC)` |
| `return_items` | `return_id` (`ON DELETE CASCADE`), `order_item_id` (→ `order_items`), `quantity` (> 0); unique `(return_id, order_item_id)`. Legacy returns have no rows (whole-order requests) |
| `return_status_history` | append-only trail of a return: `return_id` (`ON DELETE CASCADE`), `old_status` (null for the first row), `new_status`, `changed_by`, `note`, `changed_at`; index `(return_id, changed_at)` (`V60`). Legacy returns got one back-filled row |
| `return_images` | `return_id` (`ON DELETE CASCADE`), `image_url` (500), `sort_order`. The legacy `image_url` was back-filled here |

### Analytics (B09)

| Table | Columns (key points) |
|---|---|
| `analytics_events` | append-only: `event_type` (≤ 50; lowercase snake_case enforced by `chk_analytics_events_type_format`; the list of valid types lives in `AnalyticsEventTypes`), `user_id` (→ `users`, `ON DELETE SET NULL`, nullable), `session_id` (≤ 100, nullable), `payload` (`JSONB`, nullable; a flat object, `chk_analytics_events_payload_object`), `created_at` (local time of the database zone). Index `(event_type, created_at)` (`V80`). Written only by `AnalyticsEventService`; reports never write other tables |

### Notifications (B10)

| Table | Columns (key points) |
|---|---|
| `notifications` | `user_id` (→ `users`, `ON DELETE CASCADE`), `type` (≤ 50, UPPER_SNAKE_CASE; the valid list is the `NotificationType` enum), `channel` (`EMAIL`/`SMS`/`PUSH`, only EMAIL is sent), `title` (≤ 255), `body` (`TEXT`, plain text), `status` (`PENDING`/`SENT`/`FAILED`, default `PENDING`), `failure_reason` (≤ 50, a short code, never an exception message), `dedupe_key` (≤ 100, e.g. `order:123:PAID`, nullable), `sent_at`, `created_at`. Unique `(user_id, type, dedupe_key)` keeps one notification per event; indexes `(user_id, created_at DESC)` and `(status, created_at)` (`V90`). Written only by `NotificationService` |

## Behaviour worth knowing

- **Stock:** lives in `inventory` (on hand / reserved) with a ledger; an order reserves stock, payment commits it, cancellation releases or restocks it (B05 state machine). `products.stock_quantity` is **frozen** (never read or written; removal planned in B13) — any script or report that still reads it is stale. Returned goods are restocked when a return reaches `RETURNED` (B07, ledger `reference_type = 'RETURN'`). Orders cancelled before B05's cutover were never restocked; V30 has a read-only diagnostic for them.
- **Order status** changes go through the state machine in `entity/OrderStatus.java`; every change is recorded in `order_status_history`.
- **Time zone:** the connection URL in `application.properties` sets the session time zone to `Asia/Ho_Chi_Minh`.
- **Demo data:** `init-postgres.sql` seeds roles only. `database/seed-data.sql` is optional demo data and **deletes** existing
  orders, payments, cart items, products and categories before inserting (5 categories, 8 products, 2 demo users). When the B03 tables
  exist it also clears the ledger and inventory first and creates one inventory row (and a `Seed data` ledger row) per product;
  when they do not exist yet it skips that, and V30/V31 convert the stock later.
  `database/recycled_products.xlsx` is the spreadsheet that `seed-data.sql` names as the origin of its data; no code reads it.

## Verification queries (read-only)

```sql
SELECT name FROM roles ORDER BY name;                                   -- ADMIN, CUSTOMER, MANAGER (no USER)
SELECT count(*) FROM permissions;                                       -- 29 (V6 + V41)
SELECT r.name, count(rp.permission_id) FROM roles r
  LEFT JOIN role_permissions rp ON rp.role_id = r.id GROUP BY r.name;   -- ADMIN 29, CUSTOMER 0, MANAGER 26
SELECT ur.* FROM user_roles ur LEFT JOIN roles r ON r.id = ur.role_id
  WHERE r.id IS NULL;                                                   -- no rows (no orphaned links)
```
