# Contract: B03 — Inventory (on-hand / reserved model, stock ledger, concurrency safety)

Owner: B03. Depends on: B01-F1 (`/api/v1` envelope, `ApiPaging`), B01-P3 (`requirePermission`, `INVENTORY_VIEW`, `INVENTORY_ADJUST`),
B02 (products), B05 (order state machine, `InventoryGateway`). Migrations: `V30`, `V31` (hand-applied, see section 8).
Later modules that touch stock (B04 checkout, B07 returns, B08 refunds) MUST follow sections 3 and 4.

## 1. Model and invariants

One `inventory` row per product: `quantity_on_hand` (physically in the warehouse) and `reserved_quantity` (held for unpaid
`PENDING_PAYMENT` orders). **Available = on_hand − reserved. It is never stored**; it is computed when read.

| Invariant | Enforced by |
|---|---|
| `on_hand >= 0`, `reserved >= 0`, `reserved <= on_hand` | DB CHECKs (V30) and the service rules |
| Every change is checked on the row it holds a lock on | `SELECT ... FOR UPDATE` (`findByProductIdForUpdate`) |
| Every change writes exactly one ledger row in the same transaction | `InventoryService.writeLedger` |
| Only `InventoryService` changes stock | no other class writes `inventory`; `products.stock_quantity` is frozen |

`products.stock_quantity` is **frozen**: nothing reads or writes it (the entity field is private, `@Deprecated`,
`updatable = false`; a new product row still inserts 0). Public and admin `stockQuantity` fields now mean **available**.
Removal of the column: B13, after the owner confirms no script or report reads it.

## 2. Operations (`service/inventory/InventoryService`, the only `InventoryGateway`)

All gateway methods run in the caller's transaction (`MANDATORY`): an order and its stock movements commit or roll back together.

| Operation | Effect on the locked row | Ledger row | Fails when |
|---|---|---|---|
| `reserve(product, qty, refType, refId)` | reserved += qty | `RESERVE` | available < qty → `InsufficientStockException`; no inventory row → same (fail closed, WARN logged) |
| `release(...)` | reserved −= qty | `RELEASE` | reserved < qty → `IllegalStateException` (rolls back); no row → 404 |
| `commit(...)` | on_hand −= qty and reserved −= qty (available unchanged) | `COMMIT` | reserved < qty → `IllegalStateException`; no row → 404 |
| `restock(...)` | on_hand += qty | `RESTOCK` | no row → 404 |
| `adjust(product, delta, reason, actor)` | on_hand += delta | `ADJUST` (signed delta) | reason missing/blank/over 255 → 400; delta 0 → 400; on_hand + delta < reserved → 400 `BUSINESS_RULE_VIOLATION`; no row → 404 |
| `setAvailable(product, target, reason, actor)` | computes `delta = target − available` **from the locked row**, then as `adjust`; delta 0 → writes nothing, no ledger row | `ADJUST` | target < 0 → 400 |
| `initialize(product, initialStock, actor)` | creates the row (new product) | `RESTOCK` "Initial stock", or `ADJUST` 0 when the stock is 0 (a RESTOCK of 0 is forbidden by a CHECK) | stock < 0 → 400; an existing row is a logged no-op |
| `available(product)` / `availableFor(ids)` | none (read) | none | a missing row reads as 0 (WARN logged); `availableFor` is ONE query for any number of products |

The insufficient-stock rule is checked **after** the row lock is taken, on the locked values.

## 3. Idempotency rule

`RESERVE`, `RELEASE`, `COMMIT` and `RESTOCK` for the same `(product, change type, referenceType, referenceId)` can be applied
**once**. The service checks while holding the row lock; a replay is a **logged no-op** (INFO), not an error. The unique partial
index `uq_inventory_tx_idempotency` is the backstop. `ADJUST` rows and rows without a reference (initial stock, seed) are not
limited. Gateway callers must always pass both `refType` (≤ 30 chars, e.g. `"ORDER"`) and `refId`; otherwise
`IllegalArgumentException`. The same reference can legitimately appear with different change types (reserve, then release).

## 4. Lock-ordering rule (B04, B07 and anything else that changes several products in one transaction)

The gateway is per product. Whoever changes **several products in one transaction MUST call it in ascending `productId`
order**; two orders that list the same items in opposite order deadlock otherwise. `OrderService` already sorts its items in
`checkout()` and in the status-transition loop. B04 (new checkout) and B07 (returns) must sort too. Also: take no remote call
and no long work inside the locked section. A deadlock is detected by PostgreSQL (SQLState `40P01`); the repository method also
requests a 3 s lock timeout through a JPA hint (whether the PostgreSQL dialect applies a positive timeout was **not verified**).

## 5. Ledger and reconciliation

`inventory_transactions` is append-only: `product_id`, `change_type` (`RESERVE|RELEASE|COMMIT|RESTOCK|ADJUST`), `quantity`
(positive amount; the **signed delta** for `ADJUST`), `on_hand_after`, `reserved_after`, `reference_type`/`reference_id` (both
set or both null), `reason`, `actor_user_id` (admin id for adjustments, null for system movements), `created_at`.

