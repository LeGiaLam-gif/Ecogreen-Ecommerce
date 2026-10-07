# EcoGreen — Rules for AI coding agents

This file answers one question: **how do I safely understand and modify this repository?**
For what the project is and how to run it, read [`README.md`](./README.md). For work that is not built yet, read
[`docs/ai/MODULE_SPECS.md`](./docs/ai/MODULE_SPECS.md). Finished modules publish permanent contracts in
[`docs/ai/contracts/`](./docs/ai/contracts).

Order of authority when sources disagree: **the code > the contract of the module > this file > `MODULE_SPECS.md` > README**.
Never assume a document is current; verify it against the code before relying on it.

## 1. Architecture you must preserve

**Fixed stack** — React 19 + Vite + React Router (JavaScript/JSX, axios) · Spring Boot 4.0.x, Java 21, Maven · PostgreSQL.
Do not introduce Next.js, NestJS, TypeScript, Gradle, MySQL or a second ORM. A new library needs a named task or a one-line
justification in the report (smallest way to meet a requirement).

**Backend** — package `com.example.backend`, layered folders `api/ config/ controller/ dto/ entity/ exception/ repository/
security/ service/` (no `modules/` folder). Existing code uses `@Autowired` field injection, and legacy controllers take
`Map<String,Object>` bodies; DTOs expose `from(...)` factories. Follow the style of the file you edit. Endpoints you create or
rewrite use typed, validated request DTOs.

**Authentication (stable — do not change semantics without an explicit task)**
- Access token: JWT HS256, 15 min, claims `sub`, `roles`, `permissions`, `iat`, `exp`, `jti`. `JWT_SECRET` (≥ 32 bytes) is
  required; the app refuses to start without it and there is no default.
- Refresh token: opaque, stored only as SHA-256, rotated on every use, reuse revokes the whole family.
- `AuthInterceptor` (`/api/**`) builds `CurrentUser(userId, roles, permissions)` **from the token only** and rejects inactive
  users with one indexed query. Controllers enforce with `AuthGuard`.
- Google login verifies the ID token server-side (signature, issuer, audience, expiry) and fails closed when
  `GOOGLE_CLIENT_ID` is empty. Login attempts are throttled per username + IP.
- All auth endpoints live under `/api/v1/auth`. Details: `docs/ai/contracts/B01-auth.md`.

**Authorization (RBAC)**
- Roles: `CUSTOMER`, `MANAGER`, `ADMIN`. Permissions are `<resource>:<action>`, lowercase, defined **only** in
  `security/Permissions.java`; the role → permission mapping lives in the database (seeded by `V6`).
- New endpoints use `authGuard.requirePermission(request, Permissions.X)` / `requireAnyPermission(...)`.
  `requireAdmin()` / `isAdmin()` are `@Deprecated`, ADMIN-only, and stay until the owning module migrates its endpoints
  (today the legacy controllers still use them, so a `MANAGER` is denied there).
- `Role.permissions` is deliberately `EAGER` + `@BatchSize` (guarded by `RoleEagerPermissionsTest`). Its real SQL pattern was
  never measured. Do not change its fetch type or `AuthService` token orchestration without a measured run and owner approval
  (`contracts/B01-rbac.md` §7).
- Permissions reach a client through the token, so a change takes effect after ≤ 15 min. Frontend permission checks are UX only.

**API** — two generations coexist. Only `/api/v1/auth/*` uses the new convention; every other `/api/**` endpoint is legacy
(bare JSON, `{message}` errors). Rules for this transition are in section 5.

**Database** — `database/init-postgres.sql` is the baseline, `spring.jpa.hibernate.ddl-auto=update` runs in development, and
versioned SQL lives in `backend/src/main/resources/db/migration` (`V2`–`V6` used). **Flyway is not active**; migrations are
applied by hand (section 6).

