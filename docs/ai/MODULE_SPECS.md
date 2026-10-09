# EcoGreen — Module specifications (remaining work)

This file is the single place that describes **work that is not implemented yet**: the module order, the migration ranges
and one specification per remaining module. It replaces the former `00_GAP_ANALYSIS.md`, `02_DEVELOPMENT_PLAN.md`,
`03_AGENT_PROMPTS.md` (V1) and `03_AGENT_PROMPTS_V2.md`.

How an AI agent must work (workflow, scope, API contract, migration policy, testing and reporting rules) is defined in
[`CLAUDE.md`](../../CLAUDE.md), **not** here. Permanent contracts of finished modules are in [`contracts/`](./contracts).

Use: give an agent `CLAUDE.md` + sections 1–3 of this file + **exactly one** module specification from section 5 or 6.

## 1. Status (verified against the code at commit `b212ecc`)

| Block | Status | Evidence in the repository |
|---|---|---|
| B01-P1 Google ID-token verification | **Done** | `security/GoogleApiIdentityVerifier` verifies signature, issuer, audience and expiry; Google login is disabled (fails closed) when `GOOGLE_CLIENT_ID` is empty |
| B01-F1 API foundation | **Done** | `api/` package, dual-mode `GlobalExceptionHandler`, `http.js` unwrapping — contract `contracts/B01-api-foundation.md` |
| B01-P2 JWT + rotating refresh tokens | **Done** | `JwtService`, `RefreshTokenService`, `/api/v1/auth/*` — contract `contracts/B01-auth.md` |
| B01-P3 Permission-based RBAC | **Done** (one open decision, see 4) | `Permissions.java`, `AuthGuard.requirePermission`, V4–V6 — contract `contracts/B01-rbac.md`. The B02 catalogue controllers use `requirePermission`; the five other controllers (`OrderController`, `UserController`, `ReturnRequestController`, `StatsController`, `AdminController`) still call `requireAdmin()` |
| B02 Catalog | **Done** | `ProductSearchCriteria`/`ProductSpecifications` server-side search, `product_images`, category tree, `/api/v1` catalogue controllers, V10–V12, `Home.jsx` on the server — contract `contracts/B02-catalog.md`. Legacy `/api/products` and `/api/categories` removed |
| B05 Order | Not started | order status is `PENDING/CONFIRMED/PAID/CANCELLED`; `OrderService.updateStatus` does not validate transitions; no status history |
| B03 Inventory | Not started | stock is `products.stock_quantity`, decremented at checkout; nothing restores it |
| B08 Customer | Not started | `UserController` only lists and deletes; no addresses; no enable/disable endpoint |
| B04 Cart & Checkout | Not started | no discounts or shipping cost; legacy `POST /api/orders` and `/api/cart/*` |
| B06 Payment | Not started | `PaymentService.payNow` is a mock that always succeeds; no gateway, webhook or refund |
| B07 Return & Refund | Not started | `ReturnRequest` covers a whole order, one image, no refund/restock |
| B09 Analytics, B10 Notification | Not started | no `analytics_events`/`notifications`, no event classes |
| B11 / B12 UI slices | Not started | storefront/admin still call legacy endpoints; `AdminDashboard.jsx` is a single large component |
| B13 Infra | Not started | no Flyway, Dockerfile, CI workflow, actuator, Jacoco; no frontend tests |

## 2. Dependency model

Definitions — **HARD**: cannot be merged correctly without the other. **SOFT**: works without it using a documented fallback.
**TEMP-COMPAT**: needs a minimal, labelled stand-in that a named later module removes.

### 2.1 Corrections to the original plan (why the order below is what it is)

Historical rationale: the first plan assumed things the code contradicted. B01 is now complete, so rows that talk about
`requireAdmin()` "until B01-P3" are superseded by the note at the top of section 5.

| V1 claim | Reality in code | V2 resolution |
|---|---|---|
| B04 Checkout can start before B05 Order | `checkout()` must create an order in the *new* initial status (`PENDING_PAYMENT`) and record history; both belong to B05 | **B04 HARD-depends on B05.** B05 is built first and keeps the legacy `POST /api/orders` as TEMP-COMPAT until B04 replaces it |
| B03 Inventory depends on B02 Catalog | Inventory only needs `products.id`, which already exists | B03 has **no dependency on B02** (SOFT only: B02 status `OUT_OF_STOCK`) |
| B05 depends on B03 (release/commit stock) | True, but today's stock lives in `products.stock_quantity` | B05 calls an **`InventoryGateway` port**; B05 ships a TEMP-COMPAT adapter over `products.stock_quantity` (atomic conditional `UPDATE`); **B03 replaces the adapter** with the real reservation model. So B05 → B03 is TEMP-COMPAT, not HARD, and B03 has a HARD dependency on the port defined by B05 |
| B01 must finish (JWT + RBAC) before anything | Other modules only need `requireAdmin()`, which exists | Modules use `requireAdmin()` until B01-P3 ships `requirePermission()`; **permission checks are SOFT** (TEMP-COMPAT `requireAdmin`). The **API foundation (B01-F1)** is HARD for every module that creates `/api/v1` endpoints |
| B13 (Flyway, Docker, CI) blocks everything | Nothing in B01–B12 needs Docker/CI. Migration *files* are enough | **B13 is deferred; it blocks nothing** (see Section 11) |
| `refunds` table in B07 | Cancelling a *paid* order also needs a refund record | `refunds` is a **payment-domain table owned by B06** |
| `RETURNED/RESTOCK` double counting risk | V1: `-> CANCELLED` always `releaseStock`, but a PAID order was already *committed*; release would drive `reserved_quantity` negative | V2 B05: cancel from `PENDING_PAYMENT` → **release**; cancel from a committed state → **restock** |
| Event classes owned by B09 | B05/B06/B07 publish events before B09 exists | **Each event class is owned by the publishing module** in `com.example.backend.event`; B09/B10 only consume |

### 2.2 Module dependency table

| Module | HARD | SOFT | TEMP-COMPAT it creates / consumes |
|---|---|---|---|
| B01-P1 Google login hotfix | — | — | keeps legacy path `/api/auth/google`; replaced by B01-P2 |
| B01-F1 API foundation | — | — | creates envelope/error/pagination/http.js dual-mode (permanent) |
| B01-P2 JWT + refresh | B01-F1 | B01-P1 | legacy `/api/auth/*` aliases removed at end of P2 |
| B01-P3 RBAC | B01-P2 | — | `requireAdmin()` kept (`@Deprecated`) until all modules migrated |
| B02 Catalog | B01-F1 | B01-P3 (permissions) | `requireAdmin` until P3; legacy `products.image` kept |
| B05 Order | B01-F1 | B01-P3 | **creates** `InventoryGateway` + legacy adapter; **keeps** legacy `POST /api/orders` |
| B03 Inventory | B05 (port) | B02 | **removes** legacy adapter; writes inventory via port |
| B08 Customer | B01-P2 | B01-P3, B05 (order history) | — |
| B04 Cart & Checkout | **B05**, B01-F1 | B03 (real inventory), B08 (addresses), B02 | free-text address if B08 absent; removes legacy `POST /api/orders` |
| B06 Payment | **B05**, B04 (payment row creation) | B01-P3 | `mock` provider kept for dev; legacy `/api/payments/order/*` removed |
| B07 Return & Refund | **B05**, **B06** (refund table), **B03** (restock) | B02 | legacy `/api/returns*` removed |
| B09 Analytics | B05 (events/data) | B06, B07, B03 | reads only |
| B10 Notification | B05 (events) | B06, B07, B08 | in-process `@Async` only |
| B11 Storefront UI | the backend module each slice displays | — | slices, see B11 |
| B12 Admin UI | the backend module each slice manages | — | slices, see B12 |
| B13 Deferred Infra | none (runs after or beside) | — | flips `ddl-auto`, activates Flyway, removes deprecated columns |

### 2.3 Required development order (implementation dependency order, not a priority ranking)

```
Step 1   B01-P1   Google login hotfix                 DONE
Step 2   B01-F1   API foundation (envelope/errors/validation/pagination/http.js)   DONE
Step 3   B01-P2   JWT access + refresh tokens, /api/v1/auth, frontend auth migration   DONE
Step 4   B01-P3   Permission-based RBAC                  DONE
Step 5   B02      Catalog                             ┐ may run in parallel
Step 6   B05      Order state machine (+InventoryGateway port, legacy adapter)   ┘ (different files)
Step 7   B03      Inventory reservation model (replaces adapter)
Step 8   B08      Customer management (addresses, enable/disable)
Step 9   B04      Cart & Checkout (discount, shipping, address; replaces legacy POST /api/orders)
Step 10  B06      Payment (gateway abstraction, webhook, refunds)
Step 11  B07      Return & Refund
Step 12  B09      Analytics            ┐ may run in parallel, read-only
Step 13  B10      Notification         ┘
Step 14  B11/B12  Frontend slices — each slice runs right after the backend module it needs (B11/B12 drafts in section 6);
                  they are NOT a single step at the end
Step 15  B13      Deferred infrastructure (Flyway activation, Docker, CI, Redis/RabbitMQ only if justified, …)
Step 16  FINAL    Cross-system efficiency pass (report-driven; no specification written)
```
Rationale for the order: **business correctness** (order state machine, inventory) before **security hardening of every flow** is already satisfied by Steps 1–4 (security-critical auth work is first and independent); **data integrity** (B05→B03→B04→B06→B07) follows the money/stock chain; **API contracts** are fixed once in Step 2 and reused; **frontend integration** happens inside each module (minimum caller migration) plus B11/B12 slices; **analytics/notification** consume stable events; **infrastructure** and **final optimisation** come last.

## 3. Migration version ranges (namespaces, not execution order)

Already used: `V2` (`users.google_sub`), `V3` (`refresh_tokens`), `V4`–`V6` (permissions, `CUSTOMER`/`MANAGER`, seed). These
belong to B01 and are final.

