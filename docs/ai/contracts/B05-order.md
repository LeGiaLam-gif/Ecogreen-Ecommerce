# Contract: B05 — Order (state machine, status history, inventory port, safe legacy checkout)

Owner: B05. Depends on: B01-F1 (`/api/v1` envelope, `ApiPaging`), B01-P3 (permission constants). Migrations: `V20`, `V21`, `V22`.

## 1. Status enum and transitions

`entity/OrderStatus` is the single list of values (DB `CHECK`, backend, frontend labels in `utils/orderStatus.js`).

| From | Allowed next |
|---|---|
| `PENDING_PAYMENT` | `PAID`, `CANCELLED`, `PROCESSING` (cash on delivery only) |
| `PAID` | `PROCESSING`, `CANCELLED` |
| `PROCESSING` | `PACKED`, `CANCELLED` |
| `PACKED` | `SHIPPED` |
| `SHIPPED` | `DELIVERED` |
| `DELIVERED` | `RETURN_REQUESTED` |
| `RETURN_REQUESTED` | `RETURNED`, `DELIVERED` |
| `RETURNED` | `REFUNDED` |
| `CANCELLED`, `REFUNDED` | none (terminal) |

Revenue statuses (`OrderStatus.revenueStatuses()`): `PAID`, `PROCESSING`, `PACKED`, `SHIPPED`, `DELIVERED`, `RETURN_REQUESTED`.
Every revenue query (`OrderRepository.sumRevenueByDay/ByMonth/sumRevenueAndCountSince`, `OrderItemRepository.topSellingProductsSince`)
takes this set as a parameter; none hard-codes a status.

## 2. The single writer

`OrderService.transitionStatus(orderId, newStatus, actorUserId, note)` is the only code that assigns `orders.status`
(`OrderStatusSingleWriterTest` scans for a second writer). It locks the row (`SELECT ... FOR UPDATE` + refresh), validates the
transition, applies the inventory effect, writes one `order_status_history` row and publishes `OrderStatusChangedEvent`
(and `OrderCancelledEvent` on cancel). Invalid transition → `BusinessRuleViolationException` (400 `BUSINESS_RULE_VIOLATION`).

Public Java signatures (`service/OrderService`):

- `Order checkout(User, customerName, customerPhone, shippingAddress, paymentMethod)` — legacy, see section 6.
- `Order createPendingOrder(userId, List<OrderItemDraft>, subtotal, discountAmount, shippingFee, total, customerName, customerPhone, shippingAddress, discountCode)` — for B04.
- `Order transitionStatus(orderId, OrderStatus, actorUserId, note)`.
- `Order cancelOwnOrder(orderId, userId, note)` — owner cancel, only from `PENDING_PAYMENT`.
- `Page<OrderResponse> listMyOrders(userId, OrderStatus, Pageable)`, `listAdminOrders(status, keyword, from, to, Pageable, OrderViewer)`.
- `OrderResponse getOrderView(orderId, OrderViewer)`, `List<OrderStatusHistoryResponse> getHistory(orderId, OrderViewer)`.

## 3. Inventory port

`service/inventory/InventoryGateway`: `reserve`, `release`, `commit`, `restock`, `available`. B05 ships `LegacyStockInventoryGateway`
over `products.stock_quantity` (atomic `UPDATE ... WHERE stock_quantity >= ?`, products locked in id order). B03 replaces the adapter.

| Transition | Effect |
|---|---|
| `PENDING_PAYMENT` → `PAID` / `PROCESSING` | commit |
| `PENDING_PAYMENT` → `CANCELLED` | release |
| `PAID` / `PROCESSING` → `CANCELLED` | restock (never release) |
| anything else | none |

Insufficient stock → `InsufficientStockException` (409 `CONFLICT`).

## 4. Endpoints (`/api/v1`)

All responses use the `{data, meta}` envelope and the standard error shape. Lists accept `page` (0-based), `size` (default 20, max 100),
`sort=createdAt|totalPrice,asc|desc` (default `createdAt,desc`); an unknown sort field → 400 `VALIDATION_ERROR`.

| Method | Path | Permission / access | Notes |
|---|---|---|---|
| GET | `/orders/my` | any signed-in user | caller's orders, optional `status` filter; unknown status → 400 `VALIDATION_ERROR` |
| GET | `/orders/{id}` | owner, or `order:view_all` | another customer's order → 403 `FORBIDDEN` |
| GET | `/orders/{id}/history` | owner, or `order:view` | `changedBy` and `note` only for staff |
| POST | `/orders/{id}/cancel` | owner (only from `PENDING_PAYMENT`) or `order:cancel` | optional body `{ note? }` (≤ 500 chars) |
| GET | `/admin/orders` | `order:view_all` | filters `status`, `keyword` (order id, optionally `#`-prefixed, or part of the customer name), `from`, `to` (ISO dates) |
| PATCH | `/admin/orders/{id}/status` | `order:update`, plus `order:cancel` when the target is `CANCELLED` | body `{ newStatus, note? }`; invalid transition → 400 `BUSINESS_RULE_VIOLATION` |

`OrderResponse`: `id, userId, customerName, customerPhone, shippingAddress, subtotal, discountAmount, shippingFee, discountCode,
totalPrice, status, createdAt, items[], allowedNextStatuses[], canCancel`. The two last fields are computed on the server for the
caller; the frontend must render them and never compute transitions itself.

## 5. Database

| File | Change |
|---|---|
| `V20__order_state_machine.sql` | widens `chk_orders_status` to the 10 values and maps legacy rows: `PENDING`→`PENDING_PAYMENT`, `CONFIRMED`→`PROCESSING`; `PAID`, `CANCELLED` unchanged. **Changes stored data; rollback loses information once new-only statuses exist.** Stop the old application before applying. |
| `V21__order_status_history.sql` | `order_status_history` + one backfilled row per existing order |
| `V22__orders_price_breakdown.sql` | `subtotal`, `discount_amount`, `shipping_fee`, `discount_code` (backfilled, then `NOT NULL`), indexes `(user_id, created_at DESC)` and `(status, created_at DESC)` |

## 6. Legacy compatibility

- `POST /api/orders` is kept (creates the order in `PENDING_PAYMENT`, reserves stock, writes the first history row). TEMPORARY-COMPAT(B05): B04 replaces it.
- `GET /api/orders`, `/my`, `/{id}` and `PUT /api/orders/{id}/status` were removed; the frontend uses `/api/v1`.
- `POST /api/payments/order/{id}/pay` marks the order `PAID` through `transitionStatus`; it rejects an order that is not `PENDING_PAYMENT`. TEMPORARY-COMPAT(B05): B06 replaces the mock.
- `PaymentController` still uses `OrderService.getOwnedOrAdmin` until B06.
- `ReturnRequestService` accepts an order in `PAID` or `DELIVERED`.

## 7. Decisions applied

D-4: legacy `PAID` orders stay `PAID`. D-7: a legacy cash-on-delivery confirmation still goes through `payNow`.

## Requests for contract changes

None yet. Other modules append their requests here.
