# Contract: B04 — Cart & Checkout

Owner: B04. Depends on: B01-F1 (`/api/v1` envelope), B01-P3 (`requirePermission`), B05 (`OrderService.createPendingOrder`),
B03 (`InventoryService.availableFor`, `InventoryGateway.reserve`), B08 (`AddressService.getOwned`).
Migrations: `V40`, `V41` (hand-applied, `CLAUDE.md` §6).

## 1. Endpoints

All under `/api/v1`, envelope `{ "data": …, "meta": null }`, errors `{ "error": { "code", "message", "fields"? } }`.

### Cart — `/cart` (any signed-in user; always the caller's own cart)

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/cart` | — | `CartResponse` |
| POST | `/cart/items` | `{ productId, quantity }` (quantity ≥ 1) | `CartResponse` |
| PATCH | `/cart/items/{id}` | `{ quantity }` (≥ 1) | `CartResponse` |
| DELETE | `/cart/items/{id}` | — | `CartResponse` |
| DELETE | `/cart` | — | 204 |

`CartResponse`: `{ cartId, items[], subtotal, totalItems }`. Each item: `{ id, product{ id, name, price, image, … , stockQuantity },
quantity, lineTotal, availableQuantity, warning }`. `availableQuantity` is `on_hand − reserved` from `InventoryService`;
`warning` is a Vietnamese message when `quantity > availableQuantity` (otherwise `null`). A line that belongs to another
user answers **404** (the line's existence is not disclosed). Adding or raising a quantity above the available stock is
400 `BUSINESS_RULE_VIOLATION`.

### Checkout — `/checkout` (any signed-in user)

`POST /checkout/preview` — no side effects. Body `{ "discountCode"?: string }`. Result `CheckoutPreview`:

```json
{ "items": [ { "productId": 7, "name": "…", "unitPrice": 100000.00, "quantity": 2, "lineTotal": 200000.00, "available": 5 } ],
  "subtotal": 250000.00, "discountAmount": 25000.00, "shippingFee": 30000.00, "total": 255000.00,
  "issues": [ { "code": "INSUFFICIENT_STOCK", "productId": 7, "message": "…" } ] }
