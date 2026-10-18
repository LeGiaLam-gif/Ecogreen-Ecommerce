# API reference

Base URL (development): `http://localhost:8081`. The frontend reaches it through the Vite proxy (`/api/...`).

This is a reference for the endpoints that exist today. Rules for new endpoints are in [`CLAUDE.md`](./CLAUDE.md); the
authentication, RBAC and envelope contracts are in [`docs/ai/contracts/`](./docs/ai/contracts).

## Two API generations

| | `/api/v1/auth/*` (new) | every other `/api/**` (legacy) |
|---|---|---|
| Success body | `{ "data": …, "meta": null }` | the bare JSON object / array |
| Error body | `{ "error": { "code", "message", "fields"? } }` | `{ "message": "…" }` |
| Error codes | `VALIDATION_ERROR`, `BUSINESS_RULE_VIOLATION`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL_ERROR` | HTTP status only |

The frontend (`services/http.js`) unwraps the new envelope only for URLs starting with `/api/v1/`.

## Authentication and authorization

Send `Authorization: Bearer <accessToken>` (a JWT, 15 minutes). No or invalid token → `401`; valid token without the required
right → `403`. Authorization is decided on the server; the frontend only hides controls.

| Guard | Meaning |
|---|---|
| Public | no token needed |
| User | any signed-in, active user |
| Owner or Admin | the order's owner, or a user with role `ADMIN` |
| Admin | role `ADMIN` (legacy `requireAdmin`; a `MANAGER` is denied) |

Permission-based checks (`requirePermission`) exist in the backend but **no endpoint uses them yet**.

## Auth — `/api/v1/auth`

| Method | Path | Access | Body → result |
|---|---|---|---|
| POST | `/register` | Public | `{ username, email, password }` → `201` user (role `CUSTOMER`, empty cart); no automatic sign-in |
| POST | `/login` | Public | `{ username, password }` → `{ accessToken, refreshToken, expiresIn, user }`; `429` + `Retry-After` after repeated failures |
| POST | `/google` | Public | `{ idToken }` → same as login; `503` when `GOOGLE_CLIENT_ID` is not configured |
| POST | `/refresh` | Public | `{ refreshToken }` → `{ accessToken, refreshToken, expiresIn }`; the old refresh token is revoked |
| POST | `/logout` | Public | `{ refreshToken }` → `data: null`; revokes that refresh token |
| GET | `/me` | User | current user |

`user` = `{ id, username, email, active, roles, permissions }`. Details and semantics: `docs/ai/contracts/B01-auth.md`.

## Catalogue — `/api/v1` (B02)

Envelope, errors and paging follow `docs/ai/contracts/B01-api-foundation.md`; full detail in `docs/ai/contracts/B02-catalog.md`.
The legacy `/api/products` and `/api/categories` endpoints were removed.

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/products` | Public | `ACTIVE` only. Query: `keyword, categoryId, minPrice, maxPrice, inStock, page, size (default 12, max 100), sort` (`createdAt`, `price`, `name`) |
| GET | `/products/{idOrSlug}` | Public | digits = id, otherwise slug; non-active → 404 |
| GET | `/admin/products` | `product:view` | all statuses; same query plus `status` |
| POST | `/admin/products` | `product:create` | `{ name, description, price, comparePrice, stockQuantity, image, categoryId, sku, brand, status (DRAFT or ACTIVE) }` → 201 |
| PATCH | `/admin/products/{id}` | `product:update` | partial; `status` may be any of `DRAFT, ACTIVE, OUT_OF_STOCK, INACTIVE, ARCHIVED` |
| POST | `/admin/products/{id}/publish` | `product:publish` | needs name, price > 0, category, ≥ 1 image (legacy image counts) |
| DELETE | `/admin/products/{id}` | `product:delete` | soft delete: status becomes `INACTIVE` (204) |
| POST | `/admin/products/{id}/images` | `product:update` | multipart `file` (JPEG/PNG/WebP, ≤ 5 MB, checked by content) + optional `altText` |
| DELETE | `/admin/products/{id}/images/{imageId}` | `product:update` | 204 |
| GET | `/categories` | Public | flat; `?tree=true` for the tree |
| POST | `/admin/categories` | `category:create` | `{ name, description, parentId }` → 201 |
| PATCH | `/admin/categories/{id}` | `category:update` | `{ name, description, parentId }` (replaces all three) |
| DELETE | `/admin/categories/{id}` | `category:delete` | 409 while it has products or child categories |
| GET | `/files/{key}` | Public | uploaded image (binary, `nosniff`) |

`stockQuantity` is the **available** quantity (on hand minus reserved, from the inventory); `inStock=true` means available > 0.
Product fields: `id, categoryId, categoryName, name, slug, description, price, comparePrice, stockQuantity, sku, brand, status, image (legacy: first image URL), images[{id,url,altText,sortOrder}]`.