**Frontend** — `frontend/src/{pages,components,context,services,utils}`. All HTTP goes through `services/http.js` (the only
axios import; it attaches the token and does a single-flight refresh). Domain API modules live in `services/*Api.js`.
Browser storage for auth is confined to `services/authStorage.js`. `ProtectedRoute` is UX, not security.

## 2. Mandatory workflow

```
READ → INSPECT CURRENT CODE → IDENTIFY CONFLICTS → PLAN → IMPLEMENT → TEST WHAT IS POSSIBLE → REVIEW DIFF → REPORT
```

1. **Read** this file, the relevant contract and the task's spec.
2. **Inspect** every file the task touches, plus `git status` and `git log -1`. Look for an existing implementation before
   creating a new class, endpoint, table or component.
3. **Identify conflicts** between the request and the code. Preserve the business requirement, adapt the implementation to the
   real structure, and never silently pick one side.
4. **Plan** briefly: files, migrations, API changes, frontend changes, tests.
5. **Implement** the smallest change that satisfies the requirement.
6. **Review your own diff** for files outside scope, debug code, secrets, weakened security, missing tests, and contract changes
   without frontend updates.
7. **Report** (section 12).

Do not hide a failure, and do not widen scope to make something pass.

## 3. Git

- Never work on `main`. One branch per task: `feature/<module>-<short-name>`, `fix/<short-name>`, `chore/<short-name>`.
- Commit messages follow Conventional Commits, as the history does: `feat(auth): …`, `fix(auth): …`, `docs: …`.
- Commit, push, open a PR, merge or rebase **only when the owner asks**. Never force-push, rewrite history, or touch other
  branches or `.git/`. If you cannot push, hand over a patch (`git diff > <task>.patch`) or a file list.
- Keep a branch to one task. Do not mix refactors with features.

## 4. Scope and change control

- Every task defines what is allowed, what must be checked and what is forbidden. A file outside the allowed set may change only
  if that is the minimum needed to compile or to keep a contract working; say so in the report and name the owning module.
- Shared files and their owners: `pom.xml` (additive only), `application.properties` (additive only — never change an existing
  value without a stated reason), `GlobalExceptionHandler` and `api/` (B01-F1), `services/http.js` and `AuthContext.jsx` (B01),
  `.env.example` (additive; every new variable must be added), `docs/ai/contracts/*` (the owning module only).
- No unrelated cleanup, renaming, reformatting or "better architecture" refactors. Report inefficiencies and defects you find
  outside scope; do not fix them.
- Do not delete files or behaviour unless the task requires it, and explain each deletion.
- **Ask before:** destructive or irreversible migrations, dropping columns, deleting files, changing a public contract or token
  semantics, changing fetch strategies, activating Flyway, adding infrastructure (Docker services, Redis, queues), running
  `seed-data.sql` or any migration against a real database, or anything that rewrites history.

**Do not change casually** (each needs an explicit task): `JwtService` claims, `RefreshTokenService`, `AuthInterceptor`,
`GoogleApiIdentityVerifier`, `LoginThrottle`, `GlobalExceptionHandler` and `api/`, `http.js` refresh logic, `Permissions.java`,
migrations `V2`–`V6`, `database/init-postgres.sql`, `database/seed-data.sql`, `docker/docker-compose.yml`,
`application.properties` values, `package-lock.json`, `pom.xml` versions.

Temporary stand-ins use `// TEMPORARY-COMPAT(<module>): <why>; remove when <condition>` and are listed in the report.

## 5. API contract