`V1` = baseline = the current `database/init-postgres.sql` (**no module creates V1**; B13 or the owner's minimal Flyway activation creates `V1__baseline.sql` as a verbatim copy).

| Block | Range | Notes |
|---|---|---|
| B01 Auth/RBAC | V2 – V9 | |
| B02 Catalog | V10 – V19 | |
| B05 Order | V20 – V29 | numbered **before** B03 because B05 is built first |
| B03 Inventory | V30 – V39 | |
| B04 Cart/Checkout | V40 – V49 | |
| B06 Payment | V50 – V59 | owns `refunds` |
| B07 Return/Refund | V60 – V69 | |
| B08 Customer | V70 – V79 | built before B04 but numbered later; see rule 2 |
| B09 Analytics | V80 – V89 | |
| B10 Notification | V90 – V99 | |
| B13 cleanup | V100+ | drop deprecated columns, final constraints |

Rules:
1. A migration may reference (FK) only tables owned by modules that come **earlier in the dependency order of Section 2.3**.
2. Numeric order ≠ build order for B08 (V70 is built before B04's V40). When Flyway is activated the owner MUST set `spring.flyway.out-of-order=true` (or apply files manually in dependency order). Each module's report states which prior migrations it assumes.
3. A module must never use a number outside its range. Need more? Report it; do not borrow.
4. Idempotency, documentation header and EMPTY/EXISTING-database statements are mandatory (see `CLAUDE.md`, Database and migrations).

## 4. Decisions referenced in the specs but never written down

The original V2 prompt pointed to an "open decisions" section (its "Section 12") that was never written. The IDs that are
still mentioned in the repository, with what is actually known:

| ID | Where it appears | Known content |
|---|---|---|
| D-1 | B04 | customer totals change once shipping is enabled; must be documented when B04 ships |
| D-5 | `contracts/B01-auth.md` §8 | refresh token in an httpOnly cookie instead of `localStorage` — **open**, not switched |
| D-6 | `contracts/B01-rbac.md` §3 | `MANAGER` = all permissions except `system:configure`, `audit:view`, `user:disable` — implemented as the default |
| D-9 | `CLAUDE.md` (API contract) | the legacy `Asia/Ho_Chi_Minh` JVM/DB timezone default vs UTC in JSON — **open** |
| permission loading | `contracts/B01-rbac.md` §7 | the spec asked for one joined query when issuing a token; the implemented EAGER + `@BatchSize` mapping was never measured — **open** (options 1–4 in that contract) |

If a task is blocked by one of these, use the stated default, report it, and do not invent a different decision.

### Backlog items no module specification covers

From the original gap analysis; each needs a new, explicitly assigned task:

- **Audit log of admin actions** (`audit_logs` table and writer). The `audit:view` permission exists but nothing records or serves data.
- Externalise datasource and CORS settings (`application.properties` hard-codes the DB URL/user/password and `WebConfig` allows only `http://localhost:5173`).
- HTTPS / secret management for real deployments.
- Login throttling is per instance and in memory; there is no rate limit on other endpoints.

## 5. Module specifications (written against the real code, ready to use)

> Written for the V2 workflow. Where a spec says "until B01-P3 use `requireAdmin()`" or marks `TEMPORARY-COMPAT` for a
> missing permission check: **B01-P3 is complete**, so use `AuthGuard.requirePermission` with a constant from
> `security/Permissions.java`. A new permission code must be added to `Permissions.java` **and** to a seed migration in the
> module's own range; `PermissionsCatalogueTest` fails if the two drift. References to "section 1.x" below mean the
> corresponding rule in `CLAUDE.md`.

### B02 — Catalog (Product, Category, Images, Search)

**Dependencies.** HARD: B01-F1 (envelope, paging helpers, validation). SOFT: B01-P3 (`requirePermission`; until it exists use `requireAdmin()` and mark `// TEMPORARY-COMPAT(B02): switch to requirePermission when B01-P3 merged`). No dependency on B03/B05.

**Problem (current code).**
- `GET /api/products` returns **every** ACTIVE product, unpaginated; `Home.jsx` downloads all products and categories and filters/searches/“load more” **in the browser**. `ProductRepository.findByNameContainingIgnoreCase` exists but is unused by the API.
- `Product` has one `image` string (a bare filename resolved by `frontend/src/utils/imageResolver.js` against `src/assets/products/`, or a full URL), status `ACTIVE|INACTIVE`, and no `slug/sku/brand/comparePrice`. `ProductController` takes `Map<String,Object>` and calls `new BigDecimal(body.get("price").toString())` / `Integer.parseInt(...)` directly (NPE/NumberFormatException → today a 400/500 with leaked text).
- `Category` is flat. `ProductResponse.from(p)` reads `p.getCategory()` (LAZY) → likely one extra query **per product** on every list.
- No upload API exists; the admin form types an image filename/URL.

**Why it matters.** Catalogue size is bounded by what a browser can download; SEO/URLs, multi-image galleries, price-strike display and category trees are missing; malformed admin input produces unsafe errors.

**Required behaviour.**
1. **Server-side list**: `GET /api/v1/products?keyword=&categoryId=&minPrice=&maxPrice=&inStock=&page=&size=&sort=` (default `size=12` for the storefront, max 100; `sort` whitelist: `createdAt`, `price`, `name`). Public list returns `ACTIVE` products only (decision D-8: `OUT_OF_STOCK` stays admin-set and hidden from the public list by default — revisit in B03/B11). `keyword` matches `name` and `description` case-insensitively; `%`, `_` and `\` in the keyword are escaped; empty keyword = no filter.
2. **Detail**: `GET /api/v1/products/{idOrSlug}`; numeric → id, otherwise slug. Slugs must contain at least one letter so they can never collide with ids.
3. **Fields added**: `slug` (unique, NOT NULL), `comparePrice` (nullable, if present must be ≥ `price`), `sku` (nullable, unique when present), `brand` (nullable). **Status** widened to `DRAFT, ACTIVE, OUT_OF_STOCK, INACTIVE, ARCHIVED`. Preserve today's behaviours: create defaults to **ACTIVE** (DRAFT is opt-in); `DELETE` stays a soft-delete to **INACTIVE**; ARCHIVED is set explicitly with `PATCH`.
4. **Publish**: `POST /api/v1/admin/products/{id}/publish` (DRAFT/INACTIVE → ACTIVE) requires name, price > 0, a category, and at least one image (a legacy `image` counts). SKU is **not** required (legacy rows have none).
5. **Images**: new table `product_images`; `ProductResponse` returns `images:[{id,url,altText,sortOrder}]` **and still returns the legacy `image` field** (= first image URL or legacy value) so existing UI works unchanged. Upload endpoint behind an `ObjectStorageClient` interface with a local-disk implementation (directory from `ecogreen.upload.dir`, outside the classpath, git-ignored). Upload rules: size ≤ 5 MB, allowed types jpeg/png/webp **verified by magic bytes** (not extension/Content-Type), server-generated random filename, never use the client filename or path, serve with `X-Content-Type-Options: nosniff`. Served through `GET /api/v1/files/{key}` (path-traversal-safe) so the existing Vite `/api` proxy works — this is the **only** non-enveloped success response; document it in the contract.
6. **Categories**: `parent_id` + `slug`; `GET /api/v1/categories` flat (default) or `?tree=true`; admin create/update/delete. Reject parent cycles; deleting a category that has products or children → 409 `CONFLICT`.
7. **Typed DTOs + validation** (`ProductCreateRequest`, `ProductUpdateRequest`, `CategoryRequest`) replace `Map<String,Object>`; unknown/overlong/negative values → 400 `VALIDATION_ERROR` with `fields`.
8. **Slug generation** for new/renamed products: lowercase, Vietnamese diacritics stripped (`Normalizer` NFD + `đ→d`), non-alphanumerics → `-`, de-duplicated with a numeric suffix. Never rely on SQL `regexp_replace` for Vietnamese names (it destroys accented letters).
9. **Stock fields are untouched** in this module: `stockQuantity` stays in create/update/response exactly as today (B03 takes it over). Do not touch `CartService` or checkout.

**Inspect (REQUIRED TO CHECK).** `entity/Product.java`, `entity/Category.java`, `repository/ProductRepository.java` (note the overridden `findAll`/`findById` — check for fetch joins), `repository/CategoryRepository.java`, `service/ProductService.java`, `service/CategoryService.java`, `controller/ProductController.java` (incl. its `/admin/all`), `controller/CategoryController.java`, `dto/ProductResponse.java`, `dto/CategoryResponse.java`, `service/CartService.java` + `OrderService` (read-only: they read `Product.status`/`stockQuantity`), `database/init-postgres.sql`, `database/seed-data.sql`, `frontend/src/services/productApi.js`, `categoryApi.js`, `adminApi.js`, `pages/Home.jsx`, `ProductDetail.jsx`, `components/ProductCard.jsx`, `CategoryFilter.jsx`, `Navbar.jsx` (search box), `pages/admin/AdminDashboard.jsx` (product tab only), `utils/imageResolver.js`, `vite.config.js`.

**Database.** (idempotent; header block per the migration policy in `CLAUDE.md` on each)
- `V10__products_extend.sql`: add `slug`, `compare_price`, `sku`, `brand` **nullable**; backfill `slug = 'product-' || id` for NULL; **then** `SET NOT NULL` on slug and create unique indexes (`uq_products_slug`, `uq_products_sku` partial `WHERE sku IS NOT NULL`). Replace the status CHECK: drop `chk_products_status` **and any Hibernate-generated `products_status_check`**, then add the 5-value CHECK. Add `CHECK (compare_price IS NULL OR compare_price >= price)` only if existing data satisfies it (verify with a query; otherwise skip and enforce in the service). Indexes (only because the list query filters by them): `idx_products_status_category (status, category_id)`, `idx_products_created_at (created_at DESC)`.
- `V11__product_images.sql`: `product_images(id, product_id FK ON DELETE CASCADE, image_url VARCHAR(500) NOT NULL, storage_key VARCHAR(255), alt_text VARCHAR(255), sort_order INT NOT NULL DEFAULT 0, created_at)`; index on `product_id`; copy legacy `products.image` (NOT NULL/non-blank) as `sort_order = 0` **guarded by `NOT EXISTS`** so re-running does not duplicate. **Keep `products.image` and keep `Product.image` (`@Deprecated`)**; stop treating it as the source of truth but keep returning it. Legacy values remain bare filenames or URLs and are still resolved by `imageResolver.js` — do not rewrite them. Column drop is a B13 task.
- `V12__categories_tree.sql`: add `parent_id` (FK to `categories(id)` `ON DELETE RESTRICT` — not `SET NULL`, so subtree deletion is explicit) and `slug` (backfill `'category-' || id`, then NOT NULL + unique); index on `parent_id`.
Legacy impact: all existing rows get deterministic slugs/NULL optional columns; status values `ACTIVE/INACTIVE` remain valid; rollback = drop new columns/tables (image copy is lossless because `products.image` is kept). Empty DB: fine. Existing DB: fine.

**API.** (new, `/api/v1`)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/products` | public | paged, filters above |
| GET | `/api/v1/products/{idOrSlug}` | public | ACTIVE only for public |
| GET | `/api/v1/admin/products` | `product:view` | all statuses, `status`/`keyword` filters, paged |
| POST | `/api/v1/admin/products` | `product:create` | |
| PATCH | `/api/v1/admin/products/{id}` | `product:update` | partial |
| POST | `/api/v1/admin/products/{id}/publish` | `product:publish` | |
| DELETE | `/api/v1/admin/products/{id}` | `product:delete` | → INACTIVE |
| POST | `/api/v1/admin/products/{id}/images` | `product:update` | multipart |
| DELETE | `/api/v1/admin/products/{id}/images/{imageId}` | `product:update` | |
| GET | `/api/v1/categories` | public | `?tree=true` |
| POST/PATCH/DELETE | `/api/v1/admin/categories[/{id}]` | `category:*` | |
| GET | `/api/v1/files/{key}` | public | binary, nosniff |

Legacy `/api/products`, `/api/categories` (and the product `/admin/all`) are **removed in this task** after the frontend is migrated, unless you keep an alias with a stated removal condition. Error codes per 1.6 (slug/sku duplicate → 409 `CONFLICT`).

**Frontend (migrate in this task — required).**
- `productApi.js`/`categoryApi.js`/`adminApi.js` → `/api/v1`, returning `{items, meta}` for lists.
- `Home.jsx`: replace the in-browser filter/slice with server calls (`keyword`, `categoryId`, `page`, `size=12`); debounce search input (~300 ms), cancel stale requests (`AbortController`), keep the existing "load more" UX by appending the next page; remove the full-array filtering code. Keep the `searchTerm` prop plumbing from `Navbar`.
- `ProductDetail.jsx`: keep route `/product/:id`; show the gallery when `images.length > 0`, else fall back to `image`; show `comparePrice` strike-through only when present. `ProductCard.jsx`: keep using `image` (or `images[0]`).
- Admin product tab in `AdminDashboard.jsx`: **only** adapt the product API calls/response shape and add the new optional fields (`sku`, `brand`, `comparePrice`, status options); the full admin UI redesign is B12. Admin product list must use the paged admin endpoint (no full-array filter for products).
- `vite.config.js`: no change expected (files are served under `/api`).

**Security.** Admin endpoints enforce permission/role server-side; uploaded files validated as above; reject SVG uploads (script risk) unless sanitised — default: not allowed; price/stock/status validation server-side; never render product text with `dangerouslySetInnerHTML`; `sort`/`keyword` parameters never concatenated into SQL (JPQL/Specification/Criteria parameters only).

**Compatibility.** Old clients on `/api/products` break (frontend migrated here). `ProductResponse` keeps `id,name,description,price,stockQuantity,image,status,categoryId,categoryName` (check the real field names) and **adds** fields, so cart/checkout code keeps working. Compat code: deprecated `image` column/field; removal condition: B13 after the owner confirms all UIs use `images`.

**Efficiency review (do it; report evidence).** (a) Count SQL statements for a 12-item list before/after (temporarily enable SQL logging; do not commit it): fix the category N+1 with `JOIN FETCH`/`@EntityGraph`. (b) Do **not** `JOIN FETCH` the images collection together with `Pageable` (in-memory pagination); load the page, then load images for the page ids with one `IN` query (or `@BatchSize`). (c) `ILIKE '%kw%'` cannot use a btree index — report it; **do not** add `pg_trgm`/Elasticsearch unless the owner asks. (d) Home page: confirm no request is repeated per keystroke after debounce and no full dataset is downloaded. (e) Keep list DTOs small (no full description if the card does not use it — measure before removing).

**Tests (mandatory).** `search_priceRange_returnsOnlyMatching`, `search_keywordWithPercentAndUnderscore_isLiteral`, `search_sortNotWhitelisted_isRejected`, `publicList_neverReturnsDraftInactiveArchived`, `publish_missingImageOrCategory_throwsBusinessRule`, `slug_vietnameseName_isTransliteratedAndUnique`, `slug_numericOnly_isRejectedOrPrefixed`, `create_defaultsToActive`, `delete_setsInactive_notArchived`, `category_cycle_isRejected`, `category_deleteWithProducts_returns409`, `category_tree_buildsParentChild`, `upload_nonImageBytesWithImageExtension_isRejected`, `upload_oversize_isRejected`, `upload_pathTraversalKey_isRejected`, `listProducts_queryCount_doesNotGrowWithRows` (needs a real DB with statistics; if impossible report "not executed"). Frontend: `npm run lint` + `npm run build`; manual: search, category filter, load-more, product detail with and without gallery, admin create/edit product.

**Must NOT change.** Stock/quantity logic, cart, checkout, orders, auth, `init-postgres.sql` (never edit; use migrations).

**ALLOWED**: `entity/Product*.java`, `entity/Category.java`, `entity/ProductImage.java`, product/category repository/service/controller/DTOs, new `storage/` package (`ObjectStorageClient`), migrations V10–V12, `application.properties` (additive upload props), `.env.example` (`UPLOAD_DIR`), `.gitignore` (uploads dir), the frontend files in Inspect, tests, `docs/ai/contracts/B02-catalog.md`. **FORBIDDEN**: `CartService`, `OrderService`, `PaymentService`, auth code, `ReturnRequest*`.

**Definition of Done.** Server-side search/filter/sort/pagination live and used by `Home.jsx`; no browser-side full-dataset filtering remains in storefront or the admin product tab; public list never exposes non-ACTIVE products; migrations V10–V12 idempotent with legacy images preserved; images upload safely; contract doc lists `ProductSearchCriteria`, status enum, DTO shapes and the public service signatures (`ProductService.search(...)`, `getByIdOrSlug(...)`, `publish(...)`); all listed tests pass or are reported as not run.

**Report.** the completion report in `CLAUDE.md` + the SQL-statement counts before/after for the product list.

---

### B05 — Order (state machine, history, safe checkout core)

**Dependencies.** HARD: B01-F1. SOFT: B01-P3 (permissions). TEMP-COMPAT: creates the `InventoryGateway` port + a legacy adapter over `products.stock_quantity` (replaced by B03); keeps the legacy `POST /api/orders` checkout path (replaced by B04). **Must be built before B04 and B03.**

**Problem (current code).**
- `Order.Status` = `PENDING|CONFIRMED|PAID|CANCELLED`. `OrderService.updateStatus(id, String)` does `Status.valueOf(status)` and saves: **any transition is allowed** (e.g. `CANCELLED → PAID`), and unknown strings throw an unmapped exception.
- **Nothing restores stock** when an order is cancelled or returned. Admin cancelling an order permanently leaks stock.
- `OrderService.checkout()` reads stock then writes `products.stock_quantity - qty` with no lock or atomic update → two simultaneous checkouts of the last item can both pass the check and drive stock negative (the DB `CHECK (stock_quantity >= 0)` then fails one with an unmapped exception).
- Order status is written in more than one place (`OrderService.updateStatus`, `PaymentService.payNow`).
- `OrderController` returns bare unpaged lists; `GET /api/orders` (admin) loads **all** orders and calls `getItems(order.getId())` per order, and `OrderItemResponse.from` touches the lazy `product` per item → N+1 explosion.
- `Order` has no `subtotal/discount/shipping` columns (needed by B04), and there is no status history.
- `ReportService`/`OrderRepository` revenue queries count only `status = PAID`; once new in-between statuses exist, shipped orders would silently vanish from revenue.
- Frontend order status labels are duplicated in `Orders.jsx`, `OrderDetail.jsx`, `AdminDashboard.jsx`, and treat legacy `PAID` as "paid & delivered".

**Why it matters.** Wrong transitions corrupt order/payment/stock consistency; stock leaks and oversells; admin and dashboard performance collapses with data volume; revenue reports become wrong.

**Required behaviour.**
1. **Statuses** (10): `PENDING_PAYMENT, PAID, PROCESSING, PACKED, SHIPPED, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED`. Single enum `OrderStatus` is the source of truth; DB CHECK mirrors it.
2. **Transition table** — hard-coded `Map<OrderStatus, Set<OrderStatus>>`, the only place it is defined:
```
PENDING_PAYMENT  -> PAID, CANCELLED, PROCESSING*      (* COD orders only: payments.payment_method = 'COD')
PAID             -> PROCESSING, CANCELLED**           (** staff only; paid money must be refunded, see 6)
PROCESSING       -> PACKED, CANCELLED**
PACKED           -> SHIPPED
SHIPPED          -> DELIVERED
DELIVERED        -> RETURN_REQUESTED
RETURN_REQUESTED -> RETURNED, DELIVERED               (DELIVERED = return rejected)
RETURNED         -> REFUNDED
CANCELLED, REFUNDED -> (terminal)
```
3. **`OrderService.transitionStatus(orderId, newStatus, actorUserId /*nullable for system*/, note)` is the ONLY method allowed to change `orders.status`** anywhere in the codebase. Grep for `setStatus(Order.Status` / `Order.Status.` and route every writer (`OrderService.updateStatus`, `PaymentService.payNow`, return code) through it. It: locks the order row (`PESSIMISTIC_WRITE` via `findByIdForUpdate`) so concurrent transitions (double click, webhook + admin) serialise; validates the table (else `BusinessRuleViolationException` → 400 `BUSINESS_RULE_VIOLATION` with from/to in the message); applies the inventory side effect; writes one `order_status_history` row; publishes `OrderStatusChangedEvent(orderId, oldStatus, newStatus, actorUserId)` after commit (`ApplicationEventPublisher`; consumers use `@TransactionalEventListener(AFTER_COMMIT)`). Same-status transition = 400, never a silent no-op.
4. **Inventory side effects through the port** (`InventoryGateway`, interface in `service/inventory/` owned by B05; B03 supplies the real implementation):
   - `PENDING_PAYMENT → PAID` and COD `PENDING_PAYMENT → PROCESSING`: `commit` each item.
   - `PENDING_PAYMENT → CANCELLED`: `release` each item.
   - `PAID|PROCESSING → CANCELLED`: **`restock`** each item (the stock was already committed). **Do not call `release` here** — that would corrupt the reservation count (V1 bug).
   - `RETURNED`, `REFUNDED`: no inventory effect here (B07 owns restocking returned goods; never restock in two places).
   - Port methods: `reserve(productId, qty, refType, refId)` (throws `InsufficientStockException`), `release`, `commit`, `restock`, `available(productId)`.
   - **Legacy adapter** (TEMP-COMPAT, removed by B03): `reserve` = atomic `UPDATE products SET stock_quantity = stock_quantity - :q WHERE id = :id AND stock_quantity >= :q` (0 rows → `InsufficientStockException`; this also fixes the legacy race); `release`/`restock` = `+ :q`; `commit` = no-op (legacy stock was already decremented at reserve time); `available` = `stock_quantity`.
5. **`createPendingOrder(userId, List<OrderItemDraft>, subtotal, discountAmount, shippingFee, total, customerName, customerPhone, shippingAddress, discountCode)`**: persists `Order(PENDING_PAYMENT)` + `OrderItem`s (price snapshot) + the first history row; **does not** reserve stock or touch the cart (the caller orchestrates, in one transaction). Totals are recomputed/validated server-side by the caller; the method rejects `total != subtotal - discountAmount + shippingFee`.
6. **Cancellation rules**: customer self-cancel (`POST /api/v1/orders/{id}/cancel`) only from `PENDING_PAYMENT` and only for the order owner; staff with `order:cancel` may cancel per the table. Cancelling an order whose payment is `SUCCESS` writes the history note `REFUND_REQUIRED` and publishes `OrderCancelledEvent(orderId, refundRequired=true)`; the refund itself is built by B06 (until then it is a manual task — state this as a known limitation).
7. **Legacy checkout kept (TEMP-COMPAT)**: `POST /api/orders` (body `{customerName, customerPhone, shippingAddress, paymentMethod}`, bare `OrderResponse`, 201) keeps working but is rebuilt on `createPendingOrder` + `InventoryGateway.reserve` + the existing `Payment(PENDING)` row + cart clear, **in one transaction** (any `InsufficientStockException` rolls back the order). Removal condition: B04 ships `POST /api/v1/checkout` and migrates `Checkout.jsx`.
8. **`PaymentService.payNow()` (legacy mock; owned by B06)**: minimal TEMP-COMPAT edit — replace the direct `order.setStatus(PAID)` with `orderService.transitionStatus(orderId, PAID, null, "Mock payment")`, and reject (400) when the order is not `PENDING_PAYMENT`. Behaviour otherwise unchanged until B06 (the legacy COD "confirm" button still goes through `payNow`, so a COD order becomes `PAID` exactly like today — see open decision D-7).
9. **Revenue/report semantics**: add `OrderStatus.countsAsRevenue()` = `PAID, PROCESSING, PACKED, SHIPPED, DELIVERED, RETURN_REQUESTED`; update the `OrderRepository` revenue/top-product queries and `ReportService` filters to use it instead of the literal `PAID` (so existing dashboards keep reporting the same orders), and map `orders-by-status` output to the new enum. Report every query you changed.
10. **Columns for B04**: `orders.subtotal`, `orders.discount_amount`, `orders.shipping_fee` (default 0), `orders.discount_code` (plain VARCHAR, **no FK**, so deleting a discount never breaks history). Existing orders: `subtotal = total_price`, discount/shipping 0.

**Inspect (REQUIRED TO CHECK).** `entity/Order.java`, `entity/OrderItem.java`, `entity/Payment.java`, `repository/OrderRepository.java` (revenue `@Query`s), `repository/OrderItemRepository.java`, `repository/ProductRepository.java`, `service/OrderService.java`, `service/PaymentService.java`, `service/ReportService.java`, `controller/OrderController.java`, `controller/StatsController.java`, `controller/AdminController.java`, `dto/OrderResponse.java`, `dto/OrderItemResponse.java`, `database/init-postgres.sql` (orders constraint names), frontend `services/orderApi.js`, `reportApi.js`, `adminApi.js`, `pages/Orders.jsx`, `OrderDetail.jsx`, `OrderSuccess.jsx`, `Payment.jsx`, `Checkout.jsx`, `pages/admin/AdminDashboard.jsx` (orders tab), `SalesDashboard.jsx`, `components/AdminStats.jsx`. Also `grep -rn "Order.Status\|\"PENDING\"\|'PENDING'\|CONFIRMED" backend frontend/src`.

**Database.** (idempotent; header block per the migration policy in `CLAUDE.md`)
- `V20__order_state_machine.sql`, **in this order**: (1) drop the status CHECK — both `chk_orders_status` and any Hibernate-generated `orders_status_check` (look up in `pg_constraint`); (2) map legacy rows: `PENDING → PENDING_PAYMENT`, `CONFIRMED → PROCESSING` (the legacy UI describes CONFIRMED as "preparing goods"), `PAID → PAID` (no guess about delivery — see D-4), `CANCELLED` unchanged; (3) add the 10-value CHECK; (4) set the default `PENDING_PAYMENT`. Legacy impact: row counts unchanged; **legacy `PAID` orders stay `PAID` and will need staff to advance them** (optional commented SQL for the owner: advance legacy PAID orders older than N days to `DELIVERED`; do not run automatically). Rollback: reverse mapping `PENDING_PAYMENT→PENDING`, `PROCESSING→CONFIRMED`, and any new-only status has no legacy equivalent (**data-loss risk if rolled back after new statuses exist**).
- `V21__order_status_history.sql`: `order_status_history(id, order_id FK CASCADE, old_status, new_status NOT NULL, changed_by FK users ON DELETE SET NULL, note VARCHAR(500), changed_at DEFAULT now())`, index `(order_id, changed_at)`; backfill **one row per existing order** (`old_status NULL`, `new_status` = mapped status, `note 'Migrated from legacy'`, `changed_at = orders.created_at`) guarded by `NOT EXISTS` so re-running does not duplicate.
- `V22__orders_price_breakdown.sql`: add `subtotal`, `discount_amount`, `shipping_fee`, `discount_code` as **nullable** → backfill → set `NOT NULL DEFAULT 0` where applicable (nullability concern: do not add NOT NULL before backfill). Indexes backed by real queries: `idx_orders_user_created (user_id, created_at DESC)`, `idx_orders_status_created (status, created_at DESC)`.
Empty DB: fine. Existing DB: fine. Deployment note for the report: stop the old app version before running V20 (the old code cannot read new statuses).

**API.** `/api/v1`, typed DTOs, paged lists; responses keep today's order fields and add `subtotal, discountAmount, shippingFee`, plus **server-computed `allowedNextStatuses`** (staff view) and `canCancel` (owner view) so the frontend never hard-codes the transition table.

| Method | Path | Auth |
|---|---|---|
| GET | `/api/v1/orders/my?page&size&status` | logged-in |
| GET | `/api/v1/orders/{id}` | owner or `order:view` |
| GET | `/api/v1/orders/{id}/history` | owner or `order:view` |
| POST | `/api/v1/orders/{id}/cancel` `{note}` | owner (only `PENDING_PAYMENT`) or `order:cancel` |
| GET | `/api/v1/admin/orders?status&keyword&from&to&page&size` | `order:view_all` |
| PATCH | `/api/v1/admin/orders/{id}/status` `{newStatus, note}` | `order:update` |
| POST | `/api/orders` (legacy checkout) | TEMP-COMPAT, kept |

Removed in this task: legacy `GET /api/orders`, `GET /api/orders/my`, `GET /api/orders/{id}`, `PUT /api/orders/{id}/status`; the frontend is migrated here. Invalid transition → 400 `BUSINESS_RULE_VIOLATION`; unknown status string → 400 `VALIDATION_ERROR`; not owner → 403 `FORBIDDEN`; missing → 404.

**Frontend (migrate in this task).** `orderApi.js` → v1 (lists return `{items, meta}`); new single module `frontend/src/utils/orderStatus.js` with labels/colours for the 10 statuses (+ a defensive label for unknown values) imported by `Orders.jsx`, `OrderDetail.jsx`, `AdminDashboard.jsx`, `SalesDashboard.jsx` (remove the three duplicated maps); `Orders.jsx` paginates; `OrderDetail.jsx`: use `canCancel`, add a minimal status timeline from `/history`, and keep the legacy "return" button logic working until B07 (it currently shows for `PAID`; show it for `DELIVERED`, and for legacy `PAID` only while decision D-4 default is active — state what you did); `Payment.jsx`: read `PENDING_PAYMENT` instead of `PENDING`; admin orders tab: use the paged admin endpoint, status filter/tabs from the new statuses, and render status-change options from `allowedNextStatuses` (server-driven). Do not redesign the admin page (B12).

**Security.** Ownership enforced in the service (never trust path ids); status values validated against the enum; staff-only transitions guarded by permission; every transition attributable (`changed_by`); system transitions use `NULL` actor; no client-supplied totals accepted by `createPendingOrder`.

**Compatibility.** Old statuses disappear from API responses; any client matching `'PENDING'/'CONFIRMED'` is updated here. Old paths removed except legacy `POST /api/orders` (removal condition: B04). Stock adapter removal condition: B03. `payNow` edit removal condition: B06.

**Efficiency review.** (a) Replace per-order `getItems` and lazy `product` access with one query (`findByOrderIdIn(ids)` joined to product, or `JOIN FETCH`) — report query counts for 20 orders before/after; (b) admin list must be paged (no `findAll`); (c) `orders-by-status` and revenue queries: check they use the new indexes (`EXPLAIN` if a DB is available, else say not verified); (d) do not load whole `User`/`Cart` graphs to render an order; (e) `OrderResponse` should expose `userId` without initialising the lazy proxy.

**Tests (mandatory).** `transition_table_exhaustive` (every ordered pair of the 10 statuses checked against the table), `transition_invalid_throwsBusinessRule` (`DELIVERED→PAID`, `CANCELLED→PAID`), `transition_sameStatus_isRejected`, `pendingPayment_toPaid_commitsInventory`, `pendingPayment_toCancelled_releasesInventory`, `paid_toCancelled_restocksNotReleases`, `cod_pendingPayment_toProcessing_allowed_nonCod_rejected`, `transition_everyCall_writesHistoryRowWithActor`, `transition_publishesStatusChangedEventAfterCommit`, `cancel_byNonOwner_isForbidden`, `cancel_byOwnerWhenPaid_isRejected`, `cancel_paidOrder_writesRefundRequiredNote`, `legacyCheckout_oneItemInsufficient_rollsBackOrderAndStock`, `legacyAdapter_concurrentReserveLastItem_exactlyOneSucceeds` (needs PostgreSQL; else report not executed), `revenueQueries_includeNewInProgressStatuses`, `revenueQueries_excludeCancelledReturnedRefunded`, `listMyOrders_paged_queryCountIndependentOfRows` (needs DB). Migration: provide a verification SQL script comment block (counts by status before/after) and report if it could not be executed. Frontend: lint + build; manual: place order (legacy checkout) → pay (mock) → see timeline; cancel a pending order → stock restored; admin invalid options not shown.

**Must NOT change.** Cart logic (B04), real payment gateway logic (B06), return logic (B07), product catalogue, auth model.

**ALLOWED**: `entity/Order*.java`, `entity/OrderStatusHistory.java`, order repositories/services/controllers/DTOs, `service/inventory/InventoryGateway.java` + legacy adapter, `event/Order*Event.java`, `repository/ProductRepository.java` (atomic update query only), `service/PaymentService.java` (the one `payNow` edit), `service/ReportService.java` + `OrderRepository` revenue queries (status filter only), migrations V20–V22, frontend files in Inspect + `utils/orderStatus.js`, tests, `docs/ai/contracts/B05-order.md`. **FORBIDDEN**: `CartService`, `ProductService` behaviour, `ReturnRequest*` logic (except keeping it compiling), auth.

**Definition of Done.** One method changes `orders.status`; table enforced and exhaustively tested; history written for every change and backfilled for old orders; cancel restores stock correctly in both branches; legacy checkout atomic with the conditional stock update; revenue dashboards unchanged in meaning; no N+1 on order lists; frontend uses `orderStatus.js` and server-driven options; contract doc lists the enum, table, side effects, `createPendingOrder`/`transitionStatus` signatures, `InventoryGateway`, events, and the TEMP-COMPAT removal conditions.

**Report.** the completion report in `CLAUDE.md` + (a) the legacy status counts before/after migration (or "not executed"), (b) the list of every code location that used to write `orders.status`, (c) query counts before/after for list endpoints.

---

### B03 — Inventory (reservation model, ledger, concurrency safety)

**Dependencies.** HARD: B05 (defines `InventoryGateway`, the legacy adapter and the order side effects). SOFT: B02 (`OUT_OF_STOCK` status is admin-set only; B03 does not auto-manage it). Replaces B05's TEMP-COMPAT adapter.

**Problem (current code, as of after B05).** Stock is one column, `products.stock_quantity`, adjusted through B05's legacy adapter. There is no distinction between *reserved* (held for an unpaid order) and *on hand*, no ledger of changes (nobody can answer "why is stock 3?"), no way to adjust stock with a reason, and no protection beyond a conditional `UPDATE`. `CartService` (three places), `ProductService.create/update`, `ProductResponse` and the admin dashboard read/write `stockQuantity` directly. Cancelled orders created **before** B05 never restored their stock (historical leak).

**Why it matters.** Overselling, untraceable stock changes, and a hidden double-count risk when the old column and the new model coexist.

**Required behaviour.**
1. Model: `quantity_on_hand` (physical), `reserved_quantity` (held by `PENDING_PAYMENT` orders). **Available = on_hand − reserved, computed at read time, never stored.** Invariants (DB CHECKs): `quantity_on_hand >= 0`, `reserved_quantity >= 0`, `reserved_quantity <= quantity_on_hand`.
2. Operations (`InventoryService implements InventoryGateway`, all `@Transactional`, all write one ledger row):
   `reserve` (reserved += q; fails with `InsufficientStockException` if available < q, checked **after** taking the row lock), `release` (reserved −= q), `commit` (on_hand −= q **and** reserved −= q), `restock` (on_hand += q; for cancel-after-commit and returned goods), `adjust(productId, delta, reason, actorUserId)` (on_hand += delta; **reason mandatory**; result must stay ≥ reserved), `available(productId)` and a **batch** `availableFor(Collection<Long>)`.
3. **Concurrency**: `InventoryRepository.findByProductIdForUpdate` (`PESSIMISTIC_WRITE`, with a lock-timeout hint). When one operation touches several products (an order with several items) **lock in ascending `productId` order** to prevent deadlocks between two orders that list the same items in opposite order.
4. **Idempotency**: ledger has a unique partial index so the same `(product, change_type, reference_type, reference_id)` for `RESERVE/RELEASE/COMMIT/RESTOCK` cannot be applied twice; a replay is a logged no-op (webhooks and retries must be safe).
5. **Single source of truth**: after this module nothing reads or writes `products.stock_quantity`. Replace every reader: `CartService` (add/update checks), `ProductResponse.stockQuantity` (= **available**, so the storefront keeps working), `ProductService.create` (creates the inventory row; initial stock → ledger `RESTOCK` "Initial stock"), `ProductService.update` (TEMP-COMPAT: if the form sends a `stockQuantity` different from on_hand, perform an `adjust` with reason `"Edited via product form"`; removal condition: B12 inventory page), the admin dashboard's low-stock logic (read the response value). Mark `Product.stockQuantity` `@Deprecated` and map it `insertable=false, updatable=false` so nothing can write it. **Do not mirror-write the old column.** Prove with `grep -rn "StockQuantity\|stock_quantity"` that no reader remains and include the grep result in the report.
6. Delete B05's legacy adapter bean (`InventoryGateway` now has exactly one implementation).
7. Admin APIs (`/api/v1/admin/inventory`): paged list (`lowStock`, `threshold`, `keyword`), per-product detail, per-product ledger (paged), `PATCH …/{productId}/adjust {quantityDelta, reason}`.

**Inspect (REQUIRED TO CHECK).** B05's `service/inventory/*`, `service/OrderService.java`, `service/CartService.java`, `service/ProductService.java`, `entity/Product.java`, `repository/ProductRepository.java`, `dto/ProductResponse.java`, `dto/CartItemResponse.java`, `controller/CartController.java`, `controller/ProductController.java` (or its B02 successor), `database/seed-data.sql` (inserts `stock_quantity`), frontend `pages/Cart.jsx`, `components/ProductCard.jsx`, `pages/ProductDetail.jsx`, `pages/admin/AdminDashboard.jsx` (stock form/low-stock), `context/CartContext.jsx`.

**Database.** (idempotent, header block per the migration policy in `CLAUDE.md`)
- `V30__inventory.sql`: `inventory(product_id PK FK→products ON DELETE CASCADE, quantity_on_hand INT NOT NULL DEFAULT 0, reserved_quantity INT NOT NULL DEFAULT 0, updated_at, CHECKs above)`. **Legacy conversion (the critical part)** — insert one row per existing product, guarded `ON CONFLICT DO NOTHING`:
  `reserved_quantity` = Σ quantity of `order_items` belonging to orders in `PENDING_PAYMENT`;
  `quantity_on_hand` = `products.stock_quantity` **+** that same sum.
  Reason: the legacy model (and B05's adapter) already subtracted stock for unpaid orders, so on-hand must be rebuilt to include goods that are merely reserved; otherwise a later `commit` double-deducts. Orders in committed states (`PAID`…`DELIVERED`, legacy-mapped `PROCESSING`) need no adjustment. Verify with a query that `reserved <= on_hand` for all rows before adding the CHECK.
- `V31__inventory_transactions.sql`: `inventory_transactions(id, product_id FK, change_type CHECK IN ('RESERVE','RELEASE','COMMIT','RESTOCK','ADJUST'), quantity INT NOT NULL, on_hand_after INT, reserved_after INT, reference_type VARCHAR(30), reference_id BIGINT, reason VARCHAR(255), actor_user_id BIGINT NULL FK users ON DELETE SET NULL, created_at)`; indexes `(product_id, created_at DESC)`; unique partial index for idempotency (excluding `ADJUST`); write one `ADJUST`-style opening row per product (`reason 'Migrated from products.stock_quantity'`), guarded by `NOT EXISTS`.
- **Historical leak, not auto-fixed**: provide (as a comment/diagnostic query in the migration header and the report) a query listing items of orders that were `CANCELLED` before B05's cutover so the owner can correct stock via `adjust`. **Never** auto-restock them: whether stock was already corrected by hand is unknowable.
Legacy impact: `products.stock_quantity` kept untouched (becomes a frozen historical value). Rollback: re-derive `stock_quantity = on_hand − reserved` per product (document the SQL; ledger rows are lost if the table is dropped). Empty DB: fine (no rows). Existing DB: fine.

**API.** `/api/v1/admin/inventory` (`inventory:view`), `…/{productId}/transactions` (`inventory:view`), `PATCH …/{productId}/adjust` (`inventory:adjust`). Public/customer responses: only `stockQuantity` (= available) and, if useful, `inStock`; **never** expose `reserved_quantity` publicly. Errors: `InsufficientStockException` → 400 `BUSINESS_RULE_VIOLATION` with the product name; adjust below reserved → 400.

**Frontend.** Product/cart/storefront shapes are unchanged (`stockQuantity` = available), so no page should break. Cart: show a clear message when an item's available quantity dropped below the cart quantity (use the server message). Admin product form keeps working via the TEMP-COMPAT adjust. The dedicated inventory UI is B12.

**Security.** Adjust requires `inventory:adjust` (or `requireAdmin` until B01-P3) and records the actor; reason is mandatory and length-limited; no negative-stock path; no endpoint lets a customer change stock.

**Compatibility.** Old stock column frozen; old cart/product code paths updated here; TEMP-COMPAT form-adjust removal condition: B12. Anything still reading `products.stock_quantity` (SQL scripts, reports) is **stale** after this module — list such places in the report.

**Efficiency review.** (a) Product list needs available quantities: one batched query (`availableFor(ids)`) — no per-product call; (b) cart read computes availability with one query; (c) `reserve` for an N-item order: N row locks, no repeated product loads; (d) index for low-stock listing only if the query needs it (expression `(quantity_on_hand - reserved_quantity)`), otherwise report seq-scan acceptable at current size; (e) keep lock hold-time short (no remote calls inside the locked section).

**Tests (mandatory; real PostgreSQL via Testcontainers, `disabledWithoutDocker = true`; report honestly if Docker is unavailable).** `reserve_insufficientAvailable_throws`, `reserve_concurrent_lastUnit_exactlyOneSucceeds` (two threads, available=1; **repeat ≥5 times**), `reserve_twoOrdersOppositeItemOrder_noDeadlock`, `commit_reducesOnHandAndReservedTogether`, `release_restoresAvailability`, `restock_increasesOnHand_andLogs`, `adjust_requiresReason`, `adjust_belowReserved_isRejected`, `everyOperation_writesLedgerRow_withAfterValues`, `replay_sameReference_isNoOp`, `migration_legacyPendingOrders_producesCorrectOnHandAndReserved`, `productCreate_createsInventoryRow`, `cart_usesAvailableNotOnHand`, `productResponse_stockQuantity_equalsAvailable`. Unit tests with mocks for logic that does not need locking.

**Must NOT change.** Order state machine/transitions (B05), payment, checkout orchestration (B04), catalogue fields other than the stock reads listed above.

**ALLOWED**: `service/inventory/*`, `entity/Inventory*.java`, inventory repositories/controllers/DTOs, minimal edits to `CartService`, `ProductService`, `Product`, `ProductResponse`, `OrderService`/legacy adapter removal, migrations V30–V31, tests, `docs/ai/contracts/B03-inventory.md`, minimal frontend edits listed. **FORBIDDEN**: `OrderService.transitionStatus` logic, payment, returns, auth.

**Definition of Done.** One implementation of `InventoryGateway`; zero readers/writers of `products.stock_quantity`; concurrency and deadlock tests pass (or are reported as not run with reason); every stock change has a ledger row; migration conversion verified; contract doc lists the operations, invariants, idempotency rule and the lock-ordering rule that B04/B07 must respect.

**Report.** the completion report in `CLAUDE.md` + the grep proof, the legacy-cancelled-orders diagnostic output (or "not executed"), and before/after query counts for product list and cart.

---

### B08 — Customer Management (addresses, enable/disable, admin user tools)

**Dependencies.** HARD: B01-P2 (refresh-token revocation, deactivated-user rejection). SOFT: B01-P3 (permissions), B05 (order history). B04 soft-depends on this module (address picker).

**Problem (current code).** `UserController`: `GET /api/users` returns every user unpaged (and `User.roles` is `EAGER` → N+1 on the list); `DELETE /api/users/{id}` hard-deletes after removing the cart — for a user with orders this fails on `fk_orders_user` (no cascade) and surfaces as an unmapped runtime error; `User.active` exists but nothing ever sets it to `false`, so accounts cannot be disabled; there is no address book (checkout takes free text); no way to assign roles; no per-customer order view for staff.

**Why it matters.** Staff cannot suspend abusive accounts; hard delete destroys referential history or fails unsafely; customers re-type addresses on every order.

**Required behaviour.**
1. **Addresses** (customer-owned): CRUD under `/api/v1/users/me/addresses`; max 10 per user; fields `recipientName, phone, line1, ward, district, province, isDefault`; exactly **one** default (service + partial unique index); first address becomes default; deleting the default promotes the most recent remaining one; phone validated with a lenient Vietnamese pattern; every `/{id}` operation verifies ownership.
2. **Admin user management** (`/api/v1/admin/users`): paged list (`keyword`, `role`, `active`); detail; `PATCH …/{id}/disable` and `…/enable`; `GET …/{id}/orders` (paged); `PATCH …/{id}/roles` (set of role names from the allowed set).
3. **Disable** sets `active=false`, **revokes all refresh tokens** (B01 `RefreshTokenService.revokeAllForUser`), and takes effect on the next API call because the interceptor rejects inactive users (B01-P2).
4. **Safety rules**: an admin cannot disable, delete or demote **themselves**; the **last active ADMIN** can never be disabled/deleted/demoted (409 `CONFLICT`).
5. **Delete**: allowed only when the user has **no orders or return requests** (else 409 `CONFLICT` "disable the account instead"); keep removing the cart first (existing behaviour).
6. `AddressService.getOwned(userId, addressId)` is the public signature B04 uses for the checkout address picker.

**Inspect.** `controller/UserController.java`, `service/UserService.java`, `entity/User.java` (`EAGER` roles), `repository/UserRepository.java`, `dto/UserResponse.java`, B01 `RefreshToken*` and `AuthInterceptor`, `repository/OrderRepository.java`, frontend `services/userApi.js`, `pages/Checkout.jsx` (read-only), `pages/admin/AdminDashboard.jsx` (users tab ≈ lines 880–960), `components/Navbar.jsx`, `App.jsx`.

**Database.** `V70__addresses.sql`: `addresses(id, user_id FK→users ON DELETE CASCADE, recipient_name VARCHAR(100) NOT NULL, phone VARCHAR(20) NOT NULL, line1 VARCHAR(255) NOT NULL, ward, district, province VARCHAR(100) NOT NULL, is_default BOOLEAN NOT NULL DEFAULT false, created_at, updated_at)`; index `(user_id)`; `CREATE UNIQUE INDEX … ON addresses(user_id) WHERE is_default`. Legacy impact: none (new table; no data to backfill — **do not** try to parse old order addresses into the book). Empty/existing DB: fine. Rollback: drop table (customer-entered data lost).

**API.**

| Method | Path | Auth |
|---|---|---|
| GET/POST | `/api/v1/users/me/addresses` | logged-in |
| PUT/DELETE | `/api/v1/users/me/addresses/{id}` | owner |
| PATCH | `/api/v1/users/me/addresses/{id}/default` | owner |
| GET | `/api/v1/admin/users` | `user:view` |
| GET | `/api/v1/admin/users/{id}` | `user:view` |
| GET | `/api/v1/admin/users/{id}/orders` | `user:view` |
| PATCH | `/api/v1/admin/users/{id}/disable` / `enable` | `user:disable` |
| PATCH | `/api/v1/admin/users/{id}/roles` | `user:update` |
| DELETE | `/api/v1/admin/users/{id}` | `user:disable` |

Legacy `GET /api/users`, `DELETE /api/users/{id}` removed after the admin Users tab is migrated here.

**Frontend.** New `services/addressApi.js`; new page `pages/Addresses.jsx` (list/create/edit/delete/set default) with route `/account/addresses` (inside `ProtectedRoute`) and one link in `Navbar.jsx`; admin Users tab (in `AdminDashboard.jsx`, users section only): use the paged endpoint, add Enable/Disable, show the "has orders → disable instead" error, keep the role filter (value `CUSTOMER`). No checkout changes here (B04 consumes addresses).

**Security.** Ownership enforced in `AddressService` (never trust ids); admin permissions server-side; role changes restricted to the three known roles; no password hash in any response; disabling is audited in logs (actor id, target id) — a dedicated audit table is out of scope.

**Compatibility.** Old `GET /api/users` shape changes to the paged envelope (frontend migrated here). Users with orders can no longer be hard-deleted (previously a crash) — intentional; report it.

**Efficiency review.** The user list must not trigger N+1 for roles (batch/`@EntityGraph`; no collection fetch combined with `Pageable` — page ids first, then load roles with `IN`); order history paged with items fetched in batch; address list returns small DTOs.

**Tests (mandatory).** `address_update_byNonOwner_isForbidden`, `address_delete_byNonOwner_isForbidden`, `address_firstBecomesDefault`, `address_setDefault_leavesExactlyOneDefault`, `address_deleteDefault_promotesAnother`, `address_eleventh_isRejected`, `disable_revokesAllRefreshTokens`, `disabledUser_cannotCallApi` (interceptor integration), `admin_cannotDisableOrDemoteSelf`, `lastActiveAdmin_cannotBeDisabledDeletedOrDemoted`, `deleteUser_withOrders_returns409`, `deleteUser_withoutOrders_succeeds`, `setRoles_unknownRole_isRejected`, `userList_queryCountIndependentOfRows` (DB needed), `adminUserOrders_paged`. Frontend: lint + build; manual: manage addresses; disable/enable a user and confirm they are rejected/accepted.

**Must NOT change.** Checkout logic, auth token logic (use B01's service), order/payment code.

**ALLOWED**: `entity/Address.java`, address repository/service/controller/DTOs, `UserController`/`UserService`/`UserRepository` (+ `UserResponse` additive), migration V70, frontend files in Inspect + `Addresses.jsx`/`addressApi.js`, tests, `docs/ai/contracts/B08-customer.md`. **FORBIDDEN**: `OrderService`, `CartService`, `PaymentService`, B01 internals (call them, do not edit them; request changes in the contract doc).

**Definition of Done.** Address book works with the one-default invariant; accounts can be disabled/enabled with immediate effect and token revocation; last-admin and self-lockout rules enforced; unsafe hard-delete replaced; admin list paged without N+1; contract lists `AddressService` signatures and the `Address` shape for B04/B11.

---

### B04 — Cart & Checkout (discounts, shipping, address, safe order placement)

**Dependencies.** HARD: **B05** (`createPendingOrder`, `PENDING_PAYMENT`, `InventoryGateway`), B01-F1. SOFT: B03 (real inventory — before B03, B05's adapter is used through the same port), B08 (`addressId`; if absent, free-text only), B02. Removes B05's legacy `POST /api/orders` and B05-era `OrderService.checkout`.

**Problem (current code).** The cart is correct in spirit (server-side, no stored prices) — **preserve that**. But: checkout is `OrderService.checkout` on the legacy `/api/orders`; it has no discount, no shipping fee, no address selection; `CartController` uses `Map` bodies and returns bare lists; stock checks were read-then-write; `CartContext`/`Cart.jsx`/`Checkout.jsx` compute and display a single total; two simultaneous checkouts of the same cart could both proceed (duplicate orders); cart item rows are read with lazy product access.

**Why it matters.** Revenue-affecting rules (discount, shipping) must be computed only on the server; double submits and partial failures must never create inconsistent orders/reservations.

**Required behaviour.**
1. **`CheckoutService.checkout(userId, CheckoutRequest)`** (new; `OrderService` keeps only state/history). One `@Transactional` unit, in this order: (1) lock the user's cart row (`PESSIMISTIC_WRITE`) — a concurrent second checkout waits, then finds an empty cart → 400; (2) load cart items **with** products in one query; reject empty cart; (3) per item: product must be `ACTIVE`, quantity > 0, **price read from the DB**, early friendly check `available >= quantity`; (4) `subtotal = Σ price × qty`; (5) if `discountCode` supplied: validate (exists, `active`, within `valid_from/valid_to`, `min_order_amount`), `discountAmount` never exceeds `subtotal`, percent discounts rounded to whole VND (`HALF_UP`); (6) `shippingFee` from configuration (`ecogreen.shipping.flat-fee`, `ecogreen.shipping.free-threshold`; defaults 30000 / 500000 — open decision D-10; env overrides `SHIPPING_FLAT_FEE`, `FREE_SHIPPING_THRESHOLD`); (7) `total = subtotal − discount + shipping` (`BigDecimal`, scale 2); (8) `orderService.createPendingOrder(...)` (B05) with the snapshot of recipient name/phone/address text; (9) `InventoryGateway.reserve` for each item **in ascending productId order**; any `InsufficientStockException` rolls back everything including the order; (10) create `Payment(PENDING)` with a validated method (`COD`, `VIETQR`, `MOMO` today; B06 adds more) via `PaymentService.createPendingPayment(order, method)` — a minimal TEMP-COMPAT method added to `PaymentService` here (owner B06; the existing creation code moves there); (11) clear cart items **last**.
2. **Never trust the client** for price, totals, discount value, shipping, status or user id; extra JSON fields are ignored (typed DTO).
3. **Address**: request carries `addressId` (B08; must belong to the caller — else 403) **or** free-text `customerName/customerPhone/shippingAddress`. Whichever is used is **snapshotted** into the order (orders never reference the address book).
4. **Preview**: `POST /api/v1/checkout/preview` returns `{items[{productId,name,unitPrice,quantity,lineTotal,available}], subtotal, discountAmount, shippingFee, total, issues[]}` with **no side effects** (no reservation, no writes) so the UI can show the server's numbers.
5. **Cart API** moves to `/api/v1/cart`: `GET`, `POST /items`, `PATCH /items/{id}`, `DELETE /items/{id}`, `DELETE /` (clear). Responses include `availableQuantity` per item. Ownership checked on every item operation (existing `ensureOwnership` stays).
6. **Discounts**: table + service; **admin API only** (`/api/v1/admin/discounts`, permission `discount:view|manage`, or `requireAdmin` until B01-P3); no admin UI here (B12). No stored cart-level discount state (code is supplied at preview/checkout) — this deliberately drops V1's `carts.discount_code`.
7. Publish `OrderPlacedEvent(orderId, userId, total)` after commit (B09/B10 consume). Remove legacy `POST /api/orders`, legacy `/api/cart/*`, and `OrderService.checkout`.

**Inspect.** `controller/CartController.java`, `service/CartService.java`, `dto/CartResponse.java`, `dto/CartItemResponse.java`, `repository/Cart*Repository.java`, B05 `OrderService.createPendingOrder`, `controller/OrderController.java` (legacy checkout), `service/PaymentService.java`, `entity/Cart*.java`, frontend `context/CartContext.jsx`, `services/cartApi.js`, `orderApi.js`, `pages/Cart.jsx`, `pages/Checkout.jsx`, `pages/Payment.jsx`, `components/ProductCard.jsx` (add-to-cart), `ProductDetail.jsx`, `Navbar.jsx` (cart badge).

**Database.** `V40__discounts.sql`: `discounts(id, code VARCHAR(30) NOT NULL, percent_off SMALLINT CHECK 1..100, amount_off DECIMAL(12,2) CHECK > 0, min_order_amount DECIMAL(12,2), valid_from TIMESTAMP, valid_to TIMESTAMP, active BOOLEAN NOT NULL DEFAULT true, created_at)`, `CHECK` exactly one of `percent_off`/`amount_off`, unique index on `upper(code)` (codes are case-insensitive; store upper-case). Legacy impact: none (new table). Orders already carry the breakdown columns (B05 V22). Empty/existing DB: fine. Rollback: drop table (discounts lost; historical orders keep `discount_code` text).

**API.** As in Required behaviour 4–6; checkout `POST /api/v1/checkout` request `{addressId | {customerName, customerPhone, shippingAddress}, discountCode?, paymentMethod}` → 201 `{data: OrderResponse}`. Errors: empty cart 400 `BUSINESS_RULE_VIOLATION`; insufficient stock 400 with item name; bad discount 400 `BUSINESS_RULE_VIOLATION` (do not reveal whether a code exists vs expired beyond a generic "invalid or expired"); foreign address 403.

**Frontend (migrate in this task).** `cartApi.js`/`orderApi.js`/`CartContext.jsx` → v1; `Cart.jsx` shows availability warnings; `Checkout.jsx`: address picker when `/users/me/addresses` exists (else the current free-text form), discount-code field, **server-provided** breakdown rows (subtotal / discount / shipping / total) from `/checkout/preview`, submit button disabled while the request is in flight (double-submit guard), item-specific error display, then navigate to `/payment/:orderId` as today. Fix duplicate cart fetches (see Efficiency). Remove any client-side total computation that is used for anything other than display.

**Security.** Server-side price/total/discount/shipping only; discount codes validated case-insensitively with no enumeration hints; ownership on cart items and address; checkout rate: the cart row lock prevents duplicate orders; no secrets involved.

**Compatibility.** `POST /api/orders` and `/api/cart/*` removed (frontend migrated here). **Totals change for customers** when shipping is enabled (documented as D-10). Legacy orders keep their stored totals untouched. Compat code removed in this task: B05 legacy checkout.

**Efficiency review.** (a) Cart read: one query for items+products (+ availability batch), no lazy access in `CartItemResponse`; (b) checkout: items+products in one query, no per-item `findById`; (c) frontend `CartContext`: report if multiple components fetch the cart independently or refetch on every route change; fetch once, update from mutation responses, and derive the badge count from state; (d) preview called on change, debounced, not on every keystroke; (e) no `Thread.sleep`/polling.

**Tests (mandatory).** `checkout_secondItemInsufficient_rollsBackOrderReservationAndKeepsCart`, `checkout_concurrentSameCart_createsOneOrder`, `checkout_emptyCart_isRejected`, `checkout_inactiveProduct_isRejected`, `checkout_ignoresClientSuppliedPriceAndTotal`, `discount_percent_rounding`, `discount_amount_neverExceedsSubtotal`, `discount_expiredInactiveOrBelowMinimum_isRejected`, `shipping_belowThreshold_addsFee_aboveThreshold_free`, `checkout_addressOfAnotherUser_isForbidden`, `checkout_reservesItemsInAscendingProductOrder`, `preview_hasNoSideEffects`, `cart_itemOwnership_enforced`, `cart_response_includesAvailableQuantity`, `checkout_publishesOrderPlacedEventAfterCommit`. Concurrency tests need PostgreSQL (Testcontainers, `disabledWithoutDocker`); report if not executed. Frontend: lint + build; manual: add to cart → preview → apply code → place order → see payment page.

**Must NOT change.** Order state machine (B05), real payment (B06), stock model (B03), auth.

**ALLOWED**: `service/CheckoutService.java`, `controller/Checkout*.java`, `controller/CartController.java`, `service/CartService.java`, cart DTOs/repositories, `entity/Discount.java` + repository/service/admin controller, `service/PaymentService.java` (only `createPendingPayment`), `controller/OrderController.java` (remove legacy checkout), `OrderService` (remove legacy checkout only), `event/OrderPlacedEvent.java`, migration V40, `application.properties` (shipping props), `.env.example`, frontend files in Inspect, tests, `docs/ai/contracts/B04-checkout.md`. **FORBIDDEN**: `transitionStatus` logic, `InventoryService` internals, payment gateway, returns.

**Definition of Done.** Checkout is atomic and idempotent per cart; totals/discount/shipping computed only on the server and shown from `/checkout/preview`; legacy checkout and cart paths removed with frontend migrated; discounts manageable via admin API; contract doc lists `CheckoutRequest`/`CheckoutPreview`/`OrderResponse` shapes and the config properties.

## 6. Module drafts that have no V2 specification (B06, B07, B09–B13)

> **These were written in the first (V1) round, in Vietnamese, before the code was audited. Treat them as drafts of the
> required behaviour, not as ready-to-run prompts.** Before using one, apply these overrides (they come from the current code
> and from `CLAUDE.md`, and win over the text below):
>
> 1. **Paths.** `modules/**`, `apps/web/**` and `modules/*/CONTRACT.md` do not exist. Use the real layout
>    (`backend/src/main/java/com/example/backend/<layer>/`, `frontend/src/`) and publish the contract at
>    `docs/ai/contracts/<module>.md`.
> 2. **Migrations.** Flyway is not active: files are idempotent, carry the mandatory header, stay inside the module's range
>    (section 3) and are applied by hand in dependency order. Any step that activates Flyway or switches `ddl-auto` to
>    `validate` belongs to B13 and happens only when that task is assigned.
> 3. **Money** is `DECIMAL(12,2)` / `BigDecimal`. The "BIGINT smallest unit" convention was withdrawn.
> 4. **API.** `/api/v1`, `{data, meta}` envelope, closed error-code set, 0-based pagination — see `CLAUDE.md`.
> 5. **Authorization.** Use `requirePermission` with `Permissions.java` constants; no permission string literals.
> 6. **Order status and stock** follow the B05/B03 specifications above (state machine, `InventoryGateway`, release vs restock);
>    do not reintroduce the V1 behaviour listed in section 2.1.
> 7. **Events.** Each event class is owned by the publishing module (`com.example.backend.event`); B09/B10 only consume.
> 8. **Frontend.** B11/B12 are slices: each is done together with the backend module whose UI it changes, not at the end.
> 9. **B13 infra.** The Dockerfile snippets use `npm ci`, which currently fails because `frontend/package-lock.json` is out of
>    sync with `package.json` (missing `@emnapi/*`); repair the lockfile first. Redis/RabbitMQ only if a measured need exists.

### B06 — Payment (Cổng thanh toán thật)

**Bối cảnh**: `PaymentService` hiện tại mô phỏng 100% (`payNow()` luôn trả `SUCCESS` ngay lập tức). Bạn tích hợp cổng thật. **Khuyến nghị dùng VNPay sandbox** (phổ biến ở VN, tài liệu tiếng Việt đầy đủ, miễn phí test) — nếu bạn chọn cổng khác (MoMo/Stripe), giữ nguyên interface bên dưới, chỉ đổi phần implementation gọi API.

**Phụ thuộc**: B05 (`OrderService.transitionStatus()`).

#### Migration `V50__payment_gateway_fields.sql`
```sql
ALTER TABLE payments
    ADD COLUMN provider VARCHAR(30),
    ADD COLUMN raw_response TEXT;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED'));
```

#### Chữ ký hàm
```java
public interface PaymentGatewayClient {
    PaymentInitiationResult initiate(Order order, String returnUrl);  // trả về URL redirect sang cổng
    WebhookVerificationResult verifyAndParse(Map<String,String> rawParams); // verify chữ ký HMAC
}

// PaymentService
PaymentInitiationResult initiatePayment(Long orderId);
void handleWebhook(Map<String,String> rawParams);  // idempotent: nếu transaction_id đã xử lý rồi thì bỏ qua, không xử lý 2 lần
Payment getByOrderId(Long orderId);
```

**`handleWebhook()` BẮT BUỘC làm đúng thứ tự** (đây là endpoint public, không qua `AuthGuard`, nên phải tự bảo vệ bằng chữ ký):
1. `gatewayClient.verifyAndParse(rawParams)` — nếu chữ ký sai, trả `400` ngay, **không** đụng gì vào DB.
2. Tìm `Payment` theo `transactionId`; nếu đã có `status != PENDING` → trả `200 OK` luôn (idempotent — cổng thanh toán hay gọi lại webhook nhiều lần).
3. Nếu thành công: `payment.status = SUCCESS`, `paidAt = now()`, gọi `orderService.transitionStatus(orderId, PAID, null, "Auto by payment webhook")`.
4. Nếu thất bại: `payment.status = FAILED`, gọi `orderService.transitionStatus(orderId, CANCELLED, null, "Payment failed")` (giải phóng tồn kho qua hiệu ứng phụ đã có ở B05).

#### API
| Method | Path | Body | Permission |
|---|---|---|---|
| POST | `/api/v1/payments/{orderId}/initiate` | `{returnUrl}` | chủ đơn |
| GET/POST | `/api/v1/payments/webhook/{provider}` | tham số tuỳ cổng (VNPay dùng query param GET) | **Public, tự verify chữ ký, KHÔNG qua AuthGuard** |
| GET | `/api/v1/payments/{orderId}` | — | chủ đơn HOẶC `payment:view` |

#### Test bắt buộc
- `handleWebhook_invalidSignature_rejectsWithoutDbChange()`
- `handleWebhook_duplicateTransactionId_isIdempotent()` — gọi webhook 2 lần cùng `transactionId`, assert `transitionStatus` chỉ được gọi 1 lần.
- `handleWebhook_success_transitionsOrderToPaid()`

#### Definition of Done
- [ ] Secret/API key cổng thanh toán đọc từ biến môi trường (`PAYMENT_VNPAY_SECRET`...), có trong `.env.example`
- [ ] `modules/payment/CONTRACT.md` công bố `PaymentStatus` enum cho B07 (refund) dùng

---

### B07 — Return & Refund

**Bối cảnh**: `ReturnRequest` hiện có (`reason/description/imageUrl/status/adminNote`) khá gần yêu cầu nhưng chỉ hỗ trợ 1 ảnh, không có `return_items` (trả từng sản phẩm riêng), duyệt xong không tự hoàn tiền/hoàn kho. Bạn hoàn thiện domain này.

**Phụ thuộc**: B03 (`InventoryService.restock()`), B05 (`OrderService.transitionStatus()`, chỉ cho phép tạo return khi order đang `DELIVERED`), B06 (gọi refund qua gateway nếu có, hoặc đánh dấu hoàn tiền thủ công).

#### Migration `V60__returns_full.sql`
```sql
ALTER TABLE return_requests RENAME TO returns;
ALTER TABLE returns ADD COLUMN status_new VARCHAR(20);
UPDATE returns SET status_new = status;  -- giữ nguyên PENDING/APPROVED/REJECTED
ALTER TABLE returns DROP COLUMN status;
ALTER TABLE returns RENAME COLUMN status_new TO status;
ALTER TABLE returns ADD CONSTRAINT chk_returns_status
    CHECK (status IN ('PENDING','INFO_REQUESTED','APPROVED','REJECTED','RETURNED','REFUNDED'));

CREATE TABLE return_items (
    id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL REFERENCES order_items(id),
    quantity INT NOT NULL CHECK (quantity > 0)
);
CREATE TABLE return_images (
    id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);
-- Di chuyển ảnh đơn lẻ cũ sang bảng mới
INSERT INTO return_images (return_id, image_url, sort_order)
    SELECT id, image_url, 0 FROM returns WHERE image_url IS NOT NULL;

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    return_id BIGINT REFERENCES returns(id),
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    provider_transaction_id VARCHAR(100),
    refunded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

#### Chữ ký hàm
```java
ReturnRequestDto create(Long userId, Long orderId, String reason, String description,
                         List<MultipartFile> images, List<ReturnItemDraft> items);
                         // throws BusinessRuleViolationException nếu order.status != DELIVERED

ReturnRequestDto updateStatus(Long returnId, ReturnStatus newStatus, String adminNote, Long changedByUserId);
```

**Hiệu ứng phụ bắt buộc trong `updateStatus()`**:
- `-> APPROVED`: không làm gì thêm ngoài đổi status (chờ xử lý vật lý nhận hàng trả về).
- `-> RETURNED` (hàng đã thật sự về kho): với mỗi `return_item`, gọi `inventoryService.restock(productId, quantity, "RETURN", returnId)`; gọi `orderService.transitionStatus(orderId, RETURNED, ...)`.
- `-> REFUNDED`: tạo `Refund` record (`status=PENDING`), gọi `paymentGatewayClient` hoàn tiền nếu cổng hỗ trợ hoàn tự động, nếu không hỗ trợ thì để `status=PENDING` và admin tự xử lý thủ công ngoài hệ thống rồi gọi 1 endpoint riêng xác nhận; gọi `orderService.transitionStatus(orderId, REFUNDED, ...)`.

#### API
| Method | Path | Body | Permission |
|---|---|---|---|
| POST | `/api/v1/returns` | multipart: `{orderId, reason, description, items[], images[]}` | requireUser |
| GET | `/api/v1/returns/my` | — | requireUser |
| GET | `/api/v1/admin/returns?status=&page=&size=` | — | `return:view` |
| PATCH | `/api/v1/admin/returns/{id}/status` | `{newStatus, adminNote}` | `return:process` |

#### Test bắt buộc
- `create_whenOrderNotDelivered_throwsBusinessRuleViolation()`
- `updateStatus_toReturned_restocksInventoryForEachItem()`
- `updateStatus_toRefunded_createsRefundRecord()`

#### Definition of Done
- [ ] `modules/return/CONTRACT.md` công bố shape `ReturnRequestDto`

---

### B09 — Analytics Events & Dashboard mở rộng

**Bối cảnh**: Không có bảng `analytics_events`. `ReportService`/`StatsController` hiện chỉ có doanh thu/top-sản-phẩm cơ bản.

**Nguyên tắc quan trọng**: block này **chỉ đọc**, không bao giờ ghi ngược vào bảng của B02/B05/B06/B07. Để nhận biết sự kiện xảy ra ở các module khác mà không tạo phụ thuộc ngược, dùng `ApplicationEventPublisher` của Spring — các block khác publish event (xem danh sách ở dưới), bạn chỉ lắng nghe.

#### Migration `V80__analytics_events.sql`
```sql
CREATE TABLE analytics_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(100),
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_analytics_events_type_created ON analytics_events(event_type, created_at);
```

#### Chữ ký hàm
```java
// AnalyticsEventService — KHÔNG BAO GIỜ ném exception ra ngoài (wrap try/catch, chỉ log lỗi)
// để lỗi ghi analytics không bao giờ làm fail luồng nghiệp vụ chính gọi nó.
void track(String eventType, Long userId, String sessionId, Map<String, Object> payload);

@EventListener
void onOrderCreated(OrderCreatedEvent event);     // event_type = "order_created"
@EventListener
void onPaymentSuccess(PaymentSuccessEvent event); // event_type = "payment_success"
@EventListener
void onOrderCancelled(OrderCancelledEvent event);
@EventListener
void onReturnCreated(ReturnCreatedEvent event);
```

**Danh sách event class cần có** (đặt ở package dùng chung `com.example.backend.event`, đây là "hợp đồng chia sẻ" — B05/B06/B07/B02/B04 publish, bạn và B10 cùng lắng nghe, không block nào khác được sửa các class event này nếu không có sự đồng thuận):
```
ProductViewedEvent(productId, userId)
AddToCartEvent(productId, userId, quantity)
CheckoutStartedEvent(userId, cartId)
OrderCreatedEvent(orderId, userId, total)
PaymentSuccessEvent(orderId, amount)
OrderCancelledEvent(orderId, reason)
ReturnCreatedEvent(returnId, orderId)
```
Các block B02 (view sản phẩm), B04 (add to cart, checkout started), B05 (order created/cancelled), B06 (payment success), B07 (return created) chịu trách nhiệm **tự publish** đúng event tương ứng tại đúng điểm nghiệp vụ của mình bằng `applicationEventPublisher.publishEvent(new XxxEvent(...))` — không phải việc của block B09.

#### API mở rộng `ReportService`/`StatsController`
```java
Page<InventoryView> getLowStockProducts(int threshold, Pageable pageable);  // gọi InventoryService
long getPendingOrdersCount();
BigDecimal getTotalRefundAmount(LocalDate from, LocalDate to);
List<CustomerFrequency> getCustomerPurchaseFrequency(Pageable pageable);
```
| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/admin/analytics/events?type=&from=&to=&page=&size=` | `analytics:view` |
| GET | `/api/v1/admin/reports/low-stock` | `analytics:view` |
| GET | `/api/v1/admin/reports/pending-orders-count` | `analytics:view` |
| GET | `/api/v1/admin/reports/refund-summary?from=&to=` | `analytics:view` |
| GET | `/api/v1/admin/reports/customer-frequency` | `analytics:view` |

#### Test bắt buộc
- `track_whenDbFails_doesNotThrow()` — mock repository ném exception, assert `track()` vẫn return bình thường, chỉ log lỗi.

#### Definition of Done
- [ ] `modules/analytics/CONTRACT.md` công bố toàn bộ 7 event class ở package `event` cho các block khác publish đúng chữ ký

---

### B10 — Notification

**Bối cảnh**: Không có bất kỳ cơ chế gửi email/SMS nào. Bạn xây tầng này, lắng nghe cùng bộ event như B09 (không phụ thuộc B09, chỉ dùng chung package `event`).

**Lưu ý kiến trúc**: bản đầu dùng `@Async` + `ApplicationEventPublisher` trong-process (đơn giản, đủ cho academic/production nhỏ). Khi B13 dựng xong RabbitMQ (Phase 6 theo roadmap), có thể nâng cấp thành consumer thật mà không đổi chữ ký `NotificationService.send()` — ghi rõ điều này trong `CONTRACT.md` của bạn.

#### Migration `V90__notifications.sql`
```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,         -- 'ORDER_CREATED','PAYMENT_SUCCESS','RETURN_APPROVED'...
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

#### Chữ ký hàm
```java
@Async
void send(Long userId, NotificationType type, Map<String, Object> templateContext);

@EventListener
void onOrderCreated(OrderCreatedEvent event);       // gửi email "Đặt hàng thành công"
@EventListener
void onPaymentSuccess(PaymentSuccessEvent event);   // gửi email "Thanh toán thành công"
@EventListener
void onReturnStatusChanged(ReturnStatusChangedEvent event); // cần B07 publish thêm event này
```

Cấu hình bắt buộc: bật `@EnableAsync` ở main application class (nếu chưa bật — kiểm tra trước, không bật trùng 2 nơi); dùng `JavaMailSender` (Spring Boot starter mail) với SMTP config đọc từ `SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD` (biến môi trường, thêm vào `.env.example`). Nếu SMTP chưa cấu hình (môi trường dev/test), `send()` phải log ra console thay vì ném exception (không được làm fail toàn bộ đơn hàng chỉ vì gửi mail lỗi — đây là yêu cầu SRS "không chặn request quan trọng").

#### API
| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/users/me/notifications?page=&size=` | requireUser |
| GET | `/api/v1/admin/notifications?status=&page=&size=` | `analytics:view` (xem log gửi, phục vụ debug) |

#### Test bắt buộc
- `send_whenSmtpFails_doesNotThrowAndMarksFailed()`
- `onOrderCreated_createsNotificationRowWithCorrectTemplate()`

#### Definition of Done
- [ ] `modules/notification/CONTRACT.md` ghi rõ danh sách `NotificationType` hỗ trợ và template tương ứng

---

### B11 — Frontend Storefront (React/Vite)

**Bối cảnh**: Frontend hiện tại (`frontend/src/`) đã có cấu trúc đúng (`context/`, `services/`, `pages/`, `components/`) — giữ nguyên, mở rộng thêm. Thay đổi lớn nhất: chuyển từ token đơn giản sang access+refresh token (B01), và API giờ trả envelope `{data, meta}`/`{error}` thay vì trả thẳng object.

#### Việc cần làm
1. **`services/http.js`**: sửa interceptor response để tự unwrap `response.data.data` (envelope mới); interceptor lỗi đọc `error.response.data.error.code` để hiện đúng message; thêm interceptor: khi nhận lỗi `401` với `error.code === 'UNAUTHENTICATED'`, tự gọi `POST /api/v1/auth/refresh` bằng refresh token lưu sẵn, nếu thành công thì gắn access token mới và **retry lại đúng 1 lần** request gốc; nếu refresh cũng fail thì mới điều hướng `/login`.
2. **`context/AuthContext.jsx`**: lưu thêm `permissions: string[]` (từ `/api/v1/auth/me`), expose hook `hasPermission(code)`.
3. **Trang catalog (`Home.jsx`)**: đổi từ lọc client-side sang gọi API `GET /api/v1/products?keyword=&categoryId=&minPrice=&maxPrice=&page=&size=` thật — xoá toàn bộ logic filter bằng JS trên mảng đã tải hết.
4. **`ProductDetail.jsx`**: hiển thị gallery nhiều ảnh (`product.images[]` thay vì 1 ảnh), hiển thị `comparePrice` (giá gạch ngang) nếu có.
5. **Trang mới `Addresses.jsx`**: CRUD địa chỉ giao hàng (gọi API B08), chọn địa chỉ mặc định lúc checkout thay vì nhập tay.
6. **`Checkout.jsx`**: thêm ô nhập `discountCode`, hiển thị rõ 3 dòng `Tạm tính / Giảm giá / Phí ship / Tổng cộng` (không gộp chung 1 số như hiện tại).
7. **`OrderDetail.jsx`**: hiển thị timeline trạng thái đơn (gọi `GET /api/v1/orders/:id/history`), nút "Huỷ đơn" chỉ hiện khi `status` nằm trong tập cho phép huỷ (lấy đúng theo bảng transition ở B05, không tự đoán).
8. **Form tạo return request**: cho chọn nhiều ảnh (input `multiple`), chọn từng sản phẩm trong đơn muốn trả (không phải trả cả đơn như hiện tại).

#### Định dạng response mới cần biết khi viết code gọi API
```js
// Thành công — luôn unwrap .data trong http.js, code trong page component nhận thẳng giá trị thật
// Lỗi — http.js ném Error với .code và .message lấy từ error.code/error.message
try {
  await orderApi.cancel(orderId, note);
} catch (err) {
  if (err.code === 'BUSINESS_RULE_VIOLATION') { /* hiện message cụ thể */ }
}
```

#### Definition of Done
- [ ] Không còn bất kỳ logic lọc sản phẩm bằng JS thuần trên mảng đầy đủ
- [ ] Refresh token tự động hoạt động, test thủ công: đợi access token hết hạn (đặt `JWT_SECRET` TTL ngắn lúc test), gọi 1 API, xác nhận tự refresh mà không bị văng ra `/login`

---

### B12 — Frontend Admin (CMS)

**Phụ thuộc**: toàn bộ API block B01–B10 cần có `CONTRACT.md` ổn định trước khi hoàn thiện UI tương ứng (có thể bắt đầu UI sớm bằng mock response theo đúng shape trong `CONTRACT.md`).

#### Việc cần làm
1. **Permission-based rendering**: tạo hook `usePermission(code)` đọc từ `AuthContext.permissions`; mọi nút hành động nhạy cảm (xoá sản phẩm, duyệt return, đổi trạng thái đơn) **phải** bọc điều kiện `hasPermission('product:delete')` — không hiện nút nếu thiếu quyền, dù vẫn phải hiểu đây chỉ là UX, backend đã tự chặn.
2. **Trang quản lý sản phẩm**: form tạo/sửa có đủ field mới (`slug` tự sinh từ `name` nhưng cho sửa tay, `sku`, `brand`, `comparePrice`), upload nhiều ảnh kéo-thả sắp xếp `sortOrder`, nút "Publish" riêng (gọi API publish, hiện lỗi rõ ràng nếu thiếu field bắt buộc).
3. **Trang quản lý category**: hiển thị dạng cây (thu gọn/mở rộng theo `parent_id`), kéo-thả đổi cha (tối thiểu: dropdown chọn category cha khi tạo/sửa).
4. **Trang Inventory mới**: danh sách tồn kho, lọc "sắp hết hàng", nút điều chỉnh thủ công (`ADJUST`) yêu cầu nhập `reason` bắt buộc, xem lịch sử transaction của 1 sản phẩm.
5. **Trang Orders**: hiển thị đúng 10 trạng thái mới, nút đổi trạng thái chỉ hiện các lựa chọn **hợp lệ theo bảng transition** (gọi API hoặc hard-code đúng bảng ở B05 phía frontend để disable nút sai, backend vẫn là chốt chặn cuối).
6. **Trang Returns/Refunds**: duyệt/từ chối, xem ảnh (gallery nhiều ảnh), xem trạng thái refund liên kết.
7. **Trang Analytics mở rộng**: thêm card "Low stock", "Pending orders", "Refund amount tháng này", "Tần suất mua hàng theo khách" (dùng API B09).
8. **Trang Customer Management**: nút Enable/Disable tài khoản, xem lịch sử đơn của khách ngay trong trang chi tiết khách hàng.

#### Definition of Done
- [ ] Không có bất kỳ nút hành động nào hiện ra cho user thiếu permission tương ứng (kiểm tra bằng cách đăng nhập tài khoản MANAGER thiếu 1 permission cụ thể, xác nhận đúng nút biến mất)

---

### B13 — Infra, CI/CD, Observability, Testing Framework

**Đây là block duy nhất chạy độc lập hoàn toàn ngay từ ngày đầu, không chờ block nào.**

#### Việc cần làm

1. **Flyway**: thêm dependency `org.flywaydb:flyway-core` + `flyway-database-postgresql`, chuyển `database/init-postgres.sql` hiện có thành `backend/src/main/resources/db/migration/V1__baseline.sql` (copy nguyên văn, không sửa nội dung), xoá cơ chế Hibernate `ddl-auto` nếu đang bật (đổi `spring.jpa.hibernate.ddl-auto=validate` để Hibernate không tự ý đổi schema nữa — Flyway là nguồn chân lý duy nhất).

2. **Dockerfile backend** (`backend/Dockerfile`, multi-stage):
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
```

3. **Dockerfile frontend** (`frontend/Dockerfile`, build tĩnh + Nginx):
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

4. **`docker-compose.yml` đầy đủ** (mở rộng file hiện có, không viết đè — chỉ thêm service): `postgres` (giữ nguyên), thêm `redis:7-alpine` (port 6379), thêm `rabbitmq:3-management-alpine` (port 5672 + UI 15672), thêm service `backend` (build từ Dockerfile, depends_on postgres/redis/rabbitmq, đọc `.env`), thêm service `frontend` (build từ Dockerfile, depends_on backend).

5. **GitHub Actions** (`.github/workflows/ci.yml`):
```yaml
name: CI
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: cd backend && mvn -B test
      - run: cd backend && mvn -B package -DskipTests
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd frontend && npm ci
      - run: cd frontend && npm run build
```
(Thêm bước build/push Docker image sau khi 2 job trên xanh, dùng `docker/build-push-action`, đẩy lên GitHub Container Registry — chỉ bật ở nhánh `main`.)

6. **Jacoco** (code coverage): thêm plugin `jacoco-maven-plugin` vào `pom.xml`, cấu hình fail build nếu coverage < 60% cho package `service/` (không áp cho `controller/`/`dto/` — ít logic, không đáng bắt buộc coverage cao). Mỗi block B01–B10 tự viết test của mình để đạt ngưỡng này, B13 chỉ dựng công cụ đo.

7. **Observability**: thêm `spring-boot-starter-actuator`, bật `/actuator/health`, `/actuator/info`; cấu hình Logback ghi log dạng JSON (dùng `logstash-logback-encoder`) để dễ đưa vào ELK sau này; thêm `logging.level.com.example.backend=INFO` mặc định, `DEBUG` qua biến môi trường `LOG_LEVEL`.

8. **`.env.example`** tổng hợp — gom tất cả biến môi trường mà các block B01–B10 đã khai báo rải rác, giữ file này làm nguồn tổng hợp duy nhất (B13 có trách nhiệm theo dõi và cập nhật file này mỗi khi có block mới thêm biến).

#### Definition of Done
- [ ] `docker compose up` từ thư mục gốc chạy được toàn bộ stack (postgres+redis+rabbitmq+backend+frontend) không lỗi
- [ ] `mvn test` chạy được toàn bộ test của mọi block đã merge, báo cáo coverage xuất ra `target/site/jacoco/index.html`
- [ ] CI pipeline xanh trên GitHub Actions cho 1 PR thử nghiệm
- [ ] `/actuator/health` trả `200 {"status":"UP"}`