## Inventory — `/api/v1/admin/inventory` (B03)

Contract: [`docs/ai/contracts/B03-inventory.md`](./docs/ai/contracts/B03-inventory.md). Stock is on hand / reserved; available = on hand − reserved.
`quantityOnHand` and `reservedQuantity` appear only here, never in a public, cart or order response.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/` | `inventory:view` | Query: `keyword` (name or SKU), `lowStock` (true keeps available ≤ `threshold`), `threshold` (default 10), `page`, `size` (default 20, max 100). Fixed order: available ascending, name, id |
| GET | `/{productId}` | `inventory:view` | `{ productId, productName, sku, quantityOnHand, reservedQuantity, available, updatedAt }`; 404 without an inventory row |
| GET | `/{productId}/transactions` | `inventory:view` | the stock ledger, newest first |
| PATCH | `/{productId}/adjust` | `inventory:adjust` | `{ quantityDelta, reason }` (reason required, ≤ 255); delta 0 or a result below the reserved quantity → 400 |

## Address book — `/api/v1/users/me/addresses` (B08, any logged-in user)

Contract: [`docs/ai/contracts/B08-customer.md`](./docs/ai/contracts/B08-customer.md). The owner is always the caller; someone else's address → 403.

| Method | Path | Notes |
|---|---|---|
| GET | `/` | the caller's addresses (default first); not paged, at most 10 |
| POST | `/` | `{ recipientName, phone, line1, ward, district, province, isDefault? }` → 201; the first address is always the default; the 11th → 400 `BUSINESS_RULE_VIOLATION` |
| PUT | `/{id}` | same body; `isDefault: true` moves the default |
| PATCH | `/{id}/default` | makes this address the only default |
| DELETE | `/{id}` | 204; deleting the default promotes the most recent remaining address |

## Admin users — `/api/v1/admin/users` (B08)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/` | `user:view` | query `keyword`, `role` (`CUSTOMER`/`MANAGER`/`ADMIN`), `active`, `page`, `size` (default 20, max 100), `sort` (`id`, `username`, `email`, `createdAt`) |
| GET | `/{id}` | `user:view` | adds `orderCount`, `returnRequestCount` |
| GET | `/{id}/orders` | `user:view` | the user's orders, paged; query `status`, `page`, `size`, `sort` |
| PATCH | `/{id}/disable` | `user:disable` | `active=false` + all refresh tokens revoked; takes effect on the next API call |
| PATCH | `/{id}/enable` | `user:disable` | |
| PATCH | `/{id}/roles` | `user:update` | `{ roles: [...] }` (complete set); only an `ADMIN` may grant or remove `ADMIN` (403) |
| DELETE | `/{id}` | `user:disable` | 204; 409 for yourself, the last active admin, or a user with orders / return requests |

## Cart — `/api/v1/cart` (User; always the caller's own cart)

| Method | Path | Body |
|---|---|---|
| GET | `/` | → `{ cartId, items[{ id, product, quantity, lineTotal, availableQuantity, warning }], subtotal, totalItems }` |
| POST | `/items` | `{ productId, quantity }`; checked against the available stock (400 `BUSINESS_RULE_VIOLATION`) → whole cart |
| PATCH | `/items/{id}` | `{ quantity }` → whole cart; a line of another user is 404 |
| DELETE | `/items/{id}` | → whole cart |
| DELETE | `/` | empties the cart, 204 |

## Checkout — `/api/v1/checkout` (User)

| Method | Path | Notes |
|---|---|---|
| POST | `/preview` | `{ discountCode? }` → `{ items[], subtotal, discountAmount, shippingFee, total, issues[] }`; writes nothing |
| POST | `/` | `{ addressId \| customerName + customerPhone + shippingAddress, discountCode?, paymentMethod? }` (`COD` default, `VIETQR`, `MOMO`) → `201` order. One transaction: lock the cart → validate → create the `PENDING_PAYMENT` order with a price snapshot → reserve stock (ascending product id) → `PENDING` payment → empty the cart. Prices and totals are never read from the request |

Contract: [`docs/ai/contracts/B04-checkout.md`](./docs/ai/contracts/B04-checkout.md).

## Discounts — `/api/v1/admin/discounts`

`GET` (paged) and `GET /{id}` need `discount:view`; `POST`, `PUT /{id}`, `DELETE /{id}` need `discount:manage`.
Body `{ code, percentOff? | amountOff?, minOrderAmount?, validFrom?, validTo?, active }`; duplicate code → 409.

## Orders — `/api/orders`

The legacy `POST /api/orders` is gone; orders are created by `POST /api/v1/checkout`.

Reading and changing orders is on `/api/v1` (envelope `{data, meta}`, paging `page`/`size`/`sort`). Contract: [`docs/ai/contracts/B05-order.md`](./docs/ai/contracts/B05-order.md).

