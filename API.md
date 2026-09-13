# API Reference — EcoGreen backend

Base URL: `http://localhost:8081/api`

Xác thực: gửi header `Authorization: Bearer <token>` (token lấy từ
`POST /auth/login`). Không có token hoặc token sai → `401`. Có token nhưng
thiếu quyền → `403`. Đây là kiểm tra **phía server** (`AuthGuard`), frontend
ẩn nút/route chỉ là UX, không phải cơ chế bảo mật.

## Auth
| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/auth/register` | Public | `{ username, email, password }` → tạo user, role `USER`, tạo cart rỗng |
| POST | `/auth/login` | Public | `{ username, password }` → `{ token, user }` |
| POST | `/auth/logout` | Đã đăng nhập | Thu hồi token hiện tại |

## Users
| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/users/me` | Đã đăng nhập | Hồ sơ của chính mình |
| GET | `/users` | ADMIN | Danh sách tất cả user |
| DELETE | `/users/{id}` | ADMIN | Xoá user (và cart liên quan) |

## Categories
| Method | Path | Quyền |
|---|---|---|
| GET | `/categories` | Public |
| POST | `/categories` | ADMIN |
| PUT | `/categories/{id}` | ADMIN |
| DELETE | `/categories/{id}` | ADMIN |

## Products
| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/products` | Public | Chỉ trả sản phẩm `ACTIVE` |
| GET | `/products/{id}` | Public | |
| GET | `/products/admin/all` | ADMIN | Bao gồm cả `INACTIVE` |
| POST | `/products` | ADMIN | |
| PUT | `/products/{id}` | ADMIN | |
| DELETE | `/products/{id}` | ADMIN | Soft-delete → chuyển `INACTIVE` |

## Cart
Giỏ hàng luôn gắn với user đã xác thực từ token — **không** nhận `userId`
từ client.

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/cart` | Đã đăng nhập | Trả `{ cartId, items[], totalPrice, totalItems }` |
| POST | `/cart/items` | Đã đăng nhập | `{ productId, quantity }` — kiểm tra tồn kho từ DB |
| PUT | `/cart/items/{id}` | Đã đăng nhập | `{ quantity }` |
| DELETE | `/cart/items/{id}` | Đã đăng nhập | |
| DELETE | `/cart` | Đã đăng nhập | Xoá toàn bộ giỏ |

## Orders
| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/orders` | Đã đăng nhập | Checkout: `{ customerName, customerPhone, shippingAddress, paymentMethod }`. Transaction: validate stock → tạo order → tạo order_items (giá hiện tại = giá lịch sử) → trừ kho → tạo payment PENDING → xoá giỏ |
| GET | `/orders/my` | Đã đăng nhập | Đơn hàng của chính mình |
| GET | `/orders` | ADMIN | Toàn bộ đơn hàng |
| GET | `/orders/{id}` | Chủ đơn hoặc ADMIN | |
| PUT | `/orders/{id}/status` | ADMIN | `{ status }` |

## Payments
Mô phỏng — không có cổng thanh toán thật.

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/payments/order/{orderId}` | Chủ đơn hoặc ADMIN | |
| POST | `/payments/order/{orderId}/pay` | Chủ đơn hoặc ADMIN | "Pay now" — luôn thành công (trừ khi đơn đã bị huỷ), sinh `transactionId` giả `MOCK-<uuid>` |

## Admin
| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/admin/stats` | ADMIN | `{ totalUsers, totalProducts, totalOrders }` |

## Định dạng lỗi

Mọi lỗi trả về `{ "message": "..." }` với mã HTTP tương ứng
(`400`/`401`/`403`/`404`/`409`/`500`) — không bao giờ trả `200` cho một
thao tác thất bại.
