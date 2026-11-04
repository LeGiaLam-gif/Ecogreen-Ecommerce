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

**Rework (feature/b01-auth-rework) — additional configuration** (all names also in `.env.example`):
| Property / env | Meaning |
|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | datasource (`application.properties` now reads them; **`DB_PASSWORD` has no default**) |
| `CORS_ALLOWED_ORIGINS` → `ecogreen.cors.allowed-origins` | comma-separated origin **patterns** allowed on `/api/**`; default `http://localhost:*,http://127.0.0.1:*`. Any other origin gets `403 Invalid CORS request` |
| `APP_PUBLIC_URL` → `ecogreen.app.public-url` | frontend base URL used in the reset link (default `http://localhost:5173`) |
| `ecogreen.auth.password-reset-ttl-minutes` | default 30 |
| `ecogreen.auth.reset-max-requests` / `reset-window-seconds` | default 5 / 900 (per client IP **and** per e-mail address) |
| `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET`, `FACEBOOK_GRAPH_VERSION` | Facebook login; empty app id or secret = 503. Frontend: `VITE_FACEBOOK_APP_ID` |
| `SMTP_*` | unchanged (B10); with `SMTP_HOST` empty the reset e-mail is not sent (only a log line with the user id) |

## 2. Access token (JWT, HS256)
Claims: `sub` = user id (string), `roles` = `["ADMIN","CUSTOMER"]`, `permissions` = the union of the permissions of all the user's roles, read from the database (B01-P3; see `B01-rbac.md`), `iat`, `exp`, `jti`.
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
                            ("username" is a username OR an e-mail address: a value containing '@' is looked up as an e-mail)