```

Issue codes: `EMPTY_CART`, `PRODUCT_UNAVAILABLE`, `INSUFFICIENT_STOCK` (carry `productId`), `INVALID_DISCOUNT` (no `productId`).
A preview never throws for a problem it can describe; an invalid code yields discount 0 and an `INVALID_DISCOUNT` issue.

`POST /checkout` — 201 `{ data: OrderResponse }` (same shape as `GET /orders/{id}`, contract B05). Body `CheckoutRequest`:

| Field | Rule |
|---|---|
| `addressId` | optional; an address of the caller (`AddressService.getOwned`; someone else's → 403 `FORBIDDEN`, unknown → 404) |
| `customerName`, `customerPhone`, `shippingAddress` | required together when `addressId` is absent (≤ 100 / 20 / 500) |
| `discountCode` | optional, case-insensitive |
| `paymentMethod` | `COD` (default), `VIETQR`, `MOMO`; anything else → 400 `VALIDATION_ERROR` |

Only these fields are read. Prices, totals, user ids and any other property sent by the client are ignored. Either way the
recipient is copied into the order (`orders.customer_name`, `customer_phone`, `shipping_address`); the order never
references an address row. With `addressId`, `shippingAddress` is `line1, ward, district, province`.

Errors: empty cart, inactive product, insufficient stock (names the product), invalid discount → 400
`BUSINESS_RULE_VIOLATION`; bad body → 400 `VALIDATION_ERROR` with `fields`; foreign address → 403; missing address → 404.

### Admin discounts — `/admin/discounts`

| Method | Path | Permission |
|---|---|---|
| GET | `/admin/discounts`, `/{id}` | `discount:view` |
| POST | `/admin/discounts` | `discount:manage` → 201 |
| PUT | `/admin/discounts/{id}` | `discount:manage` |
| DELETE | `/admin/discounts/{id}` | `discount:manage` → 204 |

The list is paged (`page`, `size`, `sort` whitelist `createdAt`, `code`, `id`; default `createdAt,id desc`). Body:
`{ code, percentOff? , amountOff?, minOrderAmount?, validFrom?, validTo?, active }` — **exactly one** of `percentOff`
(1–100) and `amountOff` (> 0); `validFrom ≤ validTo`. A duplicate code (case-insensitive) → 409 `CONFLICT`.
Orders keep `discount_code` as plain text with no foreign key, so deleting a discount never affects history.
Roles: `ADMIN` and `MANAGER` hold both permissions (V41). There is no admin UI for discounts yet.

## 2. Checkout transaction (`CheckoutService.checkout`)

One `@Transactional` method, in this order:

1. validate `paymentMethod`; resolve the recipient (address or text fields);
2. **lock the cart row** — `CartRepository.findByUserIdForUpdate` (`PESSIMISTIC_WRITE`);
3. load the lines with their products in one query (`findByCartIdWithProduct`); an empty cart is 400;
4. every product `ACTIVE`, quantity > 0, **price read from the database**; early availability check, one batch
   (`InventoryService.availableFor`);
5. `subtotal` = Σ unit price × quantity;
6. discount (`DiscountService.apply`);
7. shipping (`ShippingPolicy.feeFor(subtotal)`);
8. `total = subtotal − discount + shipping`, scale 2;
9. `OrderService.createPendingOrder` with the snapshot;
10. `InventoryGateway.reserve` per line **in ascending `productId`** (B03 lock-ordering rule); `InsufficientStockException`
    rolls back everything above;
11. `PaymentService.createPendingPayment(order, method)`;
12. delete the cart lines **last**; publish `OrderPlacedEvent(orderId, userId, total)`.

**Cart-lock rule.** Every `CartService` mutation (add, update, remove, clear) takes the same cart row lock before reading
lines. Two checkouts of the same cart therefore serialise; the second sees an empty cart and is rejected with 400, so a
cart can produce one order only. A user has one cart row, so two users never contend; the lock order is always cart row,
then inventory rows in ascending product id.

**Event.** `com.example.backend.event.OrderPlacedEvent(Long orderId, Long userId, BigDecimal total)` is published inside the
transaction; consumers must use `@TransactionalEventListener(phase = AFTER_COMMIT)`.

## 3. Pricing rules

- **Discount.** Code stored upper-case, matched case-insensitively. Usable when it exists, is `active`, `now` is within
  `[valid_from, valid_to]` (open ends allowed) and `subtotal ≥ min_order_amount`. Percentage discounts are rounded
  `HALF_UP` to whole VND (10 % of 15 555 → 1 556.00). The discount never exceeds the subtotal. Any failure gives the same
  message, "Mã giảm giá không hợp lệ hoặc đã hết hạn." (a caller cannot tell which codes exist).
- **Shipping.** `feeFor(subtotal)`: `0.00` when the items total is **≥ `ecogreen.shipping.free-threshold`** (the boundary
  ships free; 499 999.99 pays the fee), otherwise `ecogreen.shipping.flat-fee`. The threshold is compared with the subtotal
  **before** the discount. Decision D-10.
- **Money.** `BigDecimal`, scale 2, VND.

## 4. Configuration

| Property | Env variable | Default |
|---|---|---|
| `ecogreen.shipping.flat-fee` | `SHIPPING_FLAT_FEE` | `30000` |
| `ecogreen.shipping.free-threshold` | `FREE_SHIPPING_THRESHOLD` | `500000` |

Negative values stop the application at startup.

## 5. Java signatures

```java
// service/CheckoutService
public Order checkout(Long userId, CheckoutRequest request)
public CheckoutPreviewResponse preview(Long userId, String discountCode)   // readOnly
// service/ShippingPolicy
public BigDecimal feeFor(BigDecimal subtotal)
// service/DiscountService
public AppliedDiscount apply(String rawCode, BigDecimal subtotal, Instant now)
// repository/CartRepository
Optional<Cart> findByUserIdForUpdate(Long userId)
// service/PaymentService   (TEMPORARY-COMPAT(B04), section 6)
public Payment createPendingPayment(Order order, String paymentMethod)
public static String requireSupportedMethod(String method)
```

## 6. TEMPORARY-COMPAT

- `PaymentService.createPendingPayment` and `SUPPORTED_METHODS` (`COD`, `VIETQR`, `MOMO`): the payment row used to be
  created inside the legacy `OrderService.checkout`. B06 owns payments and replaces the method list and the row creation
  when it introduces real providers.

Removed by this module: `POST /api/orders`, `/api/cart/*`, `OrderService.checkout`, `OrderService.getItems`.

## 7. Known limitations

- An abandoned `PENDING_PAYMENT` order holds its reserved stock until someone cancels it; there is no expiry job.
- `V40`/`V41` are not applied by the application; apply them by hand before the first start with this version.

## 8. Requests for contract changes

_None._
