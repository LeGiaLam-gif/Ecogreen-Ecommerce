# EcoGreen — Module specifications (remaining work)

This file describes **work that is not implemented yet**: the status of every block, the migration ranges and the
specification of what remains. Finished modules are specified by their permanent contracts in [`contracts/`](./contracts),
not here.

How an AI agent must work (workflow, scope, API contract, migration policy, testing and reporting rules) is defined in
[`CLAUDE.md`](../../CLAUDE.md), **not** here.

Use: give an agent `CLAUDE.md` + sections 1–3 of this file + **exactly one** item of section 5.

## 1. Status (verified against the code at commit `8ee0bf2`)

| Block | Status | Evidence in the repository |
|---|---|---|
| B01 Auth, API foundation, RBAC | **Done** (open decisions in section 4) | `api/`, `JwtService`, `RefreshTokenService`, `GoogleApiIdentityVerifier`, `Permissions.java` (29 codes), `AuthGuard.requirePermission`, V2–V6, V41 — contracts `B01-api-foundation.md`, `B01-auth.md`, `B01-rbac.md` |
| B02 Catalog | **Done** | `/api/v1` catalogue controllers, `product_images`, category tree, V10–V12 — `B02-catalog.md` |
| B05 Order | **Done** | 10-status state machine (`OrderService.transitionStatus`), `order_status_history`, V20–V22 — `B05-order.md` |
| B03 Inventory | **Done** | `inventory` + `inventory_transactions`, `InventoryService`, `/api/v1/admin/inventory`, V30–V31, admin Inventory tab — `B03-inventory.md` |
| B08 Customer | **Done** | address book, `/api/v1/admin/users`, V70 — `B08-customer.md`. The admin UI has no screen to change a user's roles (the API exists) |
| B04 Cart & Checkout | **Done** | `/api/v1/cart`, `/api/v1/checkout`, discounts, shipping rule, V40–V41, admin Discounts tab — `B04-checkout.md` |
| B06 Payment | **Done** (backend + UI) | `PaymentService`, `service/payment` (providers `MOCK`, `VNPAY`), `PaymentV1Controller`, `AdminPaymentV1Controller`, `refunds`, V50–V51, `Payment.jsx`/`PaymentReturn.jsx` — `B06-payment.md`. MoMo and VietQR have no backend provider (static QR, staff confirmation) |
| B07 Return & Refund | **Done** (backend + UI) | `ReturnService`, `ReturnV1Controller`, `AdminReturnV1Controller`, `returns`/`return_items`/`return_images`/`return_status_history`, V60, `OrderDetail.jsx` form, admin Returns tab — `B07-return.md` |
| B09 Analytics | **Backend done, UI partial** | `analytics_events`, `AdminAnalyticsV1Controller` (low stock, pending orders, refund summary, customer frequency, `/events`), V80, Overview cards. The events list has no screen — `B09-analytics.md` |
| B10 Notification | **Backend done, UI partial** | `notifications`, `service/notification`, `AdminNotificationV1Controller`, `NotificationV1Controller`, V90, admin Notifications tab. No customer-facing notification list — `B10-notification.md` |
| B11 Storefront UI | **Done** | server-side catalogue search, gallery, address book, checkout preview, order timeline, multi-item return form |
| B12 Admin UI | **Done except the roles screen** | tabs for overview, products, categories, inventory, discounts, orders, returns, users, notifications; `usePermission` hook |
| B13 Infra | Not started | no Flyway, Dockerfile, CI workflow, actuator or Jacoco; `ddl-auto=update`; no frontend tests besides `scripts/verify-auth-refresh.mjs` |

## 2. Dependency model and order

All modules of steps 1–13 below are built. Only B13 and the cross-system pass remain.

| Module | HARD dependency | Notes |
|---|---|---|
| B13 Infra | none | flips `ddl-auto`, activates Flyway, removes deprecated columns |
| UI gaps (section 5.1) | the backend module each screen displays (all built) | |

Development order that was followed (kept for reading the contracts):
B01 → B02 and B05 → B03 → B08 → B04 → B06 → B07 → B09 and B10 → B11/B12 slices → **B13** → cross-system efficiency pass
(report-driven, no specification written).

## 3. Migration version ranges (namespaces, not execution order)

Files present in `backend/src/main/resources/db/migration` (21): `V2`, `V3`, `V4`, `V5`, `V6`, `V10`, `V11`, `V12`, `V20`, `V21`,
`V22`, `V30`, `V31`, `V40`, `V41`, `V50`, `V51`, `V60`, `V70`, `V80`, `V90`.

`V1` = baseline = `database/init-postgres.sql` (**no module creates V1**; B13 or the owner's minimal Flyway activation creates
`V1__baseline.sql` as a verbatim copy).