Statuses: `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `RETURN_REQUESTED`, `RETURNED`, `REFUNDED`.
Invalid transitions return 400 `BUSINESS_RULE_VIOLATION`. Insufficient stock when an order is created also returns 400 `BUSINESS_RULE_VIOLATION` (since B03; it was 409), with a message that names the product.

## Orders — `/api/v1`

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/orders/my` | User | the caller's orders; optional `status` |
| GET | `/orders/{id}` | Owner or `order:view_all` | with items, `allowedNextStatuses`, `canCancel` |
| GET | `/orders/{id}/history` | Owner or `order:view` | status timeline |
| POST | `/orders/{id}/cancel` | Owner (only while `PENDING_PAYMENT`) or `order:cancel` | optional `{ note }` |
| GET | `/admin/orders` | `order:view_all` | filters `status`, `keyword`, `from`, `to` |
| PATCH | `/admin/orders/{id}/status` | `order:update` (+ `order:cancel` for `CANCELLED`) | `{ newStatus, note? }` |

## Payments — `/api/v1` (B06)

Online payment (`VNPAY`) is confirmed **only** by the provider's signed webhook; the browser returning from the gateway proves nothing. `COD` needs no payment step. `VIETQR` and `MOMO` are static QR codes with no confirmation signal, so staff confirm them. Order status changes only through the order state machine. Details and error codes: `docs/ai/contracts/B06-payment.md`.

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/payments/{orderId}/initiate` | Owner | optional `{ returnUrl }` (ignored unless it starts with `ecogreen.payment.return-url`) → `{ redirectUrl, provider }`; order must be `PENDING_PAYMENT`, method `VNPAY`; every call creates a new transaction reference |
| GET | `/payments/{orderId}` | Owner or `payment:view` | `{ id, orderId, paymentMethod, amount, status, transactionId, provider, paidAt, mockEnabled }`; the raw gateway response is never returned |
| PUT | `/payments/{orderId}/method` | Owner | `{ method }` — `COD`, `VIETQR`, `MOMO`, `VNPAY`; only while the order is `PENDING_PAYMENT` and unpaid |
| POST | `/payments/{orderId}/mock-pay` | Owner | **TEMPORARY-COMPAT(B06)**, development only: `{ outcome: SUCCESS\|FAILED }`; 400 unless `ecogreen.payment.mock-enabled=true` and the attempt used the mock provider |
| GET, POST | `/payments/webhook/{provider}` | **Public** (no token; protected by the provider's signature) | `VNPAY` (IPN, query parameters) or `MOCK`. The ONE response that is not the envelope: HTTP 200 with the gateway's JSON `{ "RspCode", "Message" }` — `00` processed, `02` already processed, `01` unknown reference, `04` amount mismatch, `97` invalid signature, `99` could not record (the gateway should deliver again). Unknown provider: standard `404` |
| GET | `/admin/payments?orderIds=1,2,…` | `payment:view` | up to 100 ids → `[{ orderId, paymentMethod, paymentStatus, amount, refundStatus }]` (the admin order list reads its page in one call) |
| POST | `/admin/payments/{orderId}/confirm` | `payment:confirm` | optional `{ note }`; only for a `PENDING` `VIETQR` / `MOMO` payment → payment `SUCCESS`, order `PAID` |
| POST | `/admin/payments/{orderId}/refund/complete` | `payment:refund` | optional `{ providerTransactionId }`; the order's `PENDING` refund → `SUCCESS`, payment → `REFUNDED`. There is no automatic gateway refund |

Legacy `/api/payments/**` was removed: `GET /order/{id}` → `GET /payments/{id}`, `PUT /order/{id}/method` → `PUT /payments/{id}/method`, `POST /order/{id}/pay` → `POST /payments/{id}/initiate` (and `mock-pay` in development).

## Returns — `/api/returns`

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/` | User | `{ orderId, reason, description?, imageUrl? }`; only for the caller's own order in `PAID` or `DELIVERED`; one pending request per order |
| GET | `/my` | User | the caller's requests |
| GET | `/` | Admin | all requests |
| PUT | `/{id}/status` | Admin | `{ status, adminNote }` — `PENDING`, `APPROVED`, `REJECTED`; approving does not refund or restock |

## Users and admin

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/admin/stats` | Admin | `{ totalUsers, totalProducts, totalOrders }` |
| GET | `/api/admin/reports/revenue-over-time` | Admin | query `range`, `groupBy` |
| GET | `/api/admin/reports/orders-by-status` | Admin | |
| GET | `/api/admin/reports/top-products` | Admin | query `limit` (default 5), `range` |
| GET | `/api/admin/reports/summary` | Admin | query `range` |

`GET /api/users`, `DELETE /api/users/{id}` (replaced by `/api/v1/admin/users`), `GET /api/users/me` and the former `/api/auth/*` endpoints no longer exist; use `/api/v1/auth/me` and `/api/v1/auth/*`.
