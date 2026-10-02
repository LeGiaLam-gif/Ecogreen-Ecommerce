# 🌿 EcoGreen — Website thương mại điện tử sản phẩm thân thiện môi trường

Đồ án chuyển đổi từ dự án "Phone Store Management" sang **EcoGreen**: một
ứng dụng Full-stack (Spring Boot + React + PostgreSQL) bán các sản phẩm sống
xanh (bình nước tái sử dụng, đồ dùng nhà bếp thân thiện môi trường, v.v.).

Đây là bản chuyển đổi **thật sự** (không phải đổi tên qua loa): toàn bộ
schema cơ sở dữ liệu, entity, service, controller và giao diện đã được
thiết kế lại cho đúng mô hình nghiệp vụ e-commerce, xem chi tiết trong
[`DATABASE.md`](./DATABASE.md) và [`API.md`](./API.md).

---

## 📋 Mục lục
- [Những gì đã thay đổi so với bản gốc](#-những-gì-đã-thay-đổi-so-với-bản-gốc)
- [Công nghệ sử dụng](#️-công-nghệ-sử-dụng)
- [Hướng dẫn cài đặt](#-hướng-dẫn-cài-đặt)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Giới hạn của phiên bản này](#-giới-hạn-của-phiên-bản-này)

---

## 🔄 Những gì đã thay đổi so với bản gốc

| Hạng mục | Bản gốc (Phone Store) | Bản EcoGreen |
|---|---|---|
| CSDL | 1 bảng `cart_item` chứa cả `product_name/price/image` trùng lặp; không có `roles`, `categories`, `order_items`, `payments` | Schema chuẩn hoá 3NF: `roles`, `users`, `user_roles`, `categories`, `products`, `carts`, `cart_items`, `orders`, `order_items`, `payments` |
| Xác thực | Không mã hoá mật khẩu, không có token thật | Mật khẩu băm bằng BCrypt; token phiên đăng nhập được server cấp phát và xác minh trên mọi API |
| Phân quyền | Frontend tự lưu `role` trong `localStorage`, backend không kiểm tra | Mọi API nhạy cảm được `AuthGuard` kiểm tra vai trò từ CSDL, không tin tưởng dữ liệu client gửi lên |
| Dữ liệu mẫu | Nút "Đồng bộ Assets" tự sinh hàng chục sản phẩm giả với giá ngẫu nhiên | Không seed dữ liệu nghiệp vụ; chỉ seed 2 role hệ thống. Ứng dụng chạy đúng với CSDL rỗng |
| Đặt hàng | Không có OrderItem, không snapshot giá, không trừ kho | `OrderService.checkout()` là một transaction: kiểm tra tồn kho từ DB → tạo đơn → lưu giá tại thời điểm mua → trừ kho → tạo payment "PENDING" → xoá giỏ hàng |
| Thanh toán | Không có | `PaymentService` mô phỏng (COD / MOCK_PAYMENT) — không gọi cổng thanh toán thật |

## 🛠️ Công nghệ sử dụng

### Backend
- Spring Boot 4.x / Java 21 / Spring Data JPA / Hibernate
- PostgreSQL 16
- `spring-security-crypto` (chỉ dùng `BCryptPasswordEncoder`, không dùng toàn bộ Spring Security filter chain — giữ đơn giản cho phiên bản đầu)

### Frontend
- React 19 (Vite)
- React Router
- Axios (qua lớp `services/http.js` tự gắn header `Authorization`)
- Context API cho `AuthContext` và `CartContext`

---

## 📦 Hướng dẫn cài đặt

### 1. Khởi tạo cơ sở dữ liệu (PostgreSQL)

```bash
# Cách A — dùng Docker (khuyên dùng)
cd docker
docker compose up -d
```

```bash
# Cách B — PostgreSQL cài sẵn trên máy
createdb ecogreen
psql -U postgres -d ecogreen -f database/init-postgres.sql
```

Xem `backend/src/main/resources/application.properties` để sửa
host/port/user/password nếu khác mặc định (`localhost:5432`, `postgres/123456`).

`database/init-postgres.sql` chỉ seed 2 role hệ thống (`USER`, `ADMIN`),
**không** seed user/sản phẩm/đơn hàng giả.

### 2. Chạy backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend chạy ở `http://localhost:8081`, kết nối PostgreSQL tại `localhost:5433`
(port map từ docker-compose). Sửa `backend/src/main/resources/application.properties` nếu bạn dùng
cổng/thông tin đăng nhập khác.

### 3. Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend chạy ở `http://localhost:5173` (mặc định Vite).

### 4. Tạo tài khoản Admin đầu tiên

Vì hệ thống không seed sẵn tài khoản, hãy:
1. Đăng ký một tài khoản bình thường qua giao diện (`/register`).
2. Gán quyền ADMIN cho tài khoản đó bằng SQL:

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'ten_dang_nhap_cua_ban' AND r.name = 'ADMIN';
```

3. Đăng nhập lại — menu **Admin** sẽ xuất hiện trên navbar.

### 5. Thêm danh mục & sản phẩm

Trang chủ sẽ trống cho tới khi admin thêm danh mục (tab **Categories**)
và sản phẩm (tab **Products**) trong Admin CMS — đây là hành vi có chủ đích,
không phải lỗi.

---

## 📁 Cấu trúc dự án

```
backend/
  src/main/java/com/example/backend/
    entity/       Role, User, Category, Product, Cart, CartItem, Order, OrderItem, Payment
    repository/   Spring Data JPA repositories
    service/      Nghiệp vụ (AuthService, CartService, OrderService, PaymentService, ...)
    controller/   REST endpoints (/api/...)
    security/     AuthTokenStore, AuthInterceptor, AuthGuard, WebConfig (CORS)
    dto/          Response DTO — không bao giờ trả password, không lỗi lazy-loading
    exception/    Exception + GlobalExceptionHandler → mã HTTP đúng nghĩa
    config/       DataLoader (chỉ seed roles)
database/
  init-postgres.sql   Schema chuẩn hoá (PostgreSQL) — xem DATABASE.md
frontend/
  src/
    context/      AuthContext, CartContext
    services/     Lớp gọi API theo domain (authApi, productApi, cartApi, orderApi, ...)
    pages/        Home, ProductDetail, Cart, Checkout, Payment, OrderSuccess, Orders, OrderDetail,
                   Login, Register, admin/AdminDashboard
    components/   Navbar, ProductCard, CategoryFilter, BannerSlider, ProtectedRoute, AdminStats
```

Xem thêm:
- [`DATABASE.md`](./DATABASE.md) — mô tả từng bảng, chuẩn hoá, ràng buộc.
- [`API.md`](./API.md) — danh sách endpoint, quyền truy cập, ví dụ request/response.

---

## ⚠️ Giới hạn của phiên bản này

Đây là bản đầu tiên, cố tình giữ đơn giản theo đúng yêu cầu ("không
over-engineer"). Một số điểm cần lưu ý nếu triển khai thật:

- **Token phiên đăng nhập lưu trong bộ nhớ (in-memory)** của backend —
  mất khi restart server, không phù hợp chạy nhiều instance. Bản production
  nên thay bằng JWT ký số hoặc session lưu Redis/DB.
- **Thanh toán là mô phỏng** (`PaymentService.payNow`) — không kết nối
  cổng thanh toán thật (VNPay/Momo/Stripe...).
- Ảnh sản phẩm là **tên file tham chiếu tới `frontend/src/assets/products/`**
  hoặc URL đầy đủ — chưa có chức năng upload ảnh từ Admin CMS.
- Chưa viết test tự động cho các luồng mới (auth/cart/checkout); backend
  cũng chưa được `mvn compile` trong môi trường tạo ra bản chuyển đổi này
  vì không có quyền truy cập Maven Central — hãy build & kiểm thử lại
  trước khi coi là hoàn chỉnh.
