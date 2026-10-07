# Contract: B01-P3 — Permission-based RBAC

Owner: B01-P3. Depends on: B01-P2 (JWT access token, `CurrentUser`, `AuthInterceptor`). Does not edit
`docs/ai/contracts/B01-auth.md` (see section 11).

## 1. Roles

Final roles: `CUSTOMER` (renamed from `USER`), `MANAGER`, `ADMIN`. `Role.USER` no longer exists in code; the constants are
`Role.CUSTOMER`, `Role.MANAGER`, `Role.ADMIN`. `DataLoader` seeds exactly these three and never re-creates `USER`.

## 2. Permission catalogue (27 codes)

The **only** place codes are defined in Java is `security/Permissions.java` (constants such as `Permissions.PRODUCT_CREATE`,
the list `Permissions.ALL`). No permission string literal may appear elsewhere in Java application code
(`PermissionsCatalogueTest.permissionLiterals_areOnlyDefinedInThePermissionsCatalogue` enforces it). The SQL copy in
`V6__seed_permissions.sql` is checked against it by `PermissionsCatalogueTest.v6Seed_matchesJavaCatalogue_noDrift`.

| Resource | Codes |
|---|---|
| product | `product:view`, `product:create`, `product:update`, `product:delete`, `product:publish` |
| category | `category:view`, `category:create`, `category:update`, `category:delete` |
| inventory | `inventory:view`, `inventory:update`, `inventory:adjust` |
| order | `order:view`, `order:view_all`, `order:update`, `order:cancel` |
| payment | `payment:view`, `payment:confirm`, `payment:refund` |
| return | `return:view`, `return:process` |
| user | `user:view`, `user:update`, `user:disable` |
| other | `analytics:view`, `audit:view`, `system:configure` |

## 3. Role → permission mapping

| Role | Permissions |
|---|---|
| `ADMIN` | all 27 |
| `MANAGER` | 24: all except `system:configure`, `audit:view`, `user:disable` (owner decision D-6, default) |
| `CUSTOMER` | none — customer rights are ownership-based (own cart/orders), not permission-based |

`Permissions.forRole(roleName)` documents this mapping for tests; runtime authorization never uses it, only the
permissions loaded from the database.

## 4. Guards for B02–B10 (`security/AuthGuard`)

```java
public void requirePermission(HttpServletRequest request, String code)
public void requireAnyPermission(HttpServletRequest request, String... codes)
```
Both throw `UnauthorizedException` (401 `UNAUTHENTICATED`) when there is no `CurrentUser`, and `ForbiddenException` (403
`FORBIDDEN`) when the token does not carry the permission (any of the codes, for the second method). Usage:
`authGuard.requirePermission(request, Permissions.PRODUCT_CREATE);`. Authorization is decided by the **permission claim only** —
holding the `ADMIN` role with an empty permission claim does **not** pass `requirePermission`.

Legacy, unchanged behaviour, now `@Deprecated`:
```java
@Deprecated public void requireAdmin(HttpServletRequest request)   // 401 / 403 unless role ADMIN
@Deprecated public boolean isAdmin(HttpServletRequest request)
```
Removal condition: every controller migrated (end of B12). `requireUser(request)` is unchanged.

## 5. `CurrentUser` (security/CurrentUser)

Unchanged: `getUserId()`, `getRoles()`, `getPermissions()`, `isAdmin()` (role `ADMIN`). Added:
```java
public boolean hasPermission(String code)                 // false for null
public boolean hasAnyPermission(String... codes)          // false for null / empty
```

## 6. JWT permission claim

`JwtService.issueAccessToken(User)` writes `permissions` = the union of the permissions of **all** of the user's roles, taken
from the database, **deduplicated and sorted**. Claims overall: `sub`, `roles`, `permissions`, `iat`, `exp`, `jti`. Nothing is
taken from the client, nothing is hard-coded per user. Applies to login, Google login and refresh (refresh issues a new access
token from the freshly loaded user, so a refresh picks up permission changes).

`UserResponse.from(User)` (login/google/register/`GET /api/v1/auth/me` bodies) reports the same union in
`permissions` (deduplicated, sorted); for `CUSTOMER` it is `[]`.

## 7. Permission loading strategy

