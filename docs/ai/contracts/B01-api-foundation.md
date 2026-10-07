# Contract: B01-F1 — API foundation

Owner: B01-F1. Permanent. Dual-mode (legacy + `/api/v1`) until the end of B12.
The rules this contract implements are in `CLAUDE.md`, section 5 (API contract).

## 1. Java API (`com.example.backend.api`)

| Class | Purpose |
|---|---|
| `ApiResponse<T>(data, meta)` | success envelope; `ApiResponse.of(value)` → `meta: null`; `ApiResponse.of(list, pageMeta)` |
| `PageMeta(page, size, totalElements, totalPages)` | `page` is 0-based |
| `PageResponse.of(Page<T>)` / `PageResponse.of(Page<S>, Function<S,T>)` | `Page` → `{data, meta}`; reads values the `Page` already holds (no second count, no extra query) |
| `ApiPaging.of(page, size, sort, whitelist, defaultSort[, defaultSize])` | safe `Pageable`: page ≥ 0, size default 20, **clamped to 100**, `sort` whitelisted |
| `ApiErrorCode` | `VALIDATION_ERROR` 400, `BUSINESS_RULE_VIOLATION` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409, `RATE_LIMITED` 429, `INTERNAL_ERROR` 500 |
| `ApiError` / `ApiErrorBody(code, message, fields?, traceId?)` | error envelope (`fields` only for validation, `traceId` only for `INTERNAL_ERROR`; null members are omitted) |
| `exception.BusinessRuleViolationException` | throw for "well-formed but not allowed" → `BUSINESS_RULE_VIOLATION` |

## 2. Wire format (only for URLs under `/api/v1/`)

```json
// single
{ "data": { "id": 1 }, "meta": null }
// list
{ "data": [ ... ], "meta": { "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 } }
// error
{ "error": { "code": "VALIDATION_ERROR", "message": "...", "fields": { "price": "must be >= 0" } } }
// unexpected failure
{ "error": { "code": "INTERNAL_ERROR", "message": "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.", "traceId": "<uuid>" } }
```

## 3. Exception → response mapping (`GlobalExceptionHandler`)

| Exception | `/api/v1/**` | legacy `/api/**` (shape `{message}`) |
|---|---|---|
| `ResourceNotFoundException` | 404 `NOT_FOUND` | 404 |
| `BadRequestException` | 400 `VALIDATION_ERROR` | 400 |
| `BusinessRuleViolationException` | 400 `BUSINESS_RULE_VIOLATION` | 400 |
| `UnauthorizedException` | 401 `UNAUTHENTICATED` | 401 |
| `ForbiddenException` | 403 `FORBIDDEN` | 403 |
| `ConflictException` | 409 `CONFLICT` | 409 |
| `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException` | 400 `VALIDATION_ERROR` + `fields` | 400 generic `{message}` |
| `HttpMessageNotReadableException` | 400 `VALIDATION_ERROR` | 400 |
| `MethodArgumentTypeMismatchException` | 400 `VALIDATION_ERROR` + field | 400 generic |
| `MissingServletRequestParameterException` | 400 `VALIDATION_ERROR` + field | 500 generic (unchanged status) |
| any other `RuntimeException` / `Exception` | **500 `INTERNAL_ERROR`** + `traceId` | **500** `{message}` generic |

Exception text, SQL, stack traces and class names are **never** returned; the real exception is logged server-side with the
trace id. Validation `fields` carry constraint messages only — never rejected values; fields whose name contains
`password`, `token`, `secret` or `credential` always get a generic message.

## 4. How a module returns a paged list

```java
private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "price", "price", "name", "name");

@GetMapping("/api/v1/products")
public ApiResponse<List<ProductDto>> list(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String sort) {   // "price,desc"
    Pageable pageable = ApiPaging.of(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "createdAt"), 12);
    return PageResponse.of(productRepository.findAll(pageable), ProductDto::from);        // ONE query (+ the Page's own count)
}
```
Rules: declare `sort` as a single `String` (not `List<String>`, Spring would split on the comma); never pass the raw client
`sort` to a repository; request DTOs use `jakarta.validation` annotations with `@Valid @RequestBody`; throw the existing
exceptions or `BusinessRuleViolationException` for business errors; return single resources with `ApiResponse.of(dto)`.
Money stays `BigDecimal` (`DECIMAL(12,2)`), never `double`/`float`.

## 5. Frontend (`frontend/src/services/http.js`)

`baseURL` stays `/api`, so a v1 endpoint is called as `http.get('/v1/products')`.
- v1 success: single → `res.data` is the plain value; paged (meta present) → `res.data = { items, meta }`; responses
  without an envelope (e.g. 204) are untouched.
- v1 error: rejected object keeps `friendlyMessage` and adds `code` (API code), `fields`, `traceId`; axios' own code is kept as `axiosCode`.
- Legacy URLs: unchanged behaviour. No token refresh logic (B01-P2).

## 6. Legacy behaviour changed by B01-F1 (the only one)

Unknown exceptions on legacy paths no longer return their raw text. Previously every `RuntimeException` (e.g. `NumberFormatException`
from `Long.parseLong(body.get(...))` in cart/product/return controllers, `NullPointerException` on a missing key,
`IllegalStateException` in `ReportService`) → **400** with the exception's own text; now **500** with the generic Vietnamese
system-error text. Known exception classes keep their statuses and messages.

## 7. Requests for contract changes
_(other modules append here)_
