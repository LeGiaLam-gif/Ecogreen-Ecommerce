# EcoGreen — Claude Code Project Rules

## 1. Project purpose

This repository contains the EcoGreen ecommerce platform.

The system consists of:

* Backend: Spring Boot
* Frontend: verify actual framework from repository before making architectural changes
* Database: PostgreSQL
* Database migration: Flyway
* Authentication: JWT + refresh token
* Authorization: permission-based RBAC
* Infrastructure: Docker Compose
* Testing: backend unit/integration tests and frontend build/tests

## 2. Source of truth

Before modifying code:

1. Read `docs/ai/00_GAP_ANALYSIS.md`
2. Read `docs/ai/01_CONVENTIONS.md`
3. Read `docs/ai/02_DEVELOPMENT_PLAN.md`
4. Read the relevant section of `docs/ai/03_AGENT_PROMPTS.md`
5. Inspect the existing source code and database schema
6. Never assume that the requirements already match the current implementation

When there is a conflict between the current code and the requirements:

* Do not silently choose one.
* Identify the conflict.
* Prefer preserving existing behavior unless the requirement explicitly requires migration.
* Document the conflict before making a destructive architectural change.

## 3. Git rules

Never modify `main` directly.

Every task must:

1. Work on a dedicated branch.
2. Keep the branch focused on one block/task.
3. Commit logical changes.
4. Run relevant tests before completion.
5. Report changed files and verification results.

Do not rewrite unrelated files.

Do not perform broad refactors unless explicitly required.

## 4. Migration rules

Flyway is the single source of truth for database schema.

Never:

* create ad-hoc schema changes outside Flyway;
* modify an already released migration to fix a new requirement;
* reuse a Flyway version assigned to another block.

Use the migration version ranges defined in `docs/ai/03_AGENT_PROMPTS.md`.

## 5. Dependency rules

Follow module dependencies defined in `03_AGENT_PROMPTS.md`.

Do not implement functionality that belongs to another block unless explicitly required as a temporary compatibility layer.

When another module dependency is not ready:

* use the published contract;
* create the smallest compatible temporary implementation only when explicitly permitted;
* document the temporary compatibility layer.

## 6. API rules

Follow:

* existing API conventions;
* request/response envelopes;
* HTTP status conventions;
* permission requirements;
* DTO naming conventions.

Do not invent a new API format when an existing convention already exists.

## 7. Security rules

Never commit:

* passwords;
* API secrets;
* JWT secrets;
* payment secrets;
* SMTP credentials;
* OAuth client secrets;
* `.env` files containing real credentials.

Use `.env.example` for required environment variables.

Never weaken authentication or authorization simply to make tests pass.

## 8. Testing rules

Every feature must include appropriate tests.

Before declaring a task complete:

* run relevant unit tests;
* run integration/API tests where applicable;
* run the frontend build where frontend code changed;
* verify database migrations;
* report failures honestly.

Do not delete or weaken tests to make the build pass.

## 9. Minimal-change rule

Prefer the smallest implementation that satisfies the requirement.

Do not:

* rewrite working modules unnecessarily;
* change framework;
* rename unrelated files;
* replace libraries without explicit justification;
* introduce new architectural patterns without requirement.

## 10. Completion report

At the end of every task report:

1. What changed
2. Files changed
3. Tests executed
4. Test results
5. Migration changes
6. API changes
7. Known limitations
8. Temporary compatibility code
9. Recommended next task
