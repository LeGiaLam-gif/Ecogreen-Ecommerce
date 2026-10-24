# EcoGreen

EcoGreen is a full-stack web shop for eco-friendly everyday products (reusable bottles, kitchenware, recycled
accessories and similar). Customers browse a catalogue, fill a cart and place orders; staff manage the catalogue, orders,
returns and users from an admin area. The storefront and admin UI are in Vietnamese.

> **Status:** a working course/portfolio-grade application. Online payment goes through VNPay (sandbox by default) and
> a development-only mock provider; several production features are not built yet — see
> [What is not included](#what-is-not-included).

## What this project does

A visitor can browse products without an account. After signing up (or signing in with Google), a customer keeps a server-side
cart, checks out with delivery details, pays for the order and follows it in their order history. An administrator uses the
admin area to keep the catalogue up to date, move orders through their statuses, handle return requests and review sales.

## Features

### Customer features

- Browse products, filter by category and search by keyword. Search, filtering and paging are done by the server (`GET /api/v1/products`, 12 per page with "load more", 300 ms debounce while typing); a keyword matches name and description.
- Product detail page (by id or slug) with an image gallery and a struck-through compare price when the product has one.
- Server-side shopping cart with stock checks (`/api/v1/cart`); each line shows the quantity still available.
- Checkout (`/checkout`): pick a saved address or type the recipient, enter an optional discount code, and see the server-computed breakdown (items, discount, shipping, total) before confirming. Shipping is a flat fee below a free-shipping threshold.
- Address book (`/account/addresses`): up to 10 saved addresses, one default, add / edit / delete / set default. Checkout offers these addresses (the default is preselected).
- Choose a payment method on the payment page. **Cash on delivery** needs no payment step. **VNPAY** sends the browser to the
  VNPay gateway; the order becomes paid only when VNPay's signed server-to-server callback arrives (returning to the shop proves
  nothing). **VietQR** and **MoMo** show a static QR code in the UI; the backend has no payment provider for them, so a staff member
  confirms the payment. With `PAYMENT_MOCK_ENABLED=true` (the default when the variable is unset) a development-only mock provider
  and "mock pay" buttons are available; set it to `false` outside development.
- Order history and order detail with a status timeline; an unpaid order can be cancelled by its owner.
- Return request for a `DELIVERED` order: choose the items and quantities, give a reason and description, and attach 1–5 photos
  (JPG/PNG/WebP, up to 5 MB each); only one open request per order. The customer follows its status in the order detail.
- Customers receive an email for order and return events when SMTP is configured; every attempt is logged (see the admin
  Notifications tab). The customer-facing notification list exists in the API only.
- Register, sign in with username and password, or sign in with Google (when configured). Sessions renew automatically.
- FAQ, contact and return-policy pages. The chat widget gives canned replies; it is not connected to a support team.

### Management / admin features

The admin area is split into tabs. A tab, and each action button inside it, is shown only when the signed-in user holds the
permission the server requires for it (the server checks it again on every request).

- Overview: action cards (orders waiting for payment, returns waiting for review, products running low, refund amount this
  month), a "top customers by order count" table, the newest orders, and the sales dashboard (revenue over time, orders by status,
  top products, selectable date range; the sales dashboard is ADMIN-only because it uses the legacy reports).
- Products: search and filter, create (always as a draft), edit, upload several images (they are saved in the order you arrange
  them before saving), delete images, publish with the missing requirements explained, deactivate (soft delete) and reopen.
- Categories: a tree you can expand and collapse, with a parent picker and "add child".
- Inventory: stock on hand, reserved and available per product, a low-stock filter, manual adjustments (a reason is required)
  and the stock history of a product.
- Orders: search and filter orders, and move each one to the next allowed status (the server decides which moves are allowed), confirm a VietQR / MoMo payment, and complete a pending refund.
- Returns: request more information from the customer, approve or reject with a note, mark the goods as received (this restocks them) and start the refund; view all the customer's photos. Staff complete the pending refund here or in the Orders tab; there is no automatic gateway refund.
- Customers: server-side search, role and active filters with paging, a detail view with order history; lock / unlock an account
  (a locked user is signed out everywhere); delete only a user with no orders or return requests (otherwise lock the account).
  You cannot lock or delete yourself or the last active admin.
- Discount codes: list, create, edit and delete percent or fixed-amount codes with validity dates.
- Notifications: the log of customer emails (sent / failed / pending) with a status filter; no message text is shown.