**Status: NOT MEASURED. The requirement "one query joining user -> roles -> permissions when issuing a token" is NOT
demonstrated by this implementation (see "Open blocker" below).**

* `Role.permissions` is a `@ManyToMany` through `role_permissions`, mapped **EAGER + `@BatchSize(50)`**. `User.roles` remains
  EAGER (unchanged).
* **Why EAGER:** `JwtService` and `UserResponse.from` read the data outside a guaranteed persistence context, and the owner
  forbade a design that can throw `LazyInitializationException`. `AuthService` may change only role constants, so permissions
  cannot be passed in explicitly. `RoleEagerPermissionsTest` fails if either mapping is made lazy.
* `JwtService.issueAccessToken(User)` and `UserResponse.from(User)` issue **no query of their own**; they read the graph that was
  loaded with the `User`.
* **Expected (from Hibernate semantics, not observed) statement pattern**, per permission-related loading step:
  * username/password login (`findByUsername`, an HQL query): user select, then a secondary select for `User.roles`, then the
    `Role.permissions` collections of the loaded roles, expected as one `@BatchSize` statement; if batching does not apply as
    intended, one statement per role. Not a single joined query.
  * Google login: same pattern for an existing user; for a new user `roleRepository.findByName(CUSTOMER)` is an HQL query, so
    the CUSTOMER role's `permissions` collection is an additional secondary select (empty result). Registration has the same
    extra select.
  * refresh and `AuthGuard.requireUser` (`findById`): Hibernate may join-fetch the eager collections into the entity load
    statement, which could make this a single joined statement. Unverified.
* **No per-permission query is expected** (permissions are a collection, never fetched one by one), but this is also unmeasured.
* **Cost:** every load of a `User` entity (login, `AuthGuard.requireUser`, refresh) also loads its roles' permissions. The size
  of that cost (+0, +1 or +1 per role statements) is unmeasured. `AuthInterceptor` is unaffected (it only calls
  `existsByIdAndActiveTrue`).
* **Persistence coupling (documented):** `UserResponse.from(User)` and `JwtService.issueAccessToken(User)` depend on
  `Role.permissions` being initialised. It always is while the mapping stays EAGER.
* **Why it is not measured:** Maven Central and the other Maven mirrors are unreachable from the authoring sandbox
  (`x-deny-reason: host_not_allowed`), `backend/mvnw` is not executable, and no Hibernate/Spring jars are available offline. To
  measure, run `AuthController` login/google/refresh against PostgreSQL with `spring.jpa.show-sql=true` (or
  `hibernate.generate_statistics=true`) and count statements.

### Open blocker (owner decision required)

The spec wording asks for one joined query when issuing a token. Satisfying it literally and safely needs one of the options
below; none was implemented because each either touches `AuthService`/`AuthController` or adds a redundant query.

1. **Accept the current design**, relaxing the requirement to "no per-permission query" once measured.
2. **Add a single fetch-join query used by `JwtService`** (`select distinct u from User u left join fetch u.roles r left join fetch
   r.permissions where u.id = :id`, in a repository) for the token claim. Literal compliance for the token, no `AuthService`
   change, but it is redundant while `Role.permissions` stays EAGER (an extra statement per token).
3. **Make `Role.permissions` LAZY and pass permissions explicitly**: one joined query, `UserResponse.from(User, List<String>)`
   overload, minimal edits in `AuthService.issueSession`/`register` and `AuthController.me`. Cleanest and cheapest, but needs
   approval to go beyond role constants in `AuthService`.
4. **Load permission codes with the role row** (a PostgreSQL-specific `@Formula` aggregate on `Role`): no extra statement and no
   lazy risk, but it is unproven here and needs a measured run first.

## 8. Security notes

* Permission data comes from the database through the token, never from the client; the frontend's `hasPermission` is UX only.
* **A removed role or permission stays effective until the current access token expires (≤ 15 min)**; a refresh issues a token
  with the new permissions. Deactivated users are still rejected immediately by `AuthInterceptor` (B01-P2, unchanged).
* **MANAGER compatibility limitation:** every endpoint that still calls `requireAdmin()` denies a `MANAGER` (403) until its
  owning module migrates it to `requirePermission`. The frontend admin area (`ProtectedRoute adminOnly`, Navbar,
  `Login.jsx`/`Register.jsx` redirects) still keys on `ADMIN` and is outside P3's allowed files, so a MANAGER cannot yet use it.