| Aspect | Rule |
|---|---|
| Base path | `/api/v1/...`, plural kebab-case nouns, no verbs (state actions are sub-resources: `POST /orders/{id}/cancel`) |
| Success | `{ "data": …, "meta": null }`; lists `{ "data": [...], "meta": { "page", "size", "totalElements", "totalPages" } }` |
| Error | `{ "error": { "code", "message", "fields"? } }`; codes `VALIDATION_ERROR` 400, `BUSINESS_RULE_VIOLATION` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409, `RATE_LIMITED` 429, `INTERNAL_ERROR` 500 — never leak exception text, SQL or stack traces |
| Pagination | every list: `page` (0-based), `size` (default 20, max 100), whitelisted `sort=field,asc\|desc` — use `ApiPaging` |
| Money | `BigDecimal` / `DECIMAL(12,2)` (VND); never `double`/`float` |
| Time | UTC ISO-8601 in JSON. Open decision D-9: the database session and JVM currently default to `Asia/Ho_Chi_Minh` |
| Ownership | never trust ids, prices, totals, statuses or user ids from the client; check ownership server-side on every `/{id}` |

**Legacy transition rule.** Legacy `/api/**` endpoints keep working until the module that owns them rewrites them. That module
creates the `/api/v1` version, migrates **every frontend caller in the same task**, and removes the legacy path (or documents a
`TEMPORARY-COMPAT` alias with a removal condition). `GlobalExceptionHandler` and `http.js` apply the new shapes **only** to
`/api/v1/**`. A backend change that alters a contract must update its frontend callers in the same task.

**Contracts.** Each module has one file `docs/ai/contracts/<module>.md`: entities, public Java signatures, endpoints (request,
response, errors, permission), status enums, and a "Requests for contract changes" section. Other modules append requests there;
they do not edit the owner's contract or code.

## 6. Database and migrations

- Files: `backend/src/main/resources/db/migration/V<n>__<snake_name>.sql`, one logical change each, inside the module's version
  range ([`MODULE_SPECS.md`](./docs/ai/MODULE_SPECS.md) §3). Never edit a migration that has been applied; add a new one.
- Because Flyway is off and `ddl-auto=update` may already have created tables from entities, every migration must be
  **idempotent** (`IF NOT EXISTS`, guarded `UPDATE`s, `ON CONFLICT DO NOTHING`) and is applied by hand with `psql` in numeric
  order. Keep the JPA entity and the migration consistent so either path yields the same schema. Do not change `ddl-auto`.
- Every migration starts with this header: `LEGACY DATA IMPACT`, `NULLABILITY` (backfill before `NOT NULL`),
  `CONSTRAINT ORDER`, `DATA-LOSS / ROLLBACK RISK` (with manual rollback SQL), `EMPTY DATABASE`, `EXISTING DATABASE`.
- Before widening an enum, drop both the `init-postgres.sql` CHECK (e.g. `chk_orders_status`) and any Hibernate-generated one
  (look it up in `pg_constraint`; do not guess its name).
- Do not drop a column that holds data in the same release that stops using it: deprecate the field first.
- Naming: plural `snake_case` tables, `snake_case` columns, `id` primary keys, `<singular>_id` foreign keys, `created_at` /
  `updated_at` timestamps, enums as uppercase `VARCHAR` values + `CHECK` (never native `ENUM`; list the valid values in one place and share them across DB, backend and frontend), soft-delete via status for rows referenced by
  orders.
- State "migration not executed" in the report unless you actually ran it. Never run migrations or seed scripts against the
  owner's database.

## 7. Security

- Never commit secrets: passwords, JWT/OAuth/payment/SMTP secrets, real `.env` files. Add each new variable **name** to
  `.env.example` (`UPPER_SNAKE_CASE`, grouped by domain prefix such as `JWT_*`, `GOOGLE_*`, `PAYMENT_*`, `SMTP_*`). Do not log secrets or return password hashes, tokens or raw gateway payloads.
- The repository already contains demo credentials: a DB password in `application.properties` and `docker-compose.yml`, and demo
  accounts in `database/seed-data.sql`. Do not add more, do not copy them into docs, and report them rather than reusing them.
- Authorization is enforced in the backend. Permission codes are never string literals outside `Permissions.java`
  (`PermissionsCatalogueTest` enforces it).
- Parameterised queries only; whitelist `sort`; validate uploads by content, size and a server-generated name; verify webhook
  signatures and make them idempotent.