Access is by role: `CUSTOMER`, `MANAGER`, `ADMIN`. The backend has a permission system (29 permission codes, assigned to roles in
the database). `ADMIN` and `MANAGER` can open `/admin` and see the tabs their permissions allow (by default a `MANAGER` cannot
lock, unlock or delete users, and has no `audit:view` or `system:configure`). The only endpoints that still require the `ADMIN`
role are the five legacy ones under `/api/admin` (`/stats` and the four `/reports/*` the sales dashboard reads).

### What is not included

- Payments: no gateway other than VNPay (and the development mock); MoMo and VietQR exist only as static QR codes in the UI.
  Refunds are recorded by staff; nothing is refunded automatically at the gateway. Expired `PENDING_PAYMENT` orders are not cancelled automatically.
- Screens: no admin screen to change a user's roles (the API exists), no screen for the analytics events list, no customer-facing notification list.
- Accounts: no password reset and no email verification.
- Operations: no audit log, no Docker image for the application, no CI pipeline, no health/metrics endpoints, no frontend tests.
- The five legacy `/api/admin` endpoints still use the older API shape.

Remaining planned work is in [`docs/ai/MODULE_SPECS.md`](./docs/ai/MODULE_SPECS.md).

## How the system works

```
Browser (React, Vite dev server :5173)
   │  /api/* is proxied to the backend in development
   ▼
Spring Boot REST API (:8081) ──► PostgreSQL
```

1. The frontend calls the REST API through one axios instance (`frontend/src/services/http.js`).
2. Signing in returns a short-lived access token (JWT, 15 minutes) and a rotating refresh token. The frontend renews the access
   token automatically when it expires.
3. The backend decides what each caller may do (roles and permissions come from the token, ownership is checked per order);
   the frontend only hides buttons.
4. Checkout is one database transaction: lock the cart → validate stock, discount and shipping → create the order (`PENDING_PAYMENT`) with a snapshot of the current
   prices → reserve stock → record the first status-history row → create a pending payment → empty the cart. Afterwards every
   status change goes through one validated state machine.

## Technology stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite 8, React Router 7, axios, `qrcode.react`; ESLint 9 |
| Backend | Java 21, Spring Boot 4.0.5 (Web, Data JPA/Hibernate, Validation, Mail), Maven wrapper |
| Security | JWT (jjwt, HS256), BCrypt (`spring-security-crypto`), Google ID-token verification (`google-api-client`) |
| Database | PostgreSQL 16 |
| Tests | JUnit 5 + Mockito (backend); a dependency-free Node script for the frontend token-refresh logic |
| Payments / email | VNPay (HMAC-signed redirect and callback), SMTP through Spring Mail |
| Local infrastructure | Docker Compose (PostgreSQL only) |

## Project structure

```
backend/                 Spring Boot application (package com.example.backend)
  src/main/java/.../       api, config, controller, dto, entity, event, exception, repository, security, service, storage
  src/main/resources/      application.properties, db/migration/ (hand-applied SQL migrations)
  src/test/                unit tests
frontend/                React application
  src/pages, components    screens and shared UI
  src/context, hooks       AuthContext, CartContext, usePermission
  src/pages/admin          admin dashboard and its tabs
  src/services, utils      API modules (http.js is the single HTTP client) and helpers
  scripts/                 verify-auth-refresh.mjs
database/                init-postgres.sql (schema), seed-data.sql (demo data, destructive), recycled_products.xlsx (spreadsheet of recycled-product data; not read by any code)
docker/                  docker-compose.yml (PostgreSQL)
docs/ai/                 module contracts (contracts/), remaining-work specification, project requirements (.docx) and a diagram
API.md, DATABASE.md      reference documentation
CLAUDE.md                rules for AI coding agents
```

## Prerequisites

- **JDK 21**
- **Node.js 20.19+ or 22.12+** (required by Vite 8) with npm
- **PostgreSQL 16** — either installed locally or via Docker (Docker Desktop / Docker Engine with Compose)
- The `psql` command-line client (to apply the SQL files)
- Optional: a Google OAuth 2.0 **Web client ID** if you want Google sign-in

Maven does not need to be installed; the project uses the Maven wrapper.

## Installation

```bash
git clone https://github.com/LeGiaLam-gif/Ecogreen-Ecommerce.git
cd Ecogreen-Ecommerce
cd frontend && npm install && cd ..
```

Use `npm install`, not `npm ci`: `npm ci` currently fails because `frontend/package-lock.json` is out of sync with
`package.json`.

## Configuration

The backend reads its settings from **environment variables and `backend/src/main/resources/application.properties`**.
Spring Boot does **not** read a `.env` file, so export the variables in the shell that starts the backend (or set them in your
IDE run configuration).

