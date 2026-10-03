# EcoGreen — Agent Prompts V2

> Self-contained specification for AI coding agents working on the EcoGreen repository.
> Stack (fixed): **React 19 + Vite + React Router (JavaScript/JSX)** · **Spring Boot 4.0.x, Java 21, Maven** · **PostgreSQL**.
> Baseline inspected: `main` @ `ce04d30`. The existing source code is the implementation reality; this file is the target specification.
> This file does NOT replace `docs/03_AGENT_PROMPTS.md` (V1). V1 is left untouched.

## What changed from V1

1. **Code-first workflow.** Every module prompt now follows `READ → INSPECT CURRENT CODE → IDENTIFY CONFLICTS → PLAN → IMPLEMENT → TEST WHAT IS POSSIBLE → REVIEW DIFF → REPORT`. V1 assumed the code already matched the prompt; it does not.
2. **B13 is deferred.** Docker/Redis/RabbitMQ/CI/Jacoco/Actuator/observability are a later phase (B13) and block nothing in B01–B12. Where a module needs a *minimal* piece (e.g. running its migration), the prompt says exactly how little to do.
3. **Corrected dependency order.** V1 had B04 (Checkout) calling `createPendingOrder()` and `PENDING_PAYMENT` that only B05 creates, and B03 depending on B02 for no technical reason. V2 distinguishes **hard / soft / temporary-compatibility** dependencies and reorders: `B03 → B05 → B04`.
4. **Explicit optimization requirements.** Each relevant module has an "Efficiency review" checklist (N+1, pagination, indexes, client-side filtering, re-renders…) with the rule *evidence first, no speculative infrastructure*.
5. **Stronger migration compatibility.** Every migration documents legacy-row impact, nullability, constraint ordering, data-loss risk, empty-DB vs existing-DB behaviour. V2 fixes real V1 data-integrity bugs (legacy `CONFIRMED` mis-mapped, legacy `PENDING` stock double-deduction, missing order price columns, Hibernate-generated CHECK constraints, `ddl-auto=update` vs manual migrations).
6. **One consistent API contract.** One target convention (`/api/v1`, `{data,meta}`, `{error:{...}}`, 0-based pagination) with a deterministic transition rule for legacy `/api/**` endpoints. V1 mixed `/api`, `/api/v1`, bare arrays and envelopes.
7. **Stronger frontend/backend coordination.** A backend change that alters a contract must either migrate its frontend callers in the same task or keep a documented temporary compatibility path. Every module has a "Frontend impact" section.
8. **Explicit Git/export workflow.** The project owner handles Git/GitHub. Agents never push, merge, force-push or rewrite history; they report changed files and/or export a patch.
9. **Clearer scope control.** Each module has ALLOWED / REQUIRED-TO-CHECK / FORBIDDEN lists using the real repository layout (`backend/src/main/java/com/example/backend/...`, `frontend/src/...`). The nonexistent `modules/*` and `apps/web/*` layouts are gone.
10. **Truthful test reporting.** Agents must state which tests were actually run, which could not run and why. Claiming a test passed that was not executed is a violation.

---

## 0. How to use this document

- Give a coding agent: **Section 1 (Global Rules)** + **Section 2 (Dependency model)** + **exactly one module prompt**.
- Do not give an agent several modules at once.
- Section 1 rules override anything in a module prompt that appears to conflict, unless the module prompt explicitly says "OVERRIDES GLOBAL RULE X".
- Product-owner decisions that are still open are listed in **Section 12**. If an open decision blocks you, use the stated default and report it. Do not invent a different one.

---

## 1. GLOBAL RULES (apply to every module)

### 1.1 Mandatory workflow

```
READ → INSPECT CURRENT CODE → IDENTIFY CONFLICTS → PLAN → IMPLEMENT → TEST WHAT IS POSSIBLE → REVIEW DIFF → REPORT
```

1. **READ**: `CLAUDE.md`, this file (Section 1, 2, your module), and the existing docs under `docs/`.
2. **INSPECT CURRENT CODE**: open every file listed under "REQUIRED TO CHECK" in your module. Do not assume the code matches this prompt. Run `git status`/`git log -1` (read-only) to know the baseline.
3. **IDENTIFY CONFLICTS**: write down every difference between this prompt and the real code *before* editing. Preserve the **business requirement**; adapt the **implementation** to the real structure. Do not silently choose the prompt over the code or the code over the prompt.
4. **PLAN**: list the files you will create/modify (must be inside ALLOWED), the migrations, the API changes, the frontend changes, the tests. Keep the plan short.
5. **IMPLEMENT**: smallest change that satisfies the requirement. No framework changes, no unrelated renames, no broad refactors.
6. **TEST WHAT IS POSSIBLE**: see 1.8.
7. **REVIEW DIFF**: read your own full diff. Look for: files outside ALLOWED, leftover debug code, secrets, weakened security, missing tests, contract changes without frontend updates.
8. **REPORT**: use the report template in 1.10.

If a prerequisite is missing: implement **only the minimum** the current module needs, mark it `// TEMPORARY-COMPAT(<module>): <why>; remove when <condition>`, and list it in the report. Never expand into the B13 roadmap.

### 1.2 Fixed technology (do not change)

React + Vite + React Router + JavaScript/JSX; Spring Boot + Java 21 + Maven; PostgreSQL. No Next.js, NestJS, TypeScript, Gradle, MySQL, or new ORM. Adding a library is allowed only when the module prompt names it or you justify it in the report as the smallest way to meet a requirement (e.g. a JWT library, a Google token verifier, `spring-boot-starter-validation`).

### 1.3 Git / export workflow (the project owner manages Git)

