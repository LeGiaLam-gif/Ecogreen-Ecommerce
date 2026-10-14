# Contract: B08 — Customer Management (address book, admin user tools)

Owner: B08. Depends on: B01-F1 (`/api/v1` envelope, `ApiPaging`), B01-P2 (token revocation, inactive users rejected by
`AuthInterceptor`), B01-P3 (`requirePermission`, `user:view` / `user:update` / `user:disable`), B05 (order history via
`OrderService.listMyOrders`). Migration: `V70` (hand-applied, section 6).
B04 (checkout) consumes `AddressService.getOwned` (section 2).

## 1. Entity: `Address` (`addresses`)

| Field (JSON) | Column | Rule |
|---|---|---|
| `id` | `id` | generated |
| — | `user_id` | the owner; **never** in a response and never read from a request |
| `recipientName` | `recipient_name` | required, ≤ 100 |
| `phone` | `phone` | required, ≤ 20, lenient Vietnamese pattern: prefix `0`, `84` or `+84`, then 8–10 digits that may be separated by one space, dot or dash |
| `line1` | `line1` | required, ≤ 255 (house number and street) |
| `ward`, `district`, `province` | same | required, ≤ 100 each |
| `isDefault` | `is_default` | exactly one `true` per non-empty book |
| `createdAt`, `updatedAt` | same | server-set |

Invariants: at most **10** addresses per user; **exactly one** default per non-empty book (service rule + partial unique index
`uq_addresses_one_default`). The first address becomes the default whatever the client sends; deleting the default promotes the
most recently created remaining address; `isDefault=false` on the current default is ignored (move the default by choosing
another address). Fields are trimmed. Old order addresses are **not** imported into the book.

## 2. Public Java signatures (`service/AddressService`)