* The admin user list (`GET /api/users`, owned by B08) maps users with `UserResponse::from`, so it now also carries each
  user's real permissions.

## 9. Database migrations (manual — Flyway is NOT active)

Apply in this order: **V4 → V5 → V6**. Other orders are not supported. All are idempotent and carry the mandatory header
(legacy impact, nullability, constraint order, rollback, empty/existing DB).

| File | Purpose |
|---|---|
| `V4__permissions.sql` | `permissions(id, code UNIQUE, description)`, `role_permissions(role_id, permission_id)` composite PK, FKs `ON DELETE CASCADE`, index on `role_permissions(permission_id)` (reverse lookup; the PK already serves `role_id` joins) |
| `V5__roles_customer_manager.sql` | guarded `USER` → `CUSTOMER` (see below), ensures `MANAGER` |
| `V6__seed_permissions.sql` | 27 catalogue rows + ADMIN (27) / MANAGER (24) mappings; CUSTOMER none |

**USER → CUSTOMER:** only `USER` exists → renamed in place (same `roles.id`, every `user_roles` link kept). Both exist (e.g. the
new `DataLoader` ran first) → links are moved to `CUSTOMER` without PK duplicates, then `USER` is deleted. Neither exists →
`CUSTOMER` is created. Rollback for the rename case: `UPDATE roles SET name = 'USER' WHERE name = 'CUSTOMER';`.

Until V4–V6 are applied, tokens carry `permissions: []`, and with `ddl-auto=update` Hibernate creates the two tables from the
entities (same names/types) but they stay empty. Existing browser sessions keep their old cached user until the frontend
revalidates it on load (`AuthContext` calls `GET /api/v1/auth/me` on boot).

### Verification queries (read-only)
```sql
-- 1. role names (expect exactly ADMIN, CUSTOMER, MANAGER)
SELECT id, name FROM roles ORDER BY name;
-- 2. absence of USER (expect 0)
SELECT count(*) AS legacy_user_role_rows FROM roles WHERE name = 'USER';
-- 3. orphaned user_roles (expect 0 rows)
SELECT ur.user_id, ur.role_id FROM user_roles ur
  LEFT JOIN roles r ON r.id = ur.role_id LEFT JOIN users u ON u.id = ur.user_id
 WHERE r.id IS NULL OR u.id IS NULL;
-- 3b. users that ended up with no role at all (expect 0 rows)
SELECT u.id, u.username FROM users u LEFT JOIN user_roles ur ON ur.user_id = u.id WHERE ur.user_id IS NULL;
-- 4. permission counts (expect permissions = 27; ADMIN 27, MANAGER 24, CUSTOMER 0)
SELECT count(*) AS permissions FROM permissions;
SELECT r.name, count(rp.permission_id) AS permission_count
  FROM roles r LEFT JOIN role_permissions rp ON rp.role_id = r.id GROUP BY r.name ORDER BY r.name;
-- 5. role -> permission mapping (read-only listing)
SELECT r.name AS role, p.code FROM role_permissions rp
  JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id ORDER BY r.name, p.code;
-- 5b. what MANAGER lacks (expect exactly audit:view, system:configure, user:disable)
SELECT p.code FROM permissions p
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                    WHERE r.name = 'MANAGER' AND rp.permission_id = p.id) ORDER BY p.code;
```

## 10. Frontend (`context/AuthContext.jsx`)

Exposes `user`, `roles`, `permissions`, `isAdmin`, `isStaff` (ADMIN or MANAGER), `hasPermission(code)`,
`hasAnyPermission(...codes)` (plus the existing `loading`, `login`, `loginWithGoogle`, `register`, `logout`). On boot, a stored
session is **always** revalidated with `GET /api/v1/auth/me` and the returned user is saved.

## 11. Requests for contract changes

* **B01-auth.md (owner approval needed, not edited by P3):** §2 shows `roles = ["ADMIN","USER"]` and `permissions = []
  (filled by B01-P3)`. After P3 the role is `CUSTOMER` and `permissions` is populated. Please update the example to
  `["ADMIN","CUSTOMER"]` and the permission note.
* Other modules append requests here; they do not edit this module's code.