**Reconcile through the `*_after` columns, never by summing quantities.** The last row of a product (highest `id`) must show
`on_hand_after` / `reserved_after` equal to that product's inventory row. Summing is wrong because legacy reserved units (unpaid
orders created before B03) have no `RESERVE` rows and because `quantity` means different things per change type. Each inventory
row starts with an opening row: `Opening balance (V30)` (migrated data), `Initial stock` (new product) or `Seed data` (demo data).
The reconciliation query is in the V31 header.

## 6. Endpoints (`/api/v1/admin/inventory`, standard envelope and error shape)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/` | `inventory:view` | Query `keyword` (name or SKU, ≤ 100 chars, literal match), `lowStock` (true keeps available ≤ `threshold`), `threshold` (default 10, 0..1,000,000; ignored unless `lowStock=true`), `page`, `size` (default 20, max 100). Order is fixed: available ascending, name, id (no `sort` parameter) |
| GET | `/{productId}` | `inventory:view` | 404 when the product has no inventory row |
| GET | `/{productId}/transactions` | `inventory:view` | newest first; 404 when no inventory row |
| PATCH | `/{productId}/adjust` | `inventory:adjust` | `{ quantityDelta, reason }`; actor = authenticated admin; returns the new row |

Row: `{ productId, productName, sku, quantityOnHand, reservedQuantity, available, updatedAt }`. **`reservedQuantity` and
`quantityOnHand` appear only in these admin responses**, never in a public, cart or order response. The two permissions do not
imply each other. Errors: 401 no token, 403 missing permission, 400 validation / `BUSINESS_RULE_VIOLATION`, 404.

Public change: `stockQuantity` in `/api/v1/products`, `/api/v1/admin/products` and the cart items is now the **available**
quantity, computed with one batched query per list, per cart or per detail. `inStock=true` means available > 0.

## 7. Error mapping (changes a B05 rule — recorded here and in `B05-order.md` §3)

`InsufficientStockException` now extends `BusinessRuleViolationException`: **400 `BUSINESS_RULE_VIOLATION`** on `/api/v1`, plain
400 `{message}` on legacy paths (B05 had 409 `CONFLICT`). The message is `Sản phẩm "<public name>" không đủ số lượng tồn kho.`;
the name is looked up on the failure path only; it never contains an id, a reserved quantity or any other internal number.
`GlobalExceptionHandler` is unchanged. Nothing in the repository caught `ConflictException` for stock or relied on the 409.

## 8. Database (migrations are hand-applied; Flyway is not active)

`V30__inventory.sql` (table, CHECKs, FK `ON DELETE CASCADE`; **converts existing data**) and `V31__inventory_transactions.sql`
(ledger, FKs, indexes, opening rows). Both are idempotent and carry the mandatory header block and read-only verification SQL.

- **Apply V30 then V31, in this order, with the application STOPPED and BEFORE its first start with B03 code.** The entities map
  `product_id` as a plain column, so with `ddl-auto=update` alone Hibernate would create these tables **without** the foreign
  keys, CHECKs and the unique partial index. (V30 repairs such a table; V31 adds the missing constraints and indexes.)
- **Conversion (V30):** `reserved = Σ order_items.quantity` of `PENDING_PAYMENT` orders; `on_hand = products.stock_quantity +
  reserved`. Goods of unpaid orders were already subtracted by the legacy model; without this the later COMMIT would subtract
  them twice. `products.stock_quantity` is left untouched.
- **Never auto-restocked:** orders cancelled BEFORE B05's cutover never gave their stock back. V30 contains a read-only
  diagnostic (cancelled orders whose history has only the V21 backfill row); correct stock by hand through the admin adjust.
- Ledger FK `product_id → products` is RESTRICT (audit trail); only `database/seed-data.sql` hard-deletes products, and it clears
  the ledger and inventory first (when those tables exist).
- Expected objects: `inventory` — 3 CHECKs, 1 FK, 1 PK; `inventory_transactions` — 4 CHECKs, 2 FKs, 1 PK, indexes
  `idx_inventory_tx_product_created`, `uq_inventory_tx_idempotency` (partial unique). The exact queries are in the headers.

## 9. TEMPORARY-COMPAT and its removal conditions

| Where | What | Remove when |
|---|---|---|
| `ProductService.update` (`// TEMPORARY-COMPAT(B03)`) | a changed `stockQuantity` from the product form becomes `setAvailable(..., "Admin product form")` | B12 (inventory page replaces the stock input of the product form) |
| `ProductService.create` | `stockQuantity` creates the inventory row (initial stock) | stays: this is the normal way a product starts with stock |
| `AdminDashboard.jsx` | on edit, `stockQuantity` is sent only if the admin changed that input | with the row above (B12) |
| `Product.stockQuantity` (frozen column) | kept, never read | B13 |

## 10. Decisions applied

Defaults of the spec's open decisions were used. In addition: `InventoryService` is the only `InventoryGateway`
(`LegacyStockInventoryGateway` and its two tests were deleted as required by the spec); a missing inventory row reads as 0 and
fails closed; `release`/`commit` beyond the reserved quantity roll the transaction back instead of clamping; `on_hand_after` and
`reserved_after` are NOT NULL; list order is fixed (no client sort); a product created with 0 stock gets an `ADJUST` of 0 first row.

## Requests for contract changes

None.