POST /api/v1/auth/google    {idToken}                    → 200 same as login        (503 when GOOGLE_CLIENT_ID is not configured)
POST /api/v1/auth/facebook  {accessToken}                → 200 same as login        (503 when FACEBOOK_APP_ID/SECRET are not configured)
POST /api/v1/auth/forgot-password {email}                → 200 {data:null}  ALWAYS, whether or not the e-mail has an account
POST /api/v1/auth/reset-password  {token,password}       → 200 {data:null}  (ends every session of the user)
POST /api/v1/auth/refresh   {refreshToken}               → 200 {data:{accessToken,refreshToken,expiresIn}}
POST /api/v1/auth/logout    {refreshToken}               → 200 {data:null}          (revokes that refresh token; idempotent)
GET  /api/v1/auth/me        (Bearer)                     → 200 {data:{id,username,email,active,roles,permissions}}
```
Errors: bad username **or** password → 401 `UNAUTHENTICATED` with the same text (the "account locked" text is only shown after the
correct password); invalid/expired/reused refresh token → 401; missing body fields → 400 `VALIDATION_ERROR`; too many failures →
429 `RATE_LIMITED` + `Retry-After`; Google not configured → 503 (`INTERNAL_ERROR` code, no 503 code exists in the closed set).
`expiresIn` is in seconds.

**Registration rules.** `username` 3–50 chars, `[A-Za-z0-9_.]` only (no `@`, so it can never look like an e-mail); `email` valid, ≤ 100, stored lower-case; `password` 8 characters minimum and ≤ 72 bytes (BCrypt limit). Duplicates are checked case-insensitively; a race on the unique indexes is a 409 `CONFLICT`, not a 500. Shape errors → 400 `VALIDATION_ERROR` with `fields`; a weak password → 400 `VALIDATION_ERROR` with the message in `error.message` (field messages of `*password*`/`*token*` fields are masked by `GlobalExceptionHandler`).

**Forgot / reset password.** `forgot-password` stores a one-time token (32 random bytes, URL-safe Base64; only its SHA-256 is saved in `password_reset_tokens`, `V7`), valid 30 minutes, and mails `{APP_PUBLIC_URL}/reset-password?token=…` through `EmailSender` directly (never through `NotificationService`, which would persist the link). A new request voids older open links. Deactivated or unknown addresses get the same 200 and no mail. More than 5 requests per 15 min from one IP or for one address → 429 `RATE_LIMITED` + `Retry-After`. `reset-password`: unknown / used / expired token → 400 `VALIDATION_ERROR` ("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn…"); success replaces the password hash, voids the user's other links and **revokes every refresh token**. Access tokens already issued live out their 15 minutes.

**Facebook login.** The client sends only the user access token. The server calls `debug_token` (must be valid and `app_id` = our app) and `/me?fields=id,name,email` (with `appsecret_proof`), then: known `facebook_id` → log in; otherwise create a new CUSTOMER account **only if Facebook returned an e-mail that no account uses** (400 without e-mail, 409 if the e-mail is taken). An existing account is **never** linked by e-mail (Facebook does not say the address is verified). Google keeps its policy: link by *verified* e-mail.

## 5. How other modules authenticate
`AuthInterceptor` (path `/api/**`) verifies signature + expiry, then checks `existsByIdAndActiveTrue(userId)` (one indexed
query, no entity/roles load) and stores `CurrentUser(userId, roles, permissions)` in the request attribute `currentUser`.
Invalid/absent/expired/legacy tokens leave **no** `CurrentUser` (resolution only). Controllers enforce with `AuthGuard`:
`requireUser(request)` (401), `requireAdmin(request)` (401/403), `isAdmin(request)`. Their semantics are unchanged.
`CurrentUser`: `getUserId()`, `getRoles()`, `getPermissions()`, `isAdmin()` (= roles contain `ADMIN`).
**Trade-off:** roles/permissions come from the token, so a role change takes effect after ≤ 15 min (until refresh); deactivation
takes effect immediately for API calls (lookup per request) and blocks refresh.

## 6. Frontend
`localStorage` (login with "Duy trì đăng nhập" ticked) **or** `sessionStorage` (unticked): `accessToken`, `refreshToken`, `user` (`services/authStorage.js`; reads look in both, a refresh writes back to the store that holds the session). `http.js` attaches the access token; on a **401**
(v1 `UNAUTHENTICATED` or legacy `{message}`) from any endpoint except login/register/google/facebook/forgot-password/reset-password/refresh/logout it performs **one**
refresh (single-flight across concurrent requests; `navigator.locks` across tabs when available) and retries the original request
once. Refresh rejected (400/401/403) → storage cleared and redirect to `/login`; network/5xx errors keep the session.
On boot a legacy UUID session (`token` key, or a non-JWT-shaped access token) is cleared silently.
`getMe` → `/api/v1/auth/me`.

## 7. Migration impact
All existing sessions (in-memory UUID tokens) are invalid after deployment: **every user logs in once**. New table `refresh_tokens`
(run `V3` manually; Flyway is not active; with `ddl-auto=update` Hibernate also creates it in dev).
Rework: `V7__password_reset_tokens.sql` (new table) and `V8__users_facebook_id.sql` (new nullable column + partial unique index) — apply by hand with `psql`, both idempotent. `V9` is still free (e-mail verification, if ever added).

## 8. Known limits / open decisions
- Tokens live in `localStorage` (readable by XSS). httpOnly-cookie refresh token = open owner decision **D-5**; not switched.
- Throttle is per instance, in memory, keyed by username + `getRemoteAddr()` (`X-Forwarded-For` deliberately not trusted).
- Expired `refresh_tokens` and used/expired `password_reset_tokens` rows are not purged yet (no scheduler in scope).
- **No e-mail verification at registration (open decision).** Someone can register with an address they do not own. Consequence for Google linking: if the real owner later signs in with Google, their Google login is linked to that pre-created account, whose password the registrant knows. Fix = e-mail verification (a `V9` token table, like password reset) before linking, or never linking password accounts to Google.
- The reset throttle is per instance and counts every request, so someone can use up an address's 5 requests and delay its owner by up to 15 minutes.
