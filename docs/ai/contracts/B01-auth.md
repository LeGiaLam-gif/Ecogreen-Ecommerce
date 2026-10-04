# Contract: B01-P2 — Authentication (JWT access token + rotating refresh token)

Owner: B01-P2. Depends on: B01-F1 (envelope/errors), B01-P1 (Google ID-token verification, unchanged).
All authentication traffic is under `/api/v1/auth`. The legacy `/api/auth/*` and `GET /api/users/me` are **removed** (404).

## 1. Configuration
| Property / env | Meaning |
|---|---|
| `JWT_SECRET` → `ecogreen.jwt.secret` | HS256 key, **≥ 32 bytes, required**. The application **fails to start** with a clear message when missing/short. No default exists. Example: `openssl rand -base64 48` |
| `ecogreen.jwt.access-ttl-seconds` | default 900 (15 min) |
| `ecogreen.jwt.refresh-ttl-days` | default 14 |
| `ecogreen.auth.max-failed-logins` / `lockout-window-seconds` | default 5 / 900 (per username + client IP) |
Tests use `src/test/resources/test-jwt.properties` (TEST-ONLY secret).

## 2. Access token (JWT, HS256)
Claims: `sub` = user id (string), `roles` = `["ADMIN","USER"]`, `permissions` = `[]` (filled by B01-P3), `iat`, `exp`, `jti`.
Sent as `Authorization: Bearer <accessToken>` only.

## 3. Refresh token
Opaque, 64 random bytes (URL-safe Base64, 86 chars), shown **once**. Only its hex SHA-256 is stored in `refresh_tokens`
(`V3__refresh_tokens.sql`). **Rotation**: every `/refresh` revokes the presented token and issues a new one in the same
family. **Reuse detection**: presenting a revoked token revokes the whole family and returns 401. The claim of a token is an
atomic `UPDATE ... WHERE revoked = false`, so two concurrent refreshes with one token cannot both succeed (the loser is treated
as reuse). Deactivated users cannot refresh (family revoked).

## 4. Endpoints (success = `{data, meta:null}`, errors = F1 `{error:{code,message,fields?}}`)
```
POST /api/v1/auth/register  {username,email,password}   → 201 {data:{id,username,email,active,roles,permissions}}   (no auto-login)
POST /api/v1/auth/login     {username,password}          → 200 {data:{accessToken,refreshToken,expiresIn,user:{id,username,email,active,roles,permissions}}}
POST /api/v1/auth/google    {idToken}                    → 200 same as login        (503 when GOOGLE_CLIENT_ID is not configured)
POST /api/v1/auth/refresh   {refreshToken}               → 200 {data:{accessToken,refreshToken,expiresIn}}
POST /api/v1/auth/logout    {refreshToken}               → 200 {data:null}          (revokes that refresh token; idempotent)
GET  /api/v1/auth/me        (Bearer)                     → 200 {data:{id,username,email,active,roles,permissions}}
```
Errors: bad username **or** password → 401 `UNAUTHENTICATED` with the same text (the "account locked" text is only shown after the
correct password); invalid/expired/reused refresh token → 401; missing body fields → 400 `VALIDATION_ERROR`; too many failures →
429 `RATE_LIMITED` + `Retry-After`; Google not configured → 503 (`INTERNAL_ERROR` code, no 503 code exists in the closed set).
`expiresIn` is in seconds.

## 5. How other modules authenticate
`AuthInterceptor` (path `/api/**`) verifies signature + expiry, then checks `existsByIdAndActiveTrue(userId)` (one indexed
query, no entity/roles load) and stores `CurrentUser(userId, roles, permissions)` in the request attribute `currentUser`.
Invalid/absent/expired/legacy tokens leave **no** `CurrentUser` (resolution only). Controllers enforce with `AuthGuard`:
`requireUser(request)` (401), `requireAdmin(request)` (401/403), `isAdmin(request)`. Their semantics are unchanged.
`CurrentUser`: `getUserId()`, `getRoles()`, `getPermissions()`, `isAdmin()` (= roles contain `ADMIN`).
**Trade-off:** roles/permissions come from the token, so a role change takes effect after ≤ 15 min (until refresh); deactivation
takes effect immediately for API calls (lookup per request) and blocks refresh.

## 6. Frontend
`localStorage`: `accessToken`, `refreshToken`, `user` (`services/authStorage.js`). `http.js` attaches the access token; on a **401**
(v1 `UNAUTHENTICATED` or legacy `{message}`) from any endpoint except login/register/google/refresh/logout it performs **one**
refresh (single-flight across concurrent requests; `navigator.locks` across tabs when available) and retries the original request
once. Refresh rejected (400/401/403) → storage cleared and redirect to `/login`; network/5xx errors keep the session.
On boot a legacy UUID session (`token` key, or a non-JWT-shaped access token) is cleared silently.
`getMe` → `/api/v1/auth/me`.

## 7. Migration impact
All existing sessions (in-memory UUID tokens) are invalid after deployment: **every user logs in once**. New table `refresh_tokens`
(run `V3` manually; Flyway is not active; with `ddl-auto=update` Hibernate also creates it in dev).

## 8. Known limits / open decisions
- Tokens live in `localStorage` (readable by XSS). httpOnly-cookie refresh token = open owner decision **D-5**; not switched.
- Throttle is per instance, in memory, keyed by username + `getRemoteAddr()` (`X-Forwarded-For` deliberately not trusted).
- Expired `refresh_tokens` rows are not purged yet (no scheduler in scope).
