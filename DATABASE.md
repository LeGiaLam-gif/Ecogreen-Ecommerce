# Database reference

PostgreSQL 16, database `ecogreen`. This document describes the schema that exists today. How to create it is in the
[README](./README.md#database-setup); the rules for changing it are in [`CLAUDE.md`](./CLAUDE.md).

## Where the schema comes from

| Source | Contents |
|---|---|
| `database/init-postgres.sql` | baseline: `roles`, `users`, `user_roles`, `categories`, `products`, `carts`, `cart_items`, `orders`, `order_items`, `payments`, `return_requests`; seeds the roles `USER` and `ADMIN` |
| `backend/src/main/resources/db/migration/V2` | `users.google_sub` (nullable, unique where not null) |
| `…/V3` | `refresh_tokens` |
| `…/V4` | `permissions`, `role_permissions` |
| `…/V5` | renames role `USER` → `CUSTOMER` (keeping every user's link), creates `MANAGER` |
| `…/V6` | seeds the 27 permissions and the role → permission mapping |
| `…/V10` | `products`: `slug`, `compare_price`, `sku`, `brand`, 5-value status CHECK, list indexes |
| `…/V11` | `product_images` (+ copy of the legacy `products.image`) |
| `…/V12` | `categories`: `parent_id` (tree) and `slug` |
| `DataLoader` (at startup) | makes sure the roles `CUSTOMER`, `MANAGER`, `ADMIN` exist |
| Hibernate `ddl-auto=update` | creates missing tables/columns from the JPA entities in development |

Flyway is **not** active: the `V*.sql` files are applied by hand with `psql`, in numeric order, and are idempotent. Load `database/seed-data.sql` **before** V10/V12 (the seed does not set the new NOT NULL `slug`). Keep each entity
and its SQL consistent (see `CLAUDE.md`).

## Relationships

```
roles ─┬─< user_roles >─┬─ users ─┬─< refresh_tokens
       │                │         ├─< carts ─< cart_items >─ products >─ categories
       └─< role_permissions >─ permissions
                        │         ├─< orders ─┬─< order_items >─ products
                        │         │           ├─< payments
                        │         │           └─< return_requests
                        │         └─< return_requests
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
| `role_permissions` | `(role_id, permission_id)` primary key, foreign keys `ON DELETE CASCADE`, index on `permission_id`. `ADMIN` has all 27, `MANAGER` 24 (all except `system:configure`, `audit:view`, `user:disable`), `CUSTOMER` none |
| `refresh_tokens` | `user_id`, `token_hash` (unique SHA-256 of the opaque token; the token itself is never stored), `family_id`, `expires_at`, `revoked`, `replaced_by_id`, `created_at`. Expired rows are not purged yet |

### Catalogue and cart

| Table | Columns (key points) |
|---|---|
| `categories` | `name` (unique, 100), `slug` (unique), `parent_id` → `categories` (`ON DELETE RESTRICT`, NULL = root), `description` |
| `products` | `category_id` → `categories`, `name`, `slug` (unique, always contains a letter), `description`, `price` (≥ 0), `compare_price` (nullable, ≥ `price`), `sku` (nullable, unique when present), `brand`, `stock_quantity` (≥ 0), `image` (legacy: file name or URL, never binary; kept), `status` (`DRAFT`, `ACTIVE`, `OUT_OF_STOCK`, `INACTIVE`, `ARCHIVED`). "Deleting" a product sets `INACTIVE` so old order lines stay valid |
| `product_images` | `product_id` → `products` (`ON DELETE CASCADE`), `image_url` (≤ 500), `storage_key` (uploads only), `alt_text`, `sort_order`. Uploaded files live in the directory `ecogreen.upload.dir`, not in the database |
| `carts` | one per user (`user_id` unique, `ON DELETE CASCADE`) |
| `cart_items` | `cart_id`, `product_id`, `quantity` (> 0); unique `(cart_id, product_id)`. Name and price are always read from `products`, not copied |

### Orders, payments and returns

| Table | Columns (key points) |
|---|---|
| `orders` | `user_id`, snapshot of `customer_name`, `customer_phone`, `shipping_address`, `subtotal`, `discount_amount`, `shipping_fee` (default 0), `discount_code`, `total_price`, `status` (10 values: `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `RETURN_REQUESTED`, `RETURNED`, `REFUNDED`; default `PENDING_PAYMENT`; `V20`). Indexes `(user_id, created_at DESC)` and `(status, created_at DESC)` (`V22`) |
| `order_status_history` | `order_id` (`ON DELETE CASCADE`), `old_status` (null for the first row), `new_status`, `changed_by` (`ON DELETE SET NULL`), `note`, `changed_at`; index `(order_id, changed_at)` (`V21`) |
| `order_items` | `order_id` (`ON DELETE CASCADE`), `product_id`, `quantity` (> 0), `price` = unit price **at purchase time** |
| `payments` | `order_id`, `payment_method` (free text; the UI sends `COD`, `VIETQR`, `MOMO`, `VNPAY`), `amount`, `status` (`PENDING`, `SUCCESS`, `FAILED`), `transaction_id` (fake `MOCK-…` value). Simulated; no gateway |
| `return_requests` | `order_id`, `user_id`, `reason`, `description`, `image_url` (one link), `status` (`PENDING`, `APPROVED`, `REJECTED`), `admin_note` |

## Behaviour worth knowing

- **Stock:** `products.stock_quantity` is decremented when an order is created. Nothing restores it on cancellation or an approved return.
- **Order status** changes are not validated and there is no status-history table.
- **Time zone:** the connection URL in `application.properties` sets the session time zone to `Asia/Ho_Chi_Minh`.
- **Demo data:** `init-postgres.sql` seeds roles only. `database/seed-data.sql` is optional demo data and **deletes** existing
  orders, payments, cart items, products and categories before inserting (5 categories, 8 products, 2 demo users).
  `database/recycled_products.xlsx` is the source of the demo products.

## Verification queries (read-only)

```sql
SELECT name FROM roles ORDER BY name;                                   -- ADMIN, CUSTOMER, MANAGER (no USER)
SELECT count(*) FROM permissions;                                       -- 27
SELECT r.name, count(rp.permission_id) FROM roles r
  LEFT JOIN role_permissions rp ON rp.role_id = r.id GROUP BY r.name;   -- ADMIN 27, CUSTOMER 0, MANAGER 24
SELECT ur.* FROM user_roles ur LEFT JOIN roles r ON r.id = ur.role_id
  WHERE r.id IS NULL;                                                   -- no rows (no orphaned links)
```