Agents MUST NOT: push, force-push, merge, rebase, rewrite history, delete/modify other branches, create PRs, or require GitHub write access.
Agents MAY (only if the owner's environment allows, and only locally): `git status`, `git diff`, `git log`, and create a local working branch if the owner asked for one.
At completion, give the owner an integration-ready result using ONE of:
- a list of changed/created/deleted files with a one-line reason each, **and/or**
- a patch: `git diff > <module>.patch` (only if a git working tree exists and the owner asked), **or** a clear file listing so the owner can copy the files.
Never commit unless the owner explicitly says so. Do not modify `.git/`.

### 1.4 Scope control (applies to every module)

Each module defines **ALLOWED**, **REQUIRED TO CHECK**, **FORBIDDEN**.
- A file outside ALLOWED may be changed only if (a) the change is the minimum needed for the module to compile or for a contract change to not break the frontend, (b) you explain why in the report under "Shared/out-of-scope changes", and (c) you name which module owns that file. Never leave two competing implementations of the same thing.
- Shared files (owner module in parentheses): `pom.xml` (whichever module adds its own dependency; additive only), `application.properties` (additive only; never change existing values without a stated reason), `GlobalExceptionHandler` and API envelope classes (**B01-F1**), `frontend/src/services/http.js` and `AuthContext.jsx` (**B01**), `.env.example` (additive; every new env var must be added), `docs/ai/contracts/*` (the owning module only).
- No refactor merely because another architecture seems better.

### 1.5 Repository reality (what exists today — verify, it may have changed)

- Backend package `com.example.backend` with layered folders: `controller/ service/ repository/ entity/ dto/ security/ exception/ config/`. No `modules/` folder. Controllers use `@Autowired` field injection and `Map<String,Object>`/`Map<String,String>` request bodies. DTOs expose `from(...)` static factories.
- Auth today: `AuthTokenStore` (in-memory `ConcurrentHashMap<UUID,userId>`), `AuthInterceptor` (resolves bearer → `CurrentUser(userId, isAdmin)`, registered on `/api/**`), `AuthGuard.requireUser/requireAdmin/isAdmin`, roles `USER`/`ADMIN` (`Role.USER`, `Role.ADMIN` constants, seeded by `DataLoader` and `init-postgres.sql`), BCrypt via `PasswordHasher`/`spring-security-crypto`.
- Schema today: `database/init-postgres.sql` (run by docker entrypoint) **plus** `spring.jpa.hibernate.ddl-auto=update`. No Flyway/Liquibase. Money is `DECIMAL(12,2)`/`BigDecimal`. Order status `PENDING|CONFIRMED|PAID|CANCELLED`; Product status `ACTIVE|INACTIVE`; Payment status `PENDING|SUCCESS|FAILED`; Return status `PENDING|APPROVED|REJECTED`.
- Stock today: `products.stock_quantity`, decremented in `OrderService.checkout()` at order creation (not at payment). **Nothing ever restores stock** (cancel/return do not).
- Checkout today: `POST /api/orders` body `{customerName, customerPhone, shippingAddress, paymentMethod}`; creates `Order(PENDING)`, `OrderItem`s with price snapshot, a `Payment(PENDING)`, clears the cart. `PaymentService.payNow()` is a mock that always succeeds and sets order `PAID`.
- Frontend: `frontend/src/{pages,components,context,services,utils}`; axios instance `services/http.js` (adds `Authorization: Bearer <localStorage.token>`, normalises errors to `friendlyMessage` using `error.response.data.message`); `AuthContext` stores `token` and `user` in `localStorage`; `ProtectedRoute` is UX only; `Home.jsx` loads **all** products and filters client-side; `AdminDashboard.jsx` (~1100 lines) loads all products/orders/returns and filters client-side; product images are bare filenames resolved by `utils/imageResolver.js` against `src/assets/products/` or full URLs.
- Tests today: one empty `contextLoads()`. Frontend has no tests and no test runner.
- Secrets today: DB password `123456` is committed in `application.properties`/`docker-compose.yml`; `database/seed-data.sql` contains `admin/admin123`. **Do not make this worse; do not add new committed secrets.**

### 1.6 ONE project-wide API contract

**Target convention** (all new/rewritten endpoints MUST follow it; no module may invent another):

| Aspect | Rule |
|---|---|
| Base path | `/api/v1/...` |
| Resources | plural nouns, kebab-case (`/api/v1/order-items`); no verbs in paths except explicit state actions as sub-resources (`POST /orders/{id}/cancel`) |
| Success (single) | `{ "data": { ... }, "meta": null }` |
| Success (list) | `{ "data": [ ... ], "meta": { "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 } }` |
| Error | `{ "error": { "code": "VALIDATION_ERROR", "message": "…", "fields": { "price": "must be >= 0" } } }` (`fields` only for validation) |
| Error codes | 400 `VALIDATION_ERROR`, 400 `BUSINESS_RULE_VIOLATION`, 401 `UNAUTHENTICATED`, 403 `FORBIDDEN`, 404 `NOT_FOUND`, 409 `CONFLICT`, 429 `RATE_LIMITED`, 500 `INTERNAL_ERROR` (never leak exception text/stack/SQL) |
| Pagination | every list endpoint: `page` (0-based, default 0), `size` (default 20, max 100), `sort=field,asc|desc`; whitelist sortable fields (never pass raw `sort` to the DB) |
| Money | JSON number in VND (`BigDecimal`, `DECIMAL(12,2)` in DB). **Decision: keep `DECIMAL(12,2)`; V1's "BIGINT money" convention is withdrawn** (it would rewrite every price column for no benefit). Never use `double`/`float`. |
| Time | UTC ISO-8601 in JSON; DB `TIMESTAMP`; see open decision D-9 about the legacy `Asia/Ho_Chi_Minh` JVM default |
| IDs | numeric `id` in paths; no sequential-ID trust for authorization — ownership is always checked server-side |
| Validation | `jakarta.validation` on request DTOs (add `spring-boot-starter-validation` once, in B01-F1), typed request DTOs instead of `Map<String,…>` for every endpoint you create or rewrite |

**Transition rule for legacy endpoints (`/api/**`, bare JSON, `{message}` errors):**
1. Legacy endpoints keep working until the module that owns them rewrites them.
2. A module that rewrites an endpoint creates the `/api/v1` version **and migrates every frontend caller in the same task**, then removes the legacy path — unless the module prompt lists a `TEMPORARY-COMPAT` alias with a removal condition.
3. `GlobalExceptionHandler` produces the new error shape **only for requests under `/api/v1/**`**; legacy paths keep `{message}` so the existing frontend is not broken mid-migration. (**Owner: B01-F1.**)
4. `frontend/src/services/http.js` unwraps `{data,meta}` and normalises `{error}` **only for URLs starting with `/api/v1/`**; other URLs are untouched. After unwrap, service functions return the plain value (or `{items, meta}` for lists) so page components stay simple. (**Owner: B01-F1.**)
5. Final state (end of B12): no legacy `/api/**` endpoint remains except explicitly documented compat aliases.

**Contract documents.** Every module publishes ONE contract file at **`docs/ai/contracts/<module>.md`** (e.g. `docs/ai/contracts/B05-order.md`). It contains: entities/columns, public Java signatures other modules may call, endpoints (path, method, request, response, errors, permission), events, status enums, and "Requests for contract changes" (other modules append requests here; they do not edit the owner's code). Do **not** create `modules/*/CONTRACT.md`.

### 1.7 Database migration policy

**Two separate things — do not conflate them:**
- **Migration FILE definition** (always required when schema changes): versioned SQL at `backend/src/main/resources/db/migration/V<n>__<snake_name>.sql` (Flyway-compatible naming), inside your assigned version range (Section 3). One file = one logical change. Never edit a migration that another module or the owner already applied; add a new one.
- **Migration EXECUTION infrastructure** (Flyway wiring, CI): deferred to B13. Until then:
  - Migration files MUST be **idempotent and order-safe** (`ADD COLUMN IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS`, `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`, guarded `UPDATE`s) because `ddl-auto=update` may already have created columns/tables from entities, and the owner may run files by hand with `psql`.
  - Do not change `spring.jpa.hibernate.ddl-auto`. Add new entities/columns **consistently in both** the JPA entity and the migration file so either path yields the same schema.
  - Hibernate 6 may have generated its own CHECK constraints for `@Enumerated(STRING)` columns (names such as `orders_status_check`, `products_status_check`) on databases created by `ddl-auto` rather than by `init-postgres.sql`. Before widening any enum, a migration must drop **both** the `init-postgres.sql` constraint name (e.g. `chk_orders_status`) and any Hibernate-generated one (look them up in `pg_constraint` inside a `DO $$ … $$` block; do not hard-code a guess).
  - A module may run its own migration against a local database only if the environment allows; otherwise state "migration not executed".
- **Minimal Flyway activation (optional, owner-triggered).** If the owner asks for automatic execution before B13, the smallest acceptable change is: add `flyway-core` + `flyway-database-postgresql`, `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=<last manually applied>`, keep `ddl-auto=update` until B13 flips it to `validate`. Whoever does it records it in their report; other modules must not duplicate it.

**Every migration file MUST begin with a comment block documenting:**
```
-- LEGACY DATA IMPACT:        what happens to existing rows
-- NULLABILITY:               new NOT NULL columns: how existing rows are backfilled BEFORE the constraint
-- CONSTRAINT ORDER:          backfill → add constraint (never the reverse)
-- DATA-LOSS / ROLLBACK RISK: what cannot be undone; the manual rollback SQL or "none possible"
-- EMPTY DATABASE:            works? (yes/no + why)
-- EXISTING DATABASE:         works? (yes/no + why)
```
Do not drop a column that contains data in the same release that stops using it; mark the entity field `@Deprecated`, stop writing it, and schedule removal in B13 or a later explicit task.

### 1.8 Testing rules (truthful reporting)

- Tests prove **business behaviour**, not coverage. Prefer: JUnit 5 + Mockito for services; `@SpringBootTest`/`@DataJpaTest` with real PostgreSQL only when concurrency or SQL behaviour is the point (see B03).
- If Testcontainers/Docker is unavailable, concurrency/SQL tests that need a real DB may be written but marked `@Disabled("requires PostgreSQL")` **only with the reason in the report**; the report must say they were NOT run.
- Frontend has no test runner. **Do not add one unless the module prompt says so.** Minimum frontend verification: `npm run lint` and `npm run build` where the environment allows, plus the manual verification steps in the module's DoD.
- Things an environment may not allow: Maven Central/npm registry access, Docker, Google credentials, payment sandbox credentials, SMTP. State which applied.
- **Never claim a test/build/lint passed unless you ran it and saw it pass.** Report: command, result, and for failures the honest reason. Do not delete or weaken existing tests to get green.

### 1.9 Cross-cutting requirements

**Security (always consider):** authentication; authorization at the backend (frontend guards are UX only); ownership checks on every `/{id}` resource that belongs to a user; JWT/token validation; secrets only from environment (never committed; add the variable *name* to `.env.example`); request validation; file-upload validation (type allow-list by content sniffing not just extension, size limit, random server-side filename, no path from client, no executable content served); webhook signature verification and idempotency; injection safety (parameterised queries/JPQL only, no string-concatenated SQL, whitelist `sort`); no sensitive data in responses/logs (no password hashes, tokens, raw gateway payloads to clients); no unsafe error messages; never trust client-supplied price/total/status/userId/email. **Never weaken security to make a test pass or to keep an old client working.**

**Efficiency review (evidence first):** in each module do a short, concrete review of the code you touch for the items under that module's "Efficiency review". Rules:
1. Do **not** optimise blindly. For each change, report: *what was inefficient, why (evidence: query count, payload size, loop), what changed, the trade-off.*
2. Prefer: `JOIN FETCH`/`@EntityGraph` or batched queries over per-row lookups; pagination over `findAll()`; DB-side filtering over client-side; indexes backed by an actual query; smaller DTOs.
3. **Do not introduce Redis, caches, queues, or new infrastructure** for the sake of optimisation. An in-method precomputation or a query rewrite is fine; a new service is not.
4. If you find an inefficiency outside your scope, **report it**, do not fix it.

**Legacy compatibility (every migration/breaking change):** state in the report (a) what happens to existing rows, (b) what happens to old clients, (c) whether compat code is temporary and the **removal condition**, (d) which data fields you deliberately kept.

### 1.10 Completion report template (mandatory)

```
MODULE: <Bxx name>
1. Summary of what changed (business terms)
2. Conflicts found between this prompt and the code, and how each was resolved
3. Files created / modified / deleted (path — reason)
4. Shared/out-of-scope changes (path, why, owning module)
5. Migrations (file, range check, legacy impact, empty-DB ok?, existing-DB ok?, executed? yes/no)
6. API changes (old → new, status codes, error codes, permissions) + frontend callers updated (paths)
7. Frontend changes (files, behaviour)
8. Efficiency review (for each item: finding → evidence → change → trade-off, or "nothing found")
9. Security review (items considered, residual risks)
10. Tests: written (names) / EXECUTED (command + result) / NOT EXECUTED (reason)
11. Build/lint: executed? result?
12. TEMPORARY-COMPAT code (location, why, removal condition)
13. Known limitations / open decisions hit (Section 12 IDs) / defaults used
14. Contract file published: docs/ai/contracts/<module>.md
15. Suggested next module
16. How the owner integrates the result (file list or patch)
```

---

## 2. Dependency model (re-derived from the actual code)

**Definitions**
- **HARD**: module cannot be implemented/merged correctly without the other being merged first.
- **SOFT**: module works without it (using a documented fallback); integrating later is an improvement.
- **TEMP-COMPAT**: module needs a stand-in for something not yet built; the stand-in is minimal, labelled, and removed by a named later module.

### 2.1 What the code shows (and what V1 got wrong)

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

### 2.3 Required development order (implementation dependency order — NOT a priority ranking)

```
Step 1   B01-P1   Google login hotfix                 (can start immediately; independent)
Step 2   B01-F1   API foundation (envelope/errors/validation/pagination/http.js)
Step 3   B01-P2   JWT access + refresh tokens, /api/v1/auth, frontend auth migration
Step 4   B01-P3   Permission-based RBAC
Step 5   B02      Catalog                             ┐ may run in parallel
Step 6   B05      Order state machine (+InventoryGateway port, legacy adapter)   ┘ (different files)
Step 7   B03      Inventory reservation model (replaces adapter)
Step 8   B08      Customer management (addresses, enable/disable)
Step 9   B04      Cart & Checkout (discount, shipping, address; replaces legacy POST /api/orders)
Step 10  B06      Payment (gateway abstraction, webhook, refunds)
Step 11  B07      Return & Refund
Step 12  B09      Analytics            ┐ may run in parallel, read-only
Step 13  B10      Notification         ┘
Step 14  B11/B12  Frontend slices — each slice runs right after the backend module it needs (B11/B12 detail below);
                  they are NOT a single step at the end
Step 15  B13      Deferred infrastructure (Flyway activation, Docker, CI, Redis/RabbitMQ only if justified, …)
Step 16  FINAL    Cross-system efficiency pass (report-driven; see Section 10)
```
Rationale for the order: **business correctness** (order state machine, inventory) before **security hardening of every flow** is already satisfied by Steps 1–4 (security-critical auth work is first and independent); **data integrity** (B05→B03→B04→B06→B07) follows the money/stock chain; **API contracts** are fixed once in Step 2 and reused; **frontend integration** happens inside each module (minimum caller migration) plus B11/B12 slices; **analytics/notification** consume stable events; **infrastructure** and **final optimisation** come last.

---

## 3. Migration version ranges (namespaces, not execution order)

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
4. Idempotency, documentation header and EMPTY/EXISTING-database statements are mandatory (Section 1.7).

---

# MODULE PROMPTS

Every module prompt below uses the same 13-point structure: **Problem · Why it matters · Required behaviour · Inspect · Database · API · Frontend · Security · Compatibility/migration · Tests · Must NOT change · Definition of Done · Report**, preceded by Dependencies and Scope. Section 1 (Global Rules) applies in full to each one.

---

## B01 — Authentication & RBAC

B01 is delivered in **four ordered sub-tasks**. Give an agent ONE sub-task at a time. P1 is independent and can start immediately.

### B01-P1 — Google login security hotfix (frontend + backend, one task)

**Dependencies**: none (HARD: none · SOFT: none).

**Problem (current code).** `AuthService.loginWithGoogle()` never verifies anything with Google. It base64-decodes the middle part of `request.credential` with a regex (`extractJsonField`), silently ignores any failure (`catch (Exception ignored)`), and then trusts `request.email`, `name`, `googleId`, `avatar` supplied by the client. The frontend (`services/googleAuth.js`) makes it worse: it uses the OAuth **access-token** flow (`initTokenClient`), calls Google's userinfo endpoint in the browser, and sends `{email, name, googleId, avatar, credential: <access_token>}`. The `credential` is not an ID token, so the decode fails silently and the backend falls back to the client-supplied `email`. **Any caller can `POST /api/auth/google {"email":"admin@ecogreen.vn"}` and receive a valid session for that account.** The frontend also lets the user store a Google client ID in `localStorage` (`ecogreen_google_client_id`).

**Why it matters.** Full authentication bypass, including for ADMIN accounts. This is the most severe defect in the repository and is independent of every other module.

**Required behaviour.**
1. The backend accepts **only** a Google **ID token** (`{ "idToken": "<jwt>" }`) and verifies it cryptographically: signature against Google's published keys, `iss` ∈ {`https://accounts.google.com`, `accounts.google.com`}, `aud` == server-configured `GOOGLE_CLIENT_ID`, `exp` not passed, and `email_verified == true`.
2. Identity data (email, name, `sub`, picture) is taken **only from the verified payload**. Any other client-supplied identity field is ignored/rejected (remove `email/name/googleId/avatar/credential` from the request DTO).
3. Matching rule: look up the user by `google_sub` first; else by verified email. If a user is matched by email and has no `google_sub`, link it (store `google_sub`). If a user already has a *different* `google_sub`, reject. Auto-created users get a random unusable password and the default customer role (`USER` today).
4. If `GOOGLE_CLIENT_ID` is not configured the endpoint returns an error (503/500 with a safe message) rather than accepting unverified input. **Fail closed.**
5. The frontend switches to the Google Identity Services **ID-token** flow (`google.accounts.id.initialize` + `prompt`/`renderButton`, callback receives `response.credential`) and sends only `{ idToken }`. The client ID comes from `VITE_GOOGLE_CLIENT_ID` only; remove the `localStorage` client-ID override and any UI that lets users type a client ID (inspect `components/GoogleOAuthModal.jsx`; if it only exists to capture the client ID, remove its usage from `Login.jsx`/`Register.jsx`, not necessarily the file).

**Inspect (REQUIRED TO CHECK).** `service/AuthService.java`, `controller/AuthController.java`, `dto/GoogleAuthRequest.java`, `dto/AuthResponse.java`, `entity/User.java`, `repository/UserRepository.java`, `security/AuthTokenStore.java`, `frontend/src/services/googleAuth.js`, `services/authApi.js`, `context/AuthContext.jsx`, `pages/Login.jsx`, `pages/Register.jsx`, `components/GoogleOAuthModal.jsx`, `.env.example`, `pom.xml`.

**Database.** Migration `V2__users_google_sub.sql`: `ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(64); CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_sub ON users(google_sub) WHERE google_sub IS NOT NULL;` Header block per 1.7. Legacy impact: existing users get `NULL` (no linking until their next Google login); empty-DB and existing-DB both fine; rollback = drop index and column (no data lost except links). Add `private String googleSub` to `User` (column `google_sub`, nullable).

**API.** Keep the **legacy path** `POST /api/auth/google` (TEMP-COMPAT; B01-P2 moves it to `/api/v1/auth/google`). Request `{ "idToken": "…" }`; response unchanged for now (`{token, user}`) so the rest of the frontend keeps working. Invalid/forged/expired token → 401 `{message}` (legacy shape).

**Frontend.** `googleAuth.js`, `Login.jsx`, `Register.jsx` (and `GoogleOAuthModal.jsx` only as needed), `authApi.js#loginWithGoogle(idToken)`. Load the GIS script only on the pages that need it (not globally in `index.html` unless already there). Verify the user-visible flow still logs in.

**Security.** Verifier library: use the smallest reliable option (e.g. `com.google.api-client:google-api-client` `GoogleIdTokenVerifier`, or another maintained JOSE library). Never log the token. Do not accept `alg=none`. Do not trust a client-provided `aud`. Do not auto-link on `email_verified=false`. **Do not auto-create or link an account that holds the ADMIN role through Google unless `email_verified` is true** (state this in the report; owner may choose to forbid Google login for ADMIN entirely — default: allowed only when verified).

**Compatibility/migration.** Old frontend builds that send `{email,…}` are **intentionally broken** (that is the vulnerability). The frontend is migrated in this same task. Existing Google-created users (random password, no `google_sub`) keep working: matched by verified email and linked. Compat code: none permanent.

**Tests (mandatory, JUnit 5 + Mockito, in `backend/src/test/java/com/example/backend/service/`).** Inject/mocked verifier so no network is needed:
`loginWithGoogle_forgedOrUnverifiableToken_throwsUnauthorized`, `…_wrongAudience_…`, `…_emailNotVerified_…`, `…_requestWithOnlyEmailField_isRejected` (the old exploit), `…_validToken_existingUserByEmail_linksSubAndIssuesSession`, `…_validToken_newUser_createsUserAndCart`, `…_subMismatch_throwsUnauthorized`, `…_clientIdNotConfigured_failsClosed`.
Frontend: `npm run lint` + `npm run build` if possible; manual: Google login works with a real client ID **or** state it could not be tested without credentials.

**Must NOT change.** Password login, token store, JWT/refresh (that is P2), other controllers, DB schema other than `users.google_sub`.

**ALLOWED**: files in "Inspect" plus the new migration and tests. **FORBIDDEN**: all other controllers/services, order/payment/product code.

**Definition of Done.** (1) The exploit request above returns 401. (2) No identity field is read from the request body. (3) `GOOGLE_CLIENT_ID` and `VITE_GOOGLE_CLIENT_ID` added to `.env.example` (names only). (4) All listed tests pass (or are reported as not run with reason). (5) Frontend builds and still logs in with email/password. (6) Report states the residual risk that the access token is still in-memory/`localStorage` (addressed by P2).

**Report.** Section 1.10 template + the explicit sentence "forged-token exploit: reproduced before / blocked after" (describe how verified).

---

### B01-F1 — API foundation (shared, permanent)

**Dependencies**: none. **Blocks**: every module that creates `/api/v1` endpoints.

**Problem.** There is no shared API contract: controllers return bare lists/objects and `Map<String,…>` bodies, errors are `{message}`, `GlobalExceptionHandler` maps *every* `RuntimeException` to 400 with `ex.getMessage()` (leaks internal messages such as `NumberFormatException` text, `NoSuchElement`, constraint violations), there is no validation, no pagination, and `http.js` knows only the legacy shapes.

**Why it matters.** Without one shared foundation each module would invent its own envelope/error/paging format, and unsafe exception messages reach clients.

**Required behaviour.** Implement the contract in Section 1.6, **additively**:
1. Backend (package `com.example.backend.api`): `ApiResponse<T>` (`data`, `meta`), `PageMeta`, `ApiError`/`ApiErrorBody` (`code`, `message`, `fields`), a `PageResponse` helper to convert Spring `Page<T>` → `{data, meta}`, a safe `sort` whitelist helper, and an `ApiErrorCode` enum.
2. `GlobalExceptionHandler`: for request paths starting with `/api/v1/` return the new error shape with the mapped `error.code` (Section 1.6); handle `MethodArgumentNotValidException`/`ConstraintViolationException`/`HttpMessageNotReadableException` → `VALIDATION_ERROR` with `fields`; map existing `BadRequestException`→`BUSINESS_RULE_VIOLATION`? **No:** keep the existing exception classes, and add `BusinessRuleViolationException` (400) and `InsufficientStockException`-style exceptions later in their modules; `BadRequestException` on `/api/v1` → `VALIDATION_ERROR`. For **all** paths: the catch-all `RuntimeException`/`Exception` handlers must return 500 `INTERNAL_ERROR` with a generic message and log the real exception server-side. **For legacy paths keep `{message}` and keep today's status codes** except that unknown runtime exceptions must no longer leak their text (message becomes the generic Vietnamese system-error text already used in `handleGeneralException`). Report any legacy behaviour this changes.
3. Add `spring-boot-starter-validation` to `pom.xml`.
4. Frontend `services/http.js`: for URLs starting with `/api/v1/`, response interceptor unwraps `{data, meta}` (single → value; list → `{items, meta}`) and error interceptor normalises `{error:{code,message,fields}}` into the existing `friendlyMessage` plus `code` and `fields`; non-`/api/v1/` URLs behave exactly as today. Do not add the refresh/token logic here (P2).
5. `docs/ai/contracts/B01-api-foundation.md`: documents the helpers and how a module returns a paged list.

**Inspect.** `exception/*`, `controller/*` (read-only, to see current shapes), `frontend/src/services/http.js` and every `services/*Api.js` (to see what unwrapping would break), `pom.xml`.

**Database.** None.

**API.** No endpoint changes. Provide a tiny test-only or documented example. **Do not** convert existing endpoints in this task.

**Frontend.** Only `services/http.js` (dual-mode as above).

**Security.** The 500 handler must never expose exception text/stack/SQL/class names; log with a correlation id (a random id returned in `error.traceId` is allowed). Validation error `fields` must not echo secrets (never echo `password`).

**Compatibility.** No existing endpoint/consumer changes except stopping the message leak (report it). Dual-mode is permanent until the end of B12, then legacy branch can be deleted.

**Efficiency review.** The paged-list helper must not call `count` twice or load the whole table; confirm `Pageable` size is clamped to 100 server-side.

**Tests.** `GlobalExceptionHandler` tests via `MockMvc` standalone: validation failure on `/api/v1/**` → 400 `VALIDATION_ERROR` + `fields`; unknown runtime exception → 500 `INTERNAL_ERROR` without original text on both v1 and legacy paths; legacy `ResourceNotFoundException` → 404 `{message}`; `PageResponse` meta correct; `size=1000` clamped to 100. Frontend: `npm run build`; manual check that existing pages still work (legacy dual-mode).

**Must NOT change.** Business logic, entities, existing endpoint paths/bodies, auth.

**ALLOWED**: new `api/` package, `exception/GlobalExceptionHandler.java` and new exception classes, `pom.xml` (add validation starter), `frontend/src/services/http.js`, tests, the contract doc. **FORBIDDEN**: controllers/services/entities of other modules.

**Definition of Done.** Helpers exist and are documented; legacy frontend still works; no exception text leaks on any path; tests pass or are reported as not run; contract doc published.

---

### B01-P2 — JWT access token + refresh token + `/api/v1/auth` (backend + frontend, one task)

**Dependencies**: HARD: B01-F1. SOFT: B01-P1.

**Problem.** Sessions are random UUIDs in a `ConcurrentHashMap` (`AuthTokenStore`): lost on restart, not shareable across instances, never expire, cannot be listed or revoked per user, and there is no refresh. `AuthController.login` re-queries the user after login; `logout` reads the raw header; `register` returns a bare `UserResponse`. The frontend stores `token` and `user` in `localStorage`, has no refresh logic, and `getMe` calls `/users/me`.

**Why it matters.** Restart logs everyone out, tokens live forever, disabling an account cannot revoke refresh capability, and horizontal scaling is impossible.

**Required behaviour.**
1. **Access token**: signed JWT (HS256 or stronger), TTL ~15 min, claims `sub` (user id), `roles`, `permissions` (empty list until P3), `iat`, `exp`, `jti`. Secret from env `JWT_SECRET` (≥32 bytes). **Fail fast at startup** with a clear message if it is missing/short; no default secret in committed code. Provide a **test-only** secret in `src/test/resources` clearly named as such.
2. **Refresh token**: opaque random (≥64 bytes, URL-safe), returned once; only its **SHA-256 hash** is stored (`refresh_tokens`); TTL ~14 days; **rotation**: each refresh issues a new refresh token and revokes the old; presenting an already-revoked token revokes the user's whole token family (reuse detection) and returns 401. If you simplify (no rotation) you must justify in the report.
3. `AuthInterceptor` validates the JWT (signature, expiry), builds `CurrentUser(userId, roles, permissions)`, and still rejects deactivated users using a cheap lookup (existence + `is_active` by id; **do not** load the full `User` with eager roles on every request unless measured necessary; report the trade-off vs. trusting claims for up to 15 min).
4. Endpoints under **`/api/v1/auth`**: `POST register`, `POST login`, `POST google`, `POST refresh`, `POST logout`, `GET me` (contract below). `login` must not query the user twice; unified error message for bad username/password (no user enumeration; keep the existing Vietnamese texts).
5. Legacy `/api/auth/*` and `/api/users/me` are **removed in this task** (the frontend is migrated here). `DELETE /api/users/{id}` and `GET /api/users` stay (owned by B08).
6. `AuthTokenStore` is deleted (or reduced to nothing) once nothing references it.
7. Frontend: `AuthContext` stores `accessToken`, `refreshToken`, `user`; `http.js` request interceptor sends the access token; response interceptor on 401 `UNAUTHENTICATED` performs **one** refresh (single-flight: concurrent 401s share one refresh promise; use `navigator.locks` if available for multi-tab) then retries the original request once; if refresh fails → clear storage and go to `/login`. `getMe` → `/api/v1/auth/me`. On boot, if a stored token is not JWT-shaped (legacy UUID session) clear it silently and treat the user as logged out.
8. Brute-force note: there is no rate limiter today. Add a minimal in-memory failed-login throttle (e.g., N attempts per username+IP window → 429 `RATE_LIMITED`) **only if** it can be done in <~80 lines without new infrastructure; otherwise report it as an open risk (do not add Redis).

**Contract (target shapes).**
```
POST /api/v1/auth/login        {username,password}
  → 200 {data:{accessToken, refreshToken, expiresIn, user:{id,username,email,active,roles,permissions}}, meta:null}
POST /api/v1/auth/register     {username,email,password}  → 201 {data:{id,username,email,active,roles,permissions}}   (preserves today's "register then go to /login" flow; do not auto-login unless Register.jsx's current behaviour is preserved)
POST /api/v1/auth/google       {idToken} → same as login
POST /api/v1/auth/refresh      {refreshToken} → 200 {data:{accessToken, refreshToken, expiresIn}}
POST /api/v1/auth/logout       {refreshToken} → 200 {data:null}   (revokes that refresh token; access token expires naturally)
GET  /api/v1/auth/me           → 200 {data:{id,username,email,active,roles,permissions}}
```

**Inspect.** `security/*`, `service/AuthService.java`, `service/PasswordHasher.java`, `controller/AuthController.java`, `controller/UserController.java`, `dto/AuthResponse.java`, `dto/UserResponse.java`, `config/DataLoader.java`, `frontend/src/context/AuthContext.jsx`, `services/http.js`, `services/authApi.js`, `services/userApi.js`, `pages/Login.jsx`, `pages/Register.jsx`, `components/Navbar.jsx`, `components/ProtectedRoute.jsx`, every service file that reads `localStorage.token`.

**Database.** `V3__refresh_tokens.sql`:
```sql
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,      -- hex SHA-256
  family_id VARCHAR(36) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT false,
  replaced_by_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens(family_id);
```
Legacy impact: new table, no existing rows. Existing sessions (UUID tokens) are invalidated — **all users must log in once** (document). Empty/existing DB both fine. Entity `RefreshToken`.

**Security.** Constant-time comparison where applicable; `jti`/hash never logged; token lifetimes from properties; `Authorization: Bearer` only; CORS unchanged (still `http://localhost:5173`; do not widen). **Residual risk to report:** tokens in `localStorage` are readable by any XSS; an httpOnly-cookie refresh token needs CORS credentials + CSRF handling and is an **open owner decision (D-5)** — do not switch silently.

**Compatibility.** Old UUID tokens: rejected (401) → frontend clears and shows login. Old client calling `/api/auth/*`: gets 404 (removed). No permanent compat code.

**Efficiency review.** Per-request DB hits in the interceptor (before/after count); `User.roles` is `FetchType.EAGER` — measure queries per login and per request; avoid loading roles when claims suffice; ensure refresh lookup uses the unique index.

**Tests.** `login_correctPassword_returnsValidJwtWithClaims`, `login_wrongPassword_throwsUnauthorizedWithoutUserEnumeration`, `jwt_expiredToken_isRejected`, `jwt_tamperedSignature_isRejected`, `jwt_missingSecret_failsFastAtStartup`, `refresh_validToken_rotatesAndRevokesOld`, `refresh_revokedToken_revokesWholeFamilyAndReturns401`, `refresh_expiredToken_returns401`, `logout_revokesRefreshToken`, `interceptor_deactivatedUser_isRejected`, `me_withoutToken_returns401`. Integration (MockMvc) for the six endpoints incl. envelope shape. Frontend: build + manual: login, wait/force expiry, confirm a protected call auto-refreshes once and retries; confirm 2 simultaneous 401s trigger one refresh.

**Must NOT change.** Authorization rules of other controllers (`requireAdmin` semantics); product/order/payment code; roles (P3).

**ALLOWED**: `security/`, `service/AuthService.java`, `controller/AuthController.java`, `controller/UserController.java` (only `me` removal), DTOs for auth, `entity/RefreshToken.java` + repository, migration, `config/` additive properties class, `pom.xml` (JWT library), `application.properties` (additive), `.env.example`, frontend auth files listed under Inspect, tests, contract doc. **FORBIDDEN**: any non-auth controller/service/entity, `init-postgres.sql`.

**Definition of Done.** All auth traffic uses `/api/v1/auth`; legacy auth paths removed; restart no longer logs users out; refresh works end-to-end; `JWT_SECRET` in `.env.example`; tests pass or reported as not run; `docs/ai/contracts/B01-auth.md` published (token claims, endpoint shapes, `CurrentUser`, how other modules authenticate).

---

### B01-P3 — Permission-based RBAC

**Dependencies**: HARD: B01-P2.

**Problem.** Authorization is `current.isAdmin()` only. Roles are `USER`/`ADMIN`, hard-coded as constants in `Role.java`, re-seeded by `DataLoader` at every start and by `init-postgres.sql`/`database/seed-data.sql`. There are no permissions, no manager role, and the frontend decides admin UI with `roles.includes('ADMIN')` (and an admin-dashboard role filter uses `USER`).

**Why it matters.** Staff cannot be given partial rights; every new module would repeat `requireAdmin()`.

**Required behaviour.**
1. Tables `permissions`, `role_permissions`; roles become `CUSTOMER` (renamed from `USER`), `MANAGER`, `ADMIN`.
2. One catalogue class `security/Permissions.java` holding **all** permission codes (`<resource>:<action>`, lowercase): `product:view|create|update|delete|publish`, `category:view|create|update|delete`, `inventory:view|update|adjust`, `order:view|view_all|update|cancel`, `payment:view|confirm|refund`, `return:view|process`, `user:view|update|disable`, `analytics:view`, `audit:view`, `system:configure`. No permission string literal may appear elsewhere in the codebase.
3. `ADMIN` gets everything; `MANAGER` gets everything except `system:configure`, `audit:view`, `user:disable` (owner decision D-6: default as stated); `CUSTOMER` gets none (customer rights are ownership-based, not permission-based).
4. `AuthGuard`: add `requirePermission(request, code)` and `requireAnyPermission(request, codes…)`; keep `requireAdmin()`/`isAdmin()` `@Deprecated` with unchanged behaviour (role `ADMIN`) so unmigrated controllers keep working. **Known consequence to document:** a `MANAGER` is denied by legacy `requireAdmin()` endpoints until the owning module migrates them.
5. Permissions are loaded efficiently: one query joining user → roles → permissions when issuing a token; never a per-permission query.
6. Rename-safe code: replace `Role.USER` usage in `AuthService`, `DataLoader` and any other place with `Role.CUSTOMER`; keep `Role.USER` as a `@Deprecated` constant **only if** something external needs it. `DataLoader` must seed the three new roles and must **not** re-create `USER`.
7. Frontend: `AuthContext` exposes `permissions`, `roles`, `hasPermission(code)`, `hasAnyPermission(...)`, and `isStaff` (ADMIN or MANAGER). `isAdmin` stays for existing callers. Update the admin dashboard role filter option `USER` → `CUSTOMER` (display text may stay Vietnamese) and any other literal `'USER'`. **Do not** redesign the admin UI here (that is B12).

**Inspect.** `entity/Role.java`, `entity/User.java`, `repository/RoleRepository.java`, `security/*`, `config/DataLoader.java`, `service/AuthService.java`, `service/UserService.java`, `dto/UserResponse.java`, `database/init-postgres.sql`, `database/seed-data.sql`, `frontend/src/context/AuthContext.jsx`, `pages/admin/AdminDashboard.jsx` (the `USER` literal), `pages/Login.jsx`/`Register.jsx` (`roles.includes('ADMIN')`).

**Database.** Files (all idempotent, ordered):
- `V4__permissions.sql` — `permissions(id, code UNIQUE, description)`, `role_permissions(role_id, permission_id, PK both)`.
- `V5__roles_customer_manager.sql` — **guarded rename**: if role `USER` exists and `CUSTOMER` does not → `UPDATE roles SET name='CUSTOMER'`; if **both** exist (possible if `DataLoader` created one) → move `user_roles` rows from `USER` to `CUSTOMER` (avoiding PK duplicates) then delete `USER`; then `INSERT … ON CONFLICT DO NOTHING` for `MANAGER`. Legacy impact: every existing user keeps their role via the same `roles.id`; no `user_roles` row lost. Rollback: rename back (document the SQL).
- `V6__seed_permissions.sql` — insert the catalogue and role mappings with `ON CONFLICT DO NOTHING`.
Update `database/seed-data.sql` **minimally** (role name `USER`→`CUSTOMER`, nothing else; do not change or add credentials; report that it still contains `admin/admin123`).
Empty DB: roles seeded by `V1` (`USER`,`ADMIN`) then renamed — fine; existing DB: fine.

**API.** No new public endpoints required (the `me` response from P2 now carries real `permissions`). Optionally `GET /api/v1/admin/roles` and `GET /api/v1/admin/permissions` (`system:configure`) — only if cheap.

**Security.** Permission data comes from the DB through the token, never from the client. A removed permission stays effective until the access token expires (≤15 min) — document; role/permission changes are infrequent. Disabled-user check from P2 stays.

**Compatibility.** Old role name `USER` disappears from API responses (`roles:["CUSTOMER"]`). Any client logic keyed on `'USER'` is updated in this task. `requireAdmin()` deprecation is the temporary compat; **removal condition: every controller migrated (end of B12)**.

**Efficiency review.** Count queries for login with 3 roles × N permissions (expect ≤2); avoid `EAGER` → `LAZY` flip unless the query count proves it and tests still pass; index `role_permissions(permission_id)` if joins need it.

**Tests.** `requirePermission_withoutPermission_throwsForbidden`, `requirePermission_withPermission_passes`, `requireAnyPermission_oneOfMany_passes`, `adminRole_hasAllPermissions`, `managerRole_lacksSystemConfigure_auditView_userDisable`, `customerRole_hasNoPermissions`, `roleRename_migrationKeepsUserRoleLinks` (SQL test needs PostgreSQL — if unavailable, provide the SQL and a manual verification query and report "not executed"), `dataLoader_doesNotRecreateUserRole`, `tokenClaims_containPermissionsFromAllRoles_deduplicated`. Frontend: build; manual: login as MANAGER, confirm `permissions` present and `isStaff` true.

**Must NOT change.** Other controllers' guards (they migrate in their own modules), the existing `requireAdmin()` behaviour, order/product logic.

**ALLOWED**: `entity/Role.java`, `entity/Permission.java`, repositories, `security/*`, `config/DataLoader.java`, `service/AuthService.java` (role constants only), `dto/UserResponse.java`, migrations V4–V6, `database/seed-data.sql` (role name only), frontend `AuthContext.jsx` + the one `USER` literal in `AdminDashboard.jsx`, tests, `docs/ai/contracts/B01-rbac.md`. **FORBIDDEN**: controllers other than auth, any module logic.

**Definition of Done.** Permissions catalogue is the only place codes are defined; roles renamed without losing user links; `requirePermission` available and tested; frontend exposes `hasPermission`; `docs/ai/contracts/B01-rbac.md` lists the catalogue, role→permission map, and the exact guard signatures for B02–B10; legacy `requireAdmin` deprecated but working.

---

## B02 — Catalog (Product, Category, Images, Search)

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

**Database.** (idempotent; header block per 1.7 on each)
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

**Report.** Section 1.10 + the SQL-statement counts before/after for the product list.

---

## B05 — Order (state machine, history, safe checkout core)

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

**Database.** (idempotent; header block per 1.7)
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

**Report.** Section 1.10 + (a) the legacy status counts before/after migration (or "not executed"), (b) the list of every code location that used to write `orders.status`, (c) query counts before/after for list endpoints.

---

## B03 — Inventory (reservation model, ledger, concurrency safety)

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

**Database.** (idempotent, header block per 1.7)
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

**Report.** Section 1.10 + the grep proof, the legacy-cancelled-orders diagnostic output (or "not executed"), and before/after query counts for product list and cart.

---

## B08 — Customer Management (addresses, enable/disable, admin user tools)

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

## B04 — Cart & Checkout (discounts, shipping, address, safe order placement)

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
