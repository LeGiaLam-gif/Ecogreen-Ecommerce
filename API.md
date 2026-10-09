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

Product fields: `id, categoryId, categoryName, name, slug, description, price, comparePrice, stockQuantity, sku, brand, status, image (legacy: first image URL), images[{id,url,altText,sortOrder}]`.

## Cart — `/api/cart` (User; always the caller's own cart)

| Method | Path | Body |
|---|---|---|
| GET | `/` | → `{ cartId, items[], totalPrice, totalItems }` |
| POST | `/items` | `{ productId, quantity? }` (default 1); stock is checked on the server |
| PUT | `/items/{id}` | `{ quantity }` |
| DELETE | `/items/{id}` | |
| DELETE | `/` | empties the cart |

## Orders — `/api/orders`

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/` | User | `{ customerName, customerPhone, shippingAddress, paymentMethod? }` (default `COD`). One transaction: validate stock → create order and items with price snapshot → deduct stock → create `PENDING` payment → empty cart |
| GET | `/my` | User | the caller's orders |
| GET | `/` | Admin | all orders |
| GET | `/{id}` | Owner or Admin | with items |
| PUT | `/{id}/status` | Admin | `{ status }` — `PENDING`, `CONFIRMED`, `PAID`, `CANCELLED`; transitions are **not** validated and stock is not restored on cancel |

## Payments — `/api/payments` (Owner or Admin)

Payment is **simulated**; there is no gateway and no webhook.

| Method | Path | Notes |
|---|---|---|
| GET | `/order/{orderId}` | payment of the order |
| PUT | `/order/{orderId}/method` | `{ method }` (the UI offers `COD`, `VIETQR`, `MOMO`, `VNPAY`); rejected once paid |
| POST | `/order/{orderId}/pay` | always succeeds unless the order is cancelled; stores a fake `MOCK-<uuid>` transaction id and marks the order `PAID` |

## Returns — `/api/returns`

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/` | User | `{ orderId, reason, description?, imageUrl? }`; only for the caller's own **paid** order; one pending request per order |
| GET | `/my` | User | the caller's requests |
| GET | `/` | Admin | all requests |
| PUT | `/{id}/status` | Admin | `{ status, adminNote }` — `PENDING`, `APPROVED`, `REJECTED`; approving does not refund or restock |

## Users and admin

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/users` | Admin | all users (with roles and permissions) |
| DELETE | `/api/users/{id}` | Admin | hard delete |
| GET | `/api/admin/stats` | Admin | `{ totalUsers, totalProducts, totalOrders }` |
| GET | `/api/admin/reports/revenue-over-time` | Admin | query `range`, `groupBy` |
| GET | `/api/admin/reports/orders-by-status` | Admin | |
| GET | `/api/admin/reports/top-products` | Admin | query `limit` (default 5), `range` |
| GET | `/api/admin/reports/summary` | Admin | query `range` |

`GET /api/users/me` and the former `/api/auth/*` endpoints no longer exist; use `/api/v1/auth/me` and `/api/v1/auth/*`.