| Variable | Used by | Required | Meaning |
|---|---|---|---|
| `JWT_SECRET` | backend | **yes** | Signing key, at least 32 bytes. The backend refuses to start without it. Generate one with `openssl rand -base64 48`. |
| `GOOGLE_CLIENT_ID` | backend | no | OAuth Web client ID. When empty, Google sign-in is disabled. |
| `VITE_GOOGLE_CLIENT_ID` | frontend | no | The same client ID. Put it in `frontend/.env` (Vite reads env files from `frontend/`). |
| `UPLOAD_DIR`, `SHIPPING_FLAT_FEE`, `FREE_SHIPPING_THRESHOLD` | backend | no | Upload directory and shipping rule (defaults in `application.properties`). |
| `PAYMENT_VNPAY_TMN_CODE`, `PAYMENT_VNPAY_SECRET`, `PAYMENT_VNPAY_URL`, `PAYMENT_RETURN_URL` | backend | no | VNPay sandbox/production settings. Without the code and secret the `VNPAY` method fails closed (400). |
| `PAYMENT_MOCK_ENABLED` | backend | no | Enables the development mock provider. **Defaults to `true` when unset**; set `false` outside development. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_AUTH`, `SMTP_STARTTLS` | backend | no | Notification email. Without `SMTP_HOST` no email is sent. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | backend | no | Override the database connection (see Database setup). |

`.env.example` at the repository root lists every variable the code reads and marks the required one; its `DB_*` entries are **not** read by the application. Never
commit a real `.env` file or real secrets.

Without overrides the backend connects to `jdbc:postgresql://localhost:5432/ecogreen` as user `postgres` with the password set
in `application.properties`, and listens on port **8081**. CORS allows only the Vite dev server (`http://localhost:5173`).

## Database setup

The schema comes from `database/init-postgres.sql`. Everything added later (Google sign-in column, refresh tokens, permissions, catalogue, orders, inventory, discounts,
payments, refunds, returns, addresses, analytics events, notifications) is in SQL files in `backend/src/main/resources/db/migration/` (`V2` to `V90`). **Flyway is not used**; apply the files yourself, in numeric order,
**before the first backend start**. They are idempotent, so re-running them is safe.

**1. Start PostgreSQL and load the schema** — choose one:

```bash
# A) Docker (port 5433 on the host; init-postgres.sql runs automatically on the first start of an empty volume)
cd docker && docker compose up -d && cd ..

# B) Local PostgreSQL (port 5432)
createdb -U postgres ecogreen
psql -U postgres -d ecogreen -f database/init-postgres.sql
```

**2. Apply the migrations in order** (adjust host/port: `-p 5433` for Docker, `-p 5432` for a local server):

```bash
for f in $(ls backend/src/main/resources/db/migration/V*.sql | sort -V); do
  psql -h localhost -p 5433 -U postgres -d ecogreen -v ON_ERROR_STOP=1 -f "$f"
done
```

On Windows, run the same files one by one with `psql ... -f <file>` in this order: V2, V3, V4, V5, V6, V10, V11, V12, V20, V21, V22, V30, V31, V40, V41, V50, V51, V60, V70, V80, V90.
`V5` renames the legacy `USER` role to `CUSTOMER` and `V6` seeds the permission catalogue and the role → permission mapping. Without them, the roles and permissions are incomplete.

`V40` creates the `discounts` table and `V41` seeds the `discount:view` / `discount:manage` permissions for `ADMIN` and `MANAGER` (Flyway is not active; apply by hand).

`V10`–`V12` add the catalogue columns, the product image table and the category tree (load `database/seed-data.sql` before them). Uploaded product images are stored in the directory given by `UPLOAD_DIR` (default `./uploads`, not committed).

`V30` and `V31` introduce the stock model (`inventory` on hand / reserved and the `inventory_transactions` ledger) and **convert** the existing `products.stock_quantity` values. Apply them in this order with the backend **stopped** and before its first start with this version; the verification queries are in the file headers and in `docs/ai/contracts/B03-inventory.md`.

`V50` and `V51` add the payment gateway fields and the `refunds` table, `V60` the full return model (several items, images, status history), `V80` the `analytics_events` table and `V90` the `notifications` table; details are in the contracts `B06`–`B10` and `DATABASE.md`.

`V70` creates the `addresses` table (customer address book) with its foreign key and the "one default per user" unique index; it changes no existing data. Apply it before relying on that guarantee in the database; details in `docs/ai/contracts/B08-customer.md`.

`V20` re-labels stored orders (`PENDING` → `PENDING_PAYMENT`, `CONFIRMED` → `PROCESSING`) and widens the status check. Stop the old backend before applying it and back up `orders` first.

