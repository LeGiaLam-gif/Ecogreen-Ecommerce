# Contract: B02 — Catalog (products, categories, images, server-side search)

Status: implemented on `feature/b02-catalog`. Conventions (envelope, error codes, paging) are those of `B01-api-foundation.md`;
permissions are those of `B01-rbac.md`. Everything here is under `/api/v1`.

## 1. Product status

`Product.Status` = `DRAFT`, `ACTIVE`, `OUT_OF_STOCK`, `INACTIVE`, `ARCHIVED` (the same five values are in `chk_products_status`, V10).

| Rule | Behaviour |
|---|---|
| Create | default `ACTIVE`; `DRAFT` is opt-in; any other status → 400 |
| `DELETE` | soft delete → `INACTIVE` (never `ARCHIVED`) |
| `ARCHIVED`, `OUT_OF_STOCK` | set explicitly with `PATCH`; nothing sets them automatically (stock logic is B03) |
| Public list/detail | `ACTIVE` only. A non-active product is `404` on the public detail |
| `PATCH` to `ACTIVE` from another status | runs the same checks as publish |

## 2. Endpoints

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/products` | public | paged; `keyword, categoryId, minPrice, maxPrice, inStock, page, size, sort` |
| GET | `/products/{idOrSlug}` | public | digits → id, otherwise slug; non-ACTIVE → 404 |
| GET | `/admin/products` | `product:view` | all statuses; same filters plus `status` |
| POST | `/admin/products` | `product:create` | `ProductCreateRequest` → 201 + product |
| PATCH | `/admin/products/{id}` | `product:update` | `ProductUpdateRequest`, null member = unchanged |
| POST | `/admin/products/{id}/publish` | `product:publish` | `DRAFT`/`INACTIVE` → `ACTIVE` (idempotent when already active) |
| DELETE | `/admin/products/{id}` | `product:delete` | → `INACTIVE`, 204 |
| POST | `/admin/products/{id}/images` | `product:update` | multipart `file` (+ optional `altText`) → 201 `ImageDto` |
| DELETE | `/admin/products/{id}/images/{imageId}` | `product:update` | 204; the image must belong to the product |
| GET | `/categories` | public | flat list; `?tree=true` for the tree. Not paged (small set) |
| POST | `/admin/categories` | `category:create` | `CategoryRequest` → 201 |
| PATCH | `/admin/categories/{id}` | `category:update` | replaces name, description and parent together |
| DELETE | `/admin/categories/{id}` | `category:delete` | 409 while it has products or child categories |
| GET | `/files/{key}` | public | binary image; the only non-enveloped success response |

Errors: 400 `VALIDATION_ERROR` (bad body, unknown enum, sort not whitelisted, negative/inverted price range, overlong keyword,
non-image or oversized upload — including multipart size overflow), 400 `BUSINESS_RULE_VIOLATION` (publish rules, category cycle),
401/403 (missing token / missing permission), 404 `NOT_FOUND`, 409 `CONFLICT` (duplicate SKU or category name, slug race,
category delete). `category:view` exists but no B02 endpoint needs it.

## 3. Search and paging

`ProductSearchCriteria(keyword, categoryId, minPrice, maxPrice, inStock, status)` (record; blank keyword → none; keyword ≤ 100;
prices ≥ 0 and min ≤ max). `status` is honoured only by the admin search; the public search always forces `ACTIVE`.

* `keyword`: case-insensitive substring of `name` or `description`, matched literally (`\`, `%`, `_` escaped, `ESCAPE '\'`), always a bound parameter.
* `inStock=true` → `stock_quantity > 0`; `false`/absent adds no filter.
* `size` default 12, max 100 (clamped); `page` ≥ 0; `sort` whitelist `createdAt`, `price`, `name` (`field,asc|desc`, default `createdAt,desc`); any other value → 400. An `id` tie-breaker is appended so "load more" never repeats rows.
* Category is fetched with the page (`@EntityGraph`); images of a page are loaded with **one** `IN` query (`ProductService.loadImages`), never `JOIN FETCH` + `Pageable`.

Service signatures: `Page<Product> search(ProductSearchCriteria, Pageable)`, `searchAdmin(...)`, `Product getByIdOrSlug(String, boolean publicOnly)`,
`Product publish(Long)`, `create(ProductCreateRequest)`, `update(Long, ProductUpdateRequest)`, `delete(Long)`, `getById(Long)` (used by `CartService`),
`static Pageable toPageable(Integer page, Integer size, String sort)`, `Map<Long,List<ProductImage>> loadImages(Collection<Product>)`.

## 4. DTOs

* `ProductResponse`: legacy `id, categoryId, categoryName, name, description, price, stockQuantity, image, status` **unchanged**, plus `slug, comparePrice, sku, brand, images[{id,url,altText,sortOrder}]`. `image` = first gallery URL, else the legacy `products.image`. `images` is `null` when built without images (cart/order callers).
* `ProductCreateRequest`: `name*, description, price*, comparePrice, stockQuantity*, image, categoryId*, sku, brand, status`.
* `ProductUpdateRequest`: all optional; blank `sku`/`brand` clears; `clearComparePrice=true` clears the compare price.
* `CategoryRequest`: `name*, description, parentId`. `CategoryResponse`: `id, name, description, slug, parentId, children` (children only in the tree).
* Rules: `comparePrice ≥ price`; `sku` unique when present; `stockQuantity ≥ 0` (stock logic itself is untouched).