- Never weaken authentication or authorization to make a test pass or to keep an old client working.

## 8. Code conventions

- Java: `PascalCase` classes, `camelCase` members, `UPPER_SNAKE_CASE` constants, file name = class name, business methods named
  `<verb><Noun>`. Never expose entities in responses; use DTOs.
- User-facing messages in code and UI are Vietnamese — keep that. New code comments and documentation are English.
- Frontend: no direct axios or `fetch` outside `services/`; no browser storage outside `authStorage.js`; never compute or trust
  price, total, status or stock in the browser; route guards are UX only. Do not add a frontend test runner unless a task says so.
- Efficiency work is evidence-first: report what was inefficient, the evidence (query count, payload size), the change and the
  trade-off. Prefer `JOIN FETCH`/batching, DB-side filtering and pagination over new infrastructure. Do not add caches, queues
  or Redis for optimisation.

## 9. Testing and truthful reporting

- Backend: JUnit 5 + Mockito for behaviour; real PostgreSQL only when SQL or concurrency is the point. Tests that need a
  database may be written but must be skipped explicitly and reported as **not run**. Test secrets live only in
  `src/test/resources/test-jwt.properties`.
- A feature is done when: it validates input, returns the standard error shape on `/api/v1`, every permission-sensitive endpoint has
  a test for both the allowed and the denied caller, and calculations (price, stock, status transitions) are tested with exact
  numbers, not just "does not throw".
- Run what the change affects: `cd backend && ./mvnw test` (see README for the `mvnw` caveat), `cd frontend && npm run lint &&
  npm run build`, and `node frontend/scripts/verify-auth-refresh.mjs` when `http.js` or token storage changed.
- Known baseline (verify, it may have changed): `npm run build` passes, `npm run lint` reports a few pre-existing errors,
  `npm ci` fails because `package-lock.json` is out of sync (use `npm install` in a scratch copy rather than rewriting the
  lockfile unless the task is to fix it).
- **Never claim a build, lint or test passed unless you ran it and saw it pass.** Report the command and the result; for a
  failure give the honest reason. Do not delete or weaken a test to get green. If Maven Central, npm, Docker, Google or SMTP is
  unreachable, say so.

## 10. Documentation

- `README.md` — what the product does and how a person runs it. `CLAUDE.md` (this file) — how agents work here.
  `API.md` / `DATABASE.md` — reference for what exists today. `docs/ai/contracts/` — module contracts.
  `docs/ai/MODULE_SPECS.md` — remaining work. Do not duplicate one in another.
- Update the affected document in the same task when behaviour, commands, endpoints, tables or environment variables change.
- Document only what exists. No task history, changelogs, "TODO" lists or unfinished features presented as done. Do not create
  a new Markdown file when an existing one is the right home.

## 11. Known open items (also see `MODULE_SPECS.md` §4)

Seven legacy controllers still use `requireAdmin()`; the permission-loading strategy is unmeasured; `application.properties`
hard-codes the datasource (the `DB_*` names in `.env.example` are not read by the application); `mvnw` is committed without
the executable bit; `scratch/` and three `*.patch` files are committed developer leftovers.

## 12. Completion report

End every task with:

1. Summary in business terms · 2. Conflicts found between the request and the code, and how each was resolved ·
3. Files created / modified / deleted, with a one-line reason · 4. Out-of-scope or shared changes (path, why, owner) ·
5. Migrations (file, legacy impact, empty/existing DB, **executed or not**) · 6. API changes (old → new, status and error
codes, permissions) and the frontend callers updated · 7. Efficiency and security review findings ·
8. Tests written, **executed (command + result)**, and **not executed (reason)** · 9. Build and lint results ·
10. `TEMPORARY-COMPAT` code and its removal condition · 11. Known limitations and open decisions hit ·
12. Suggested next task · 13. How the owner integrates the result (files or patch).