| Block | Range | Used |
|---|---|---|
| B01 Auth/RBAC | V2 – V9 | V2–V6 |
| B02 Catalog | V10 – V19 | V10–V12 |
| B05 Order | V20 – V29 | V20–V22 |
| B03 Inventory | V30 – V39 | V30–V31 |
| B04 Cart/Checkout | V40 – V49 | V40–V41 |
| B06 Payment | V50 – V59 | V50–V51 (owns `refunds`) |
| B07 Return/Refund | V60 – V69 | V60 |
| B08 Customer | V70 – V79 | V70 |
| B09 Analytics | V80 – V89 | V80 |
| B10 Notification | V90 – V99 | V90 |
| B13 cleanup | V100+ | none yet |

Rules:
1. A migration may reference (FK) only tables owned by modules that come earlier in the order of section 2.
2. Numeric order is not build order for B08 (V70 was built before V40). When Flyway is activated the owner MUST set
   `spring.flyway.out-of-order=true` (or apply files manually in dependency order).
3. A module must never use a number outside its range. Need more? Report it; do not borrow.
4. Idempotency, documentation header and EMPTY/EXISTING-database statements are mandatory (see `CLAUDE.md`, Database and migrations).

## 4. Decisions referenced in the repository but never written down

The IDs below are still mentioned in the repository, with what is actually known:

| ID | Where it appears | Known content |
|---|---|---|
| D-1 | B04 | customer totals change once shipping is enabled; documented in `contracts/B04-checkout.md` (see D-10) |
| D-10 | B04 | flat shipping fee `ecogreen.shipping.flat-fee` (30 000 VND) below `ecogreen.shipping.free-threshold` (500 000 VND, compared with the items total before discount, boundary ships free); both configurable. Before B04 shipping was free for everyone, so customers now pay more on orders under the threshold |
| D-5 | `contracts/B01-auth.md` §8 | refresh token in an httpOnly cookie instead of `localStorage` — **open**, not switched |
| D-6 | `contracts/B01-rbac.md` §3 | `MANAGER` = all permissions except `system:configure`, `audit:view`, `user:disable` — implemented as the default |
| D-9 | `CLAUDE.md` (API contract) | the legacy `Asia/Ho_Chi_Minh` JVM/DB timezone default vs UTC in JSON — **open** |
| permission loading | `contracts/B01-rbac.md` §7 | the spec asked for one joined query when issuing a token; the implemented EAGER + `@BatchSize` mapping was never measured — **open** (options 1–4 in that contract) |

If a task is blocked by one of these, use the stated default, report it, and do not invent a different decision.

### Backlog items no module specification covers

Each needs a new, explicitly assigned task:

- **Audit log of admin actions** (`audit_logs` table and writer). The `audit:view` permission exists but nothing records or serves data.
- Externalise datasource and CORS settings (`application.properties` hard-codes the DB URL/user/password and `WebConfig` allows only `http://localhost:5173`).
- HTTPS / secret management for real deployments.
- Login throttling is per instance and in memory; there is no rate limit on other endpoints.

## 5. Remaining work

### 5.1 UI gaps

Each is a small slice that only calls existing endpoints (contracts in `contracts/`):

- Admin screen to change a user's roles: `PATCH /api/v1/admin/users/{id}/roles` (`setUserRoles` already exists in `services/userApi.js`, unused).
- Admin screen for the analytics events list: `GET /api/v1/admin/analytics/events` (B09).
- Customer notification list: `GET /api/v1/users/me/notifications` (B10).

### 5.2 B13 — Infrastructure, CI/CD, observability

Runs independently; blocks nothing. Items (each only when assigned):

1. **Flyway**: add `flyway-core` and `flyway-database-postgresql`; create `V1__baseline.sql` as a verbatim copy of
   `database/init-postgres.sql`; set `spring.flyway.out-of-order=true` (section 3, rule 2); then switch `spring.jpa.hibernate.ddl-auto`
   from `update` to `validate`.
2. **Dockerfiles** for the backend (multi-stage Maven → JRE 21) and the frontend (Vite build served by Nginx); extend
   `docker/docker-compose.yml` with the services (do not overwrite the existing `postgres` service). Redis/RabbitMQ only if a
   measured need exists.
3. **CI** (`.github/workflows/ci.yml`): backend `mvn -B test` and `package`, frontend lint and build.
   Repair `frontend/package-lock.json` first: `npm ci` currently fails because it is out of sync with `package.json`
   (missing `@emnapi/*`).
4. **Jacoco** with a coverage threshold on `service/` only.
5. **Observability**: `spring-boot-starter-actuator` with `/actuator/health` and `/actuator/info` exposed; structured logging.
6. **Configuration**: externalise the datasource and the CORS origin (`application.properties`, `WebConfig`); keep `.env.example`
   as the single list of variables.
7. **Cleanup migrations** (V100+): drop the deprecated `products.image` column (removal condition in `contracts/B02-catalog.md`) and any other column a contract marks deprecated
   and add final constraints.

Definition of done: `docker compose up` starts the whole stack; `mvn test` and the CI pipeline are green; `/actuator/health`
answers `200 {"status":"UP"}`.