**3. Point the backend at the right database.** `application.properties` expects port 5432; Docker publishes 5433 with the
password defined in `docker/docker-compose.yml`. For Docker, export before starting the backend:

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/ecogreen?options=-c%20timezone=Asia/Ho_Chi_Minh'
export SPRING_DATASOURCE_PASSWORD='<POSTGRES_PASSWORD from docker/docker-compose.yml>'
```

**4. Demo data and the first admin (optional).** The application works with empty tables (the storefront shows no products
until an admin adds some).

- `database/seed-data.sql` loads 5 categories, 8 products and two demo accounts (an admin and a customer; see the file).
  **It deletes all existing orders, payments, cart items, products and categories first** — use it only on a disposable
  development database, and never expose the demo accounts.
- Without the seed, register through `/register`, then grant that user the admin role:

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'your_username' AND r.name = 'ADMIN';
```

Reload the page or sign in again; the admin link appears in the navbar.

## Run the backend

```bash
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run             # Windows: .\mvnw.cmd spring-boot:run
```

If `./mvnw` answers "Permission denied" (a checkout from before the executable bit was committed), run `chmod +x mvnw` or use
`sh ./mvnw`. The API is then available at `http://localhost:8081`.
On PowerShell set the secret with `$env:JWT_SECRET = '<at least 32 bytes>'`. A secret generated per shell session invalidates
existing sign-ins when it changes; keep a stable value if you want sessions to survive restarts.

## Run the frontend

```bash
cd frontend
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` to `http://localhost:8081`.

## Run the complete project

1. Start PostgreSQL and apply the schema and migrations (Database setup).
2. Start the backend and wait for it to finish starting.
3. Start the frontend and open `http://localhost:5173`.

## Testing and verification

```bash
# Backend unit tests
cd backend && ./mvnw test

# Frontend
cd frontend
npm run lint                           # ESLint
npm run build                          # production build
node scripts/verify-auth-refresh.mjs   # token-refresh logic, no network needed
```

Manual smoke test: `curl -i http://localhost:8081/api/v1/products` returns `200` with `{"data":[...],"meta":{...}}`; register a user, sign in,
add a product to the cart, check out, and open the order in the order history. As an admin, open `/admin`.

## Common issues

- **Backend exits at startup mentioning the JWT secret** — `JWT_SECRET` is missing or shorter than 32 bytes.
- **`Connection refused` or `password authentication failed`** — the backend uses port 5432 and the `application.properties`
  password by default; with Docker set the `SPRING_DATASOURCE_*` variables (Database setup, step 3).
- **`./mvnw: Permission denied`** — run `chmod +x backend/mvnw` or use `sh ./mvnw ...`.
- **`npm ci` fails with "package.json and package-lock.json are not in sync"** — use `npm install`.
- **Vite fails to start with a Node engine error** — upgrade to Node 20.19+ or 22.12+.
- **Google button is disabled, or Google sign-in answers 503** — set `VITE_GOOGLE_CLIENT_ID` in `frontend/.env` (and rebuild/restart
  Vite) and `GOOGLE_CLIENT_ID` for the backend, using the same Web client ID.
- **Roles look wrong or the admin menu is missing** — make sure migrations `V4`–`V6` (and `V41` for the discount permissions) were applied and that the user has the
  `ADMIN` role, then sign in again.
- **HTTP 429 on sign-in** — five failed attempts per username and IP lock sign-in for 15 minutes.
- **The storefront is empty** — there are no products yet; add them as admin or load `database/seed-data.sql` on a disposable DB.

## Development notes

- Every endpoint under `/api/v1` follows the new API convention (`{ data, meta }` success, `{ error }` failure). Only the five
  legacy `/api/admin` endpoints return plain JSON. The rules for API changes are in [`CLAUDE.md`](./CLAUDE.md).
- Permission codes live in one class, `security/Permissions.java`. Roles and their permissions are stored in the database.
- Hibernate runs with `ddl-auto=update` in development, so the entities and the SQL files must be kept consistent.
- Reference: [`API.md`](./API.md) (endpoints), [`DATABASE.md`](./DATABASE.md) (tables), `docs/ai/contracts/` (one contract per module).

## Contributing

1. Create a branch from `main` (`feature/<name>`, `fix/<name>` or `chore/<name>`); do not commit to `main` directly.
2. Keep the change focused, follow the conventions in [`CLAUDE.md`](./CLAUDE.md), and update the documentation your change
   affects.
3. Run the backend tests, `npm run lint` and `npm run build` before opening a pull request, and describe what you ran.
4. Never commit secrets or a real `.env` file.
