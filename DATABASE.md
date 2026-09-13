# Thiết kế cơ sở dữ liệu — EcoGreen

Nguồn: [`database/init-postgres.sql`](./database/init-postgres.sql). Tham khảo cấu trúc từ
sơ đồ schema EcoGreen do người dùng cung cấp (users/roles/orders/order_items/
products/categories/payments/shipping_addresses), rút gọn `shipping_addresses`
thành các cột snapshot ngay trong `orders` cho phiên bản đầu (xem phần
"Khác biệt so với sơ đồ tham khảo" bên dưới).

## Sơ đồ quan hệ (rút gọn)

```
roles ──┬─< user_roles >──┬── users ──< carts ──< cart_items >── products >── categories
        │                 │
        │                 └── orders ──< order_items >── products
        │                        │
        │                        └── payments
```

## Các bảng

### roles
Cấu hình hệ thống, không phải dữ liệu nghiệp vụ. Seed sẵn `USER`, `ADMIN`.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR(50) UNIQUE | |

### users
| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGINT PK | |
| username | VARCHAR(50) UNIQUE | |
| email | VARCHAR(100) UNIQUE | |
| password | VARCHAR(255) | Luôn là **hash BCrypt**, không bao giờ lưu plaintext |
| is_active | BOOLEAN | |
| created_at / updated_at | TIMESTAMP | |

### user_roles
Bảng trung gian N-N giữa `users` và `roles`. Khoá chính composite `(user_id, role_id)`.

### categories
| Cột | Kiểu |
|---|---|
| id | BIGINT PK |
| name | VARCHAR(100) UNIQUE |
| description | TEXT |

### products
| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGINT PK | |
| category_id | BIGINT FK → categories | |
| name | VARCHAR(150) | |
| description | TEXT | |
| price | DECIMAL(12,2) | CHECK >= 0 |
| stock_quantity | INT | CHECK >= 0 |
| image | VARCHAR(255) | **Chỉ là đường dẫn/tên file tham chiếu**, không lưu binary |
| status | ENUM('ACTIVE','INACTIVE') | Xoá sản phẩm = chuyển INACTIVE (soft delete), để order_items cũ vẫn còn tham chiếu hợp lệ |

### carts / cart_items
- `carts`: mỗi `user_id` có đúng **một** cart (`UNIQUE`), quan hệ 1-1.
- `cart_items`: **không** lưu `product_name/price/image` — luôn đọc trực
  tiếp từ `products` qua `product_id` để tránh dữ liệu trùng lặp/lệch nhau
  (đúng chuẩn 3NF). `UNIQUE(cart_id, product_id)` đảm bảo 1 sản phẩm chỉ có
  1 dòng trong giỏ (cộng dồn `quantity` khi thêm lại).

### orders
Lưu **snapshot** thông tin giao hàng tại thời điểm đặt hàng
(`customer_name`, `customer_phone`, `shipping_address`) — không phụ thuộc
vào hồ sơ user hiện tại, để lịch sử đơn hàng không bị thay đổi nếu sau này
user cập nhật thông tin cá nhân.

| Cột | Kiểu |
|---|---|
| status | ENUM('PENDING','CONFIRMED','PAID','CANCELLED') |
| total_price | DECIMAL(12,2) |

### order_items
`price` là **giá sản phẩm tại thời điểm mua hàng** — không bao giờ tính lại
từ giá hiện tại của `products` (giá có thể đã thay đổi sau này).

### payments
Mô phỏng thanh toán (`payment_method`: `COD` | `MOCK_PAYMENT`). Không kết
nối cổng thanh toán thật. `status`: `PENDING` → `SUCCESS`/`FAILED`.

## Chuẩn hoá (1NF/2NF/3NF)

- **1NF**: mọi cột chứa giá trị nguyên tố (không có danh sách/JSON nhồi
  trong 1 cột), có khoá chính trên mọi bảng.
- **2NF**: không có bảng khoá chính ghép nào bị phụ thuộc bộ phận — bảng
  ghép duy nhất (`user_roles`) không có cột phụ thuộc nào ngoài 2 khoá.
- **3NF**: loại bỏ phụ thuộc bắc cầu — ví dụ `cart_items` không lưu lại
  `product_name/price` (phụ thuộc vào `product_id` chứ không phụ thuộc
  trực tiếp vào khoá `cart_items.id`).

## Chính sách dữ liệu mẫu

`init-postgres.sql` **chỉ** seed bảng
`roles`. Không có `INSERT` nào cho `users/categories/products/orders/order_items/payments`. Ứng dụng phải
chạy đúng (không lỗi, hiển thị trạng thái rỗng hợp lý) khi các bảng này
trống — xem `Home.jsx` ("No products available yet") và `AdminDashboard.jsx`
("No products/categories/orders/users yet").

## Khác biệt so với sơ đồ tham khảo

Sơ đồ ảnh do người dùng cung cấp có thêm bảng `shipping_addresses`
(nhiều địa chỉ giao hàng đã lưu sẵn cho mỗi user) và `order_items` tách
riêng khỏi `orders`. Phiên bản này:
- **Giữ** `order_items` riêng, đúng như sơ đồ.
- **Không tạo** bảng `shipping_addresses` riêng ở bản đầu — địa chỉ giao
  hàng được nhập trực tiếp ở bước checkout và lưu snapshot ngay trong
  `orders` (`shipping_address`), để tránh over-engineer một tính năng
  "sổ địa chỉ" chưa được yêu cầu rõ trong PROMPT.docx. Đây là điểm có thể
  bổ sung sau như bảng `shipping_addresses(id, user_id, recipient_name,
  phone, address_line1, address_line2, city, is_default, ...)` và cho
  phép chọn nhanh khi checkout.