```java
public static final int MAX_ADDRESSES = 10;
public List<Address> list(Long userId)                                  // default first, then newest first
public Address getOwned(Long userId, Long addressId)                    // B04: the checkout address picker
public Address create(Long userId, AddressRequest request)              // 400 BUSINESS_RULE_VIOLATION when the book is full
public Address update(Long userId, Long addressId, AddressRequest request)
public Address setDefault(Long userId, Long addressId)
public void delete(Long userId, Long addressId)
```
`getOwned` throws `ResourceNotFoundException` (404) when the address does not exist and `ForbiddenException` (403) when it belongs
to someone else (so an id probe can tell "exists" from "does not exist"; the owner's data is never returned). **B04 must call it
with the user id from the token and copy the address fields into the order snapshot** (`orders.customer_name`,
`customer_phone`, `shipping_address`); an order never references an address row, so editing or deleting an address cannot change
an existing order. All mutations lock the user row (`SELECT … FOR UPDATE`) so concurrent requests of one user are serialised.

## 3. Endpoints — address book (`/api/v1/users/me/addresses`, any logged-in user, success `{data, meta:null}`)

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/` | — | 200 array of addresses (not paged: at most 10) |
| POST | `/` | `{recipientName, phone, line1, ward, district, province, isDefault?}` | 201 the address |
| PUT | `/{id}` | same | 200 the address |
| PATCH | `/{id}/default` | — | 200 the address |
| DELETE | `/{id}` | — | 204 |

Errors: 401 no token; 400 `VALIDATION_ERROR` with `fields`; 400 `BUSINESS_RULE_VIOLATION` for the 11th address; 403 `FORBIDDEN`
for someone else's address; 404 `NOT_FOUND`. There is no permission code: rights are ownership-based.

## 4. Endpoints — admin users (`/api/v1/admin/users`)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/` | `user:view` | query `keyword` (literal, case-insensitive match on username or email, ≤ 100), `role` (`CUSTOMER`, `MANAGER`, `ADMIN`), `active` (`true`/`false`), `page`, `size` (default 20, max 100), `sort` (`id`, `username`, `email`, `createdAt`; default `createdAt,desc`). 400 for an unknown role or sort field |
| GET | `/{id}` | `user:view` | detail, adds `orderCount` and `returnRequestCount` |
| GET | `/{id}/orders` | `user:view` | the user's orders (same `OrderResponse` as `/api/v1/orders/my`), query `status`, `page`, `size`, `sort` (`createdAt`, `totalPrice`); 404 for an unknown user |
| PATCH | `/{id}/disable` | `user:disable` | `active=false` and **all refresh tokens revoked**; idempotent |
| PATCH | `/{id}/enable` | `user:disable` | `active=true`; idempotent |
| PATCH | `/{id}/roles` | `user:update` | `{ "roles": ["CUSTOMER","MANAGER"] }` — the complete new set (1–3 names from `CUSTOMER`, `MANAGER`, `ADMIN`) |
| DELETE | `/{id}` | `user:disable` | 204; see the rules below |

Row: `{ id, username, email, active, roles (sorted), createdAt }` (+ the two counts in the detail). No password hash, Google
subject or permission list is ever returned. Disabling takes effect on the **next API call** because `AuthInterceptor` rejects
inactive users with one indexed query; the user cannot refresh either. Enabling does not need a new login while the access token
is still valid. A role change reaches the user's token after a refresh (≤ 15 min, B01-RBAC §8); the refresh token is not revoked.

**Safety rules** (all 409 `CONFLICT` unless stated; enforced in `UserService`, never in the browser):

1. An actor cannot disable, delete or change the roles of **themselves** (any role change on yourself is refused).
2. The **last active `ADMIN`** can never be disabled, deleted or demoted. The active admins are locked (`FOR UPDATE`) before the
   check, so two concurrent requests cannot remove the last two. A disabled admin is not counted.
3. Only an `ADMIN` may grant or remove the `ADMIN` role → 403 `FORBIDDEN` for anyone else (a `MANAGER` holds `user:update`; without
   this rule a manager could make themselves an admin).
4. **Delete** is allowed only when the user has **no orders and no return requests** (else 409, "hãy khóa tài khoản"). It removes the
   cart (existing behaviour), the address book and the refresh tokens first, then the user.
5. Unknown role names → 400 `VALIDATION_ERROR`; an empty set → 400.

Disable / enable / role change / delete are logged (actor id, target id) at INFO; a dedicated audit table is out of scope.

## 5. Query efficiency

User list: one page-of-ids query + one count + one `IN` load of the users with their roles (+ the batched role permissions that
`Role.permissions` EAGER already loads): the statement count does not depend on the number of rows (`CustomerPostgresTest`,
needs PostgreSQL). No collection is fetched together with the page limit. The order history reuses `OrderService.listMyOrders`
(page, count, items with products).

## 6. Database (`V70__addresses.sql`, hand-applied; Flyway is not active)

Creates `addresses` (FK `user_id → users ON DELETE CASCADE`), `idx_addresses_user_id`, partial unique index
`uq_addresses_one_default ON addresses(user_id) WHERE is_default`. Idempotent, header block and read-only verification SQL
included. No legacy data is changed. Without the script, `ddl-auto=update` creates the table **without** the foreign key,
defaults and both indexes (the entity maps `user_id` as a plain column), so apply V70 before relying on the one-default guarantee in
the database. Rollback: `DROP TABLE IF EXISTS addresses;` (customer-entered addresses are lost).

Permissions: `user:view`, `user:update`, `user:disable` already exist in `Permissions.java` and in `V6`; **no new permission and no
second migration was needed**.

## 7. Legacy removed

`GET /api/users` and `DELETE /api/users/{id}` (and `UserController`) are removed; the admin Users tab uses `/api/v1/admin/users`.
Behaviour changes: the list is paged (envelope with `meta`); a user with orders can no longer be hard-deleted (this used to fail
with an unmapped database error); `MANAGER` can now read users (`user:view`) through the new endpoints even though the admin UI
still requires `ADMIN`.

## 8. TEMPORARY-COMPAT

| Where | What | Remove when |
|---|---|---|
| `repository/UserSessionRepository` + `UserService` | revoke / delete all refresh tokens of a user through a B08-owned repository over the B01 `RefreshToken` entity | B01 publishes `RefreshTokenService.revokeAllForUser(Long userId)`; then call it and delete the repository |

## Requests for contract changes

* **B01-auth (owner B01):** `RefreshTokenService.revokeAllForUser(Long userId)` does not exist (only `revoke(rawToken)` and
  family revocation on reuse). B08's spec asks to call it. Please add it (one atomic
  `UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false`, `@Transactional`). Until then B08 uses
  the TEMPORARY-COMPAT repository above, which issues exactly that statement. B01 internals were not edited.
* **B04 (checkout):** call `AddressService.getOwned(tokenUserId, addressId)` and snapshot the fields (section 2); never accept
  recipient fields for a saved address from the client.