## 5. Slugs

Lower-case, Vietnamese diacritics removed in Java (`Normalizer` NFD + `đ`→`d`), non-alphanumerics → `-`, de-duplicated with `-2`, `-3`, …,
max 150 characters; always contains a letter (digits-only text gets the `product-` / `category-` prefix) so it can never equal a numeric id.
Products regenerate the slug on rename; categories keep theirs. SQL `regexp_replace` is never used for slugs.

## 6. Publish rules

`DRAFT`/`INACTIVE` → `ACTIVE` needs: non-blank name, price > 0, a category, and at least one image (a gallery image **or** the legacy `image` counts).
SKU is not required. Missing items are listed in the 400 message. `ARCHIVED`/`OUT_OF_STOCK` cannot be published.

## 7. Images and storage

* `ObjectStorageClient` (`store`, `load`, `delete`) with `LocalDiskStorageClient` under `ecogreen.upload.dir` (env `UPLOAD_DIR`, default `./uploads`, git-ignored).
* Upload: ≤ 5 MB, type decided by **magic bytes** (JPEG, PNG, WebP). SVG, HTML, GIF, executables and truncated files are rejected; the client's file name and `Content-Type` are ignored.
* Stored name is `<32 hex>.<jpg|png|webp>`, random, server-generated. `image_url` = `/api/v1/files/<key>`.
* `GET /files/{key}`: key must match that exact format and resolve inside the upload directory (anything else → 404); sends `X-Content-Type-Options: nosniff`, an image `Content-Type` and a 7-day public cache.
* Deleting an image removes the row and the file; a failed insert removes the just-stored file.

## 8. Categories

`parent_id` (FK `ON DELETE RESTRICT`) + `slug`. A category cannot be its own parent or the parent of one of its ancestors (400 `BUSINESS_RULE_VIOLATION`).
Duplicate names (case-insensitive) → 409.

## 9. Database migrations (manual — Flyway is NOT active)

Apply with `psql`, in order `V10` → `V11` → `V12`, after V2–V6. All idempotent; each has the mandatory header block.

| File | Legacy impact |
|---|---|
| `V10__products_extend.sql` | adds `slug` (backfilled `product-<id>`, then NOT NULL, unique), `compare_price`, `sku` (unique when present), `brand`; replaces every status CHECK (looked up in `pg_constraint`) with the 5-value one; compare-price CHECK only if no row violates it; list indexes |
| `V11__product_images.sql` | creates `product_images`; copies non-blank `products.image` once per product (`NOT EXISTS`); `products.image` is kept |
| `V12__categories_tree.sql` | adds `parent_id` (NULL = root) and `slug` (backfilled `category-<id>`, NOT NULL, unique) |

**Load `database/seed-data.sql` before V10/V12**: the seed inserts without a slug and fails once `slug` is NOT NULL.
Status at delivery: the three files were executed **twice** against a throw-away local PostgreSQL 16 (scratch cluster, removed afterwards) and behaved as described;
they were **not** executed against any real database. `CatalogMigrationPostgresTest` repeats the check when `ECOGREEN_TEST_PG_URL` is set.

## 10. Frontend

`productApi.js` / `categoryApi.js` use `/api/v1` (lists resolve to `{items, meta}`). `Home.jsx` searches on the server (300 ms debounce, `AbortController`, "Xem thêm" appends the next page).
`ProductDetail.jsx` accepts an id or slug (route `/product/:id` unchanged), shows the gallery or the legacy image, and strikes through `comparePrice` only when higher than `price`.
`imageResolver` passes `/api/...` URLs through unchanged. The admin product tab uses the paged admin endpoint (search/category/status filtered by the server, 20 per page) and has the new optional fields;
the dashboard tab reads one page of up to 100 products for the low-stock alert.

## 11. Compatibility and removals

Removed: `ProductController` (`/api/products`, `/admin/all`), `CategoryController` (`/api/categories`), and the legacy `ProductService`/`CategoryService` methods only they used.
Kept: `Product.image` / `ProductResponse.image` (**deprecated**; removal condition: B13, after the owner confirms every UI uses `images`) and `ProductService.getById` (used by `CartService`).
No `TEMPORARY-COMPAT` markers were added. `requireAdmin()` is no longer called by any catalog endpoint.

## 12. Configuration

`ecogreen.upload.dir=${UPLOAD_DIR:./uploads}`, `spring.servlet.multipart.max-file-size=5MB`, `max-request-size=6MB`. `WebConfig` CORS allows `PATCH`.

## 13. Known limitations

* Keyword search is `lower(...) LIKE` (no trigram index): fine for small catalogues; `pg_trgm` is a later optimisation. Case folding of non-ASCII letters depends on the database locale.
* The admin stock-bucket filter (≤ 10, 0, > 10) is applied to the page being shown, not on the server (no such server parameter in this contract).
* The dashboard low-stock count covers the newest 100 products.
* No cap on images per product; the admin UI has no gallery upload control yet (the endpoint exists; the legacy single-image field is still editable).
* Public detail/list responses are not cached beyond the image cache header.

## 14. Requests for contract changes

Open decisions used with the spec's defaults (D-8): `OUT_OF_STOCK` stays admin-set and hidden from the public list. Revisit in B03/B11.
