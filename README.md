# EcoGreen

EcoGreen is a full-stack web shop for eco-friendly everyday products (reusable bottles, kitchenware, recycled
accessories and similar). Customers browse a catalogue, fill a cart and place orders; staff manage the catalogue, orders,
returns and users from an admin area. The storefront and admin UI are in Vietnamese.

> **Status:** a working course/portfolio-grade application. Payment is **simulated** (no real payment gateway) and several
> production features are not built yet — see [What is not included](#what-is-not-included).

## What this project does

A visitor can browse products without an account. After signing up (or signing in with Google), a customer keeps a server-side
cart, checks out with delivery details, "pays" for the order and follows it in their order history. An administrator uses the
admin area to keep the catalogue up to date, move orders through their statuses, handle return requests and review sales.

## Features

### Customer features

- Browse products, filter by category and search by keyword (done in the browser over the full product list).
- Product detail page.
- Server-side shopping cart with stock checks.
- Checkout with recipient name, phone and delivery address.
- Choose a payment method — cash on delivery, VietQR, MoMo or VNPAY — and confirm payment on a simulated payment page
  (QR codes are displayed, but no money moves and no external service is called).
- Order history and order detail.
- Return / exchange request for a paid order (reason, description, optional image link) and its review status.
- Register, sign in with username and password, or sign in with Google (when configured). Sessions renew automatically.
- FAQ, contact and return-policy pages. The chat widget gives canned replies; it is not connected to a support team.

### Management / admin features

- Overview: totals for users, products and orders, plus a sales dashboard (revenue over time, orders by status, top products,
  selectable date range).
- Products: create, edit, deactivate (soft delete), including inactive products.
- Categories: create, edit, delete.
- Orders: list all orders and change their status.
- Returns: approve or reject requests with a note to the customer.
- Users: list with role filter, delete.

Access is by role: `CUSTOMER`, `MANAGER`, `ADMIN`. The backend has a permission system (27 permission codes, assigned to roles in
the database), but today every admin endpoint and the admin UI still require the `ADMIN` role; a `MANAGER` cannot use them yet.

### What is not included

Real payment gateway and webhooks, inventory reservation, an order state machine with history, discounts and shipping cost,
saved addresses, product search/filter/pagination on the server, multiple product images and image upload, notifications,
analytics events, audit log, Docker images for the app, CI pipeline, frontend tests. The planned work is described in
[`docs/ai/MODULE_SPECS.md`](./docs/ai/MODULE_SPECS.md).

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
4. Checkout is one database transaction: validate stock → create the order with a snapshot of the current prices → deduct
   stock → create a pending payment → empty the cart.

## Technology stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite 8, React Router 7, axios, `qrcode.react`; ESLint 9 |
| Backend | Java 21, Spring Boot 4.0.5 (Web, Data JPA/Hibernate, Validation), Maven wrapper |
| Security | JWT (jjwt, HS256), BCrypt (`spring-security-crypto`), Google ID-token verification (`google-api-client`) |
| Database | PostgreSQL 16 |
| Tests | JUnit 5 + Mockito (backend); a dependency-free Node script for the frontend token-refresh logic |
| Local infrastructure | Docker Compose (PostgreSQL only) |

## Project structure

```
backend/                 Spring Boot application (package com.example.backend)
  src/main/java/.../       api, config, controller, dto, entity, exception, repository, security, service
  src/main/resources/      application.properties, db/migration/ (hand-applied SQL migrations)
  src/test/                unit tests
frontend/                React application
  src/pages, components    screens and shared UI
  src/context              AuthContext, CartContext
  src/services             API modules; http.js is the single HTTP client
  scripts/                 verify-auth-refresh.mjs
database/                init-postgres.sql (schema), seed-data.sql (demo data, destructive), recycled_products.xlsx (source of the demo products)
docker/                  docker-compose.yml (PostgreSQL)
docs/ai/                 module contracts and the remaining-work specification
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
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | backend | no | Override the database connection (see Database setup). |

`.env.example` at the repository root lists the variable names; its `DB_*` entries are **not** read by the application. Never
commit a real `.env` file or real secrets.

Without overrides the backend connects to `jdbc:postgresql://localhost:5432/ecogreen` as user `postgres` with the password set
in `application.properties`, and listens on port **8081**. CORS allows the Vite dev server (`http://localhost:5173`).

## Database setup

The schema comes from `database/init-postgres.sql`. Newer structures (Google sign-in column, refresh tokens, permissions) are
SQL files in `backend/src/main/resources/db/migration/`. **Flyway is not used**; apply the files yourself, in numeric order,
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

On Windows, run the same files one by one with `psql ... -f <file>` in the order V2, V3, V4, V5, V6.

`V5` renames the legacy `USER` role to `CUSTOMER` and `V6` seeds the permission catalogue and the role → permission
mapping. Without them the roles and permissions are incomplete.

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
sh ./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

`./mvnw` alone fails on Linux/macOS because the file is committed without the executable bit; use `sh ./mvnw` or
`chmod +x mvnw`. The API is then available at `http://localhost:8081`.
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
cd backend && sh ./mvnw test

# Frontend
cd frontend
npm run lint                           # currently reports 4 pre-existing errors on main
npm run build                          # production build
node scripts/verify-auth-refresh.mjs   # token-refresh logic, no network needed
```

Manual smoke test: `curl -i http://localhost:8081/api/products` returns `200` with a JSON array; register a user, sign in,
add a product to the cart, check out, and open the order in the order history. As an admin, open `/admin`.

## Common issues

- **Backend exits at startup mentioning the JWT secret** — `JWT_SECRET` is missing or shorter than 32 bytes.
- **`Connection refused` or `password authentication failed`** — the backend uses port 5432 and the `application.properties`
  password by default; with Docker set the `SPRING_DATASOURCE_*` variables (Database setup, step 3).
- **`./mvnw: Permission denied`** — use `sh ./mvnw ...` or `chmod +x backend/mvnw`.
- **`npm ci` fails with "package.json and package-lock.json are not in sync"** — use `npm install`.
- **Vite fails to start with a Node engine error** — upgrade to Node 20.19+ or 22.12+.
- **Google button is disabled, or Google sign-in answers 503** — set `VITE_GOOGLE_CLIENT_ID` in `frontend/.env` (and rebuild/restart
  Vite) and `GOOGLE_CLIENT_ID` for the backend, using the same Web client ID.
- **Roles look wrong or the admin menu is missing** — make sure migrations `V4`–`V6` were applied and that the user has the
  `ADMIN` role, then sign in again.
- **HTTP 429 on sign-in** — five failed attempts per username and IP lock sign-in for 15 minutes.
- **The storefront is empty** — there are no products yet; add them as admin or load `database/seed-data.sql` on a disposable DB.

## Development notes

- Only `/api/v1/auth/*` follows the new API convention (`{ data, meta }` success, `{ error }` failure); the other `/api/**`
  endpoints are older and return plain JSON. The rules for moving them are in [`CLAUDE.md`](./CLAUDE.md).
- Permission codes live in one class, `security/Permissions.java`. Roles and their permissions are stored in the database.
- Hibernate runs with `ddl-auto=update` in development, so the entities and the SQL files must be kept consistent.
- Reference: [`API.md`](./API.md) (endpoints), [`DATABASE.md`](./DATABASE.md) (tables), `docs/ai/contracts/` (authentication,
  API foundation and RBAC contracts).

## Contributing

1. Create a branch from `main` (`feature/<name>`, `fix/<name>` or `chore/<name>`); do not commit to `main` directly.
2. Keep the change focused, follow the conventions in [`CLAUDE.md`](./CLAUDE.md), and update the documentation your change
   affects.
3. Run the backend tests, `npm run lint` and `npm run build` before opening a pull request, and describe what you ran.
4. Never commit secrets or a real `.env` file.
