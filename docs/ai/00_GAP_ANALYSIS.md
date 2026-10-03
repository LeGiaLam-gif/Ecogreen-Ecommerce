# EcoGreen — Gap Analysis: Hiện trạng vs Yêu cầu Production (SRS v1.0 + ERD cập nhật)

> Vế "Hiện trạng" trong tài liệu này được trích dẫn trực tiếp từ việc đọc toàn bộ source code thật (xem `EcoGreen_Tai_Lieu_Hoc_Tap.md`), không suy đoán. Vế "Yêu cầu" trích từ `EcoGreen_Project_Requirements.docx` và ảnh kiến trúc/ERD bạn gửi.

## 0. Quyết định kiến trúc lớn nhất — CẦN CHỐT TRƯỚC KHI LÀM BẤT CỨ GÌ KHÁC

Ảnh kiến trúc bạn gửi vẽ **Frontend = Next.js** và **Backend API = NestJS/Node.js**. Hệ thống hiện tại là **React (Vite, không phải Next.js)** + **Spring Boot (Java, không phải NestJS)**. Đây không phải một "gap" có thể vá — đây là **câu hỏi chọn hướng đi** quyết định toàn bộ phần còn lại của tài liệu này (ngôn ngữ, framework, cấu trúc thư mục, ORM dùng trong mọi prompt ở file `03_AGENT_PROMPTS.md`). Xem câu hỏi ở cuối cuộc trò chuyện này — ba file đầu (gap analysis, conventions, development plan) được viết để dùng được với CẢ HAI lựa chọn; chỉ riêng bộ prompt chi tiết theo từng khối công việc mới cần biết câu trả lời trước khi viết.

---

## 1. Kiến trúc tổng thể & hạ tầng

| Hạng mục | Hiện trạng | Yêu cầu SRS | Khoảng trống | Mức độ |
|---|---|---|---|---|
| Kiểu kiến trúc | Monolith 3 tầng đơn giản, 1 backend, 1 DB | Modular monolith trước, tách service sau khi cần (Section 17) | Thiếu ranh giới module rõ ràng trong code (service nào cũng autowire thẳng repository của service khác) | Trung bình |
| Cache | Không có | Redis (bắt buộc theo sơ đồ khối 2, 4) | Thiếu hoàn toàn | Trung bình |
| Message Queue | Không có | RabbitMQ (notification, analytics event async) | Thiếu hoàn toàn | Trung bình |
| Search Engine | Không có (lọc client-side) | PostgreSQL full-text đủ cho bản đầu, Elasticsearch sau | Thiếu kể cả tầng cơ bản | Trung bình |
| Object Storage | Không — `products.image` chỉ là string filename | S3/Cloudflare R2 + bảng `product_images` (url, storage_key, alt_text, sort_order) | Thiếu hoàn toàn | Trung bình |
| Containerize | `docker-compose.yml` **chỉ** chạy Postgres | Backend + Frontend đều phải có Dockerfile, deploy bằng container | Chưa có Dockerfile cho backend/frontend | Nhỏ–Trung bình |
| CI/CD | Không có pipeline nào | GitHub Actions: lint → test → build → image → deploy | Thiếu hoàn toàn | Trung bình |
| CDN/WAF | Không có | CDN cho static assets, WAF ở tầng trước LB | Thiếu hoàn toàn | Nhỏ (hạ tầng, không phải code) |

## 2. Authentication & Authorization

| Hạng mục | Hiện trạng | Yêu cầu SRS | Khoảng trống | Mức độ |
|---|---|---|---|---|
| Token | `UUID` ngẫu nhiên lưu `ConcurrentHashMap` trong RAM | "JWT/session security" (Section 9) | Không có JWT, không ký số, mất khi restart, không scale ngang | Trung bình |
| Số role | 2 role cứng: `USER`, `ADMIN` | 3 role: `CUSTOMER`, `MANAGER`, `ADMIN` + **permission string** chi tiết (`product:create`, `order:view`...) | Thiếu hẳn tầng permission — hiện tại chỉ có `isAdmin` boolean, không có RBAC thật theo nghĩa permission-based | **Lớn** |
| Bảng permissions | Không có | Bảng `permissions`, `roles`, `user_roles` tách biệt + bảng map role↔permission | Thiếu bảng, thiếu toàn bộ cơ chế enforce theo permission (hiện chỉ enforce theo role) | Lớn |
| Google OAuth | Có, nhưng **không verify chữ ký token với Google** (đã phát hiện ở audit trước) | OAuth là optional extension nhưng phải an toàn | Lỗ hổng auth-bypass thật cần vá trước khi lên production | **Cao (bảo mật)** |
| Audit log hành vi admin | Không có | Bắt buộc (Section 11) | Thiếu bảng `audit_logs` + middleware ghi log | Trung bình |

## 3. Product Management

| Trường/field | Có trong `Product` hiện tại? | SRS yêu cầu | Gap |
|---|---|---|---|
| id, name, description, price | ✅ | ✅ | — |
| `slug` | ❌ | ✅ (bắt buộc cho SEO/URL) | Thiếu |
| `compare_price` (giá gạch ngang) | ❌ | ✅ | Thiếu |
| `sku` | ❌ | ✅ | Thiếu |
| `brand` | ❌ | ✅ | Thiếu |
| `images` (nhiều ảnh) | ❌ (chỉ 1 field `image` string) | ✅ (bảng `product_images` riêng) | Thiếu hoàn toàn |
| `status` | Chỉ `ACTIVE/INACTIVE` | `DRAFT, ACTIVE, OUT_OF_STOCK, INACTIVE, ARCHIVED` (5 giá trị) | Thiếu 3 trạng thái |
| Stock tách riêng | Nằm thẳng trong `Product.stockQuantity` | Phải tách qua bảng `inventory` riêng (xem mục 6) | Vi phạm nguyên tắc tách domain |
| API search/filter/sort/pagination | **Không có** — `GET /api/products` không nhận bất kỳ query param nào | Bắt buộc (Section 4.15, 8) | Thiếu hoàn toàn ở tầng backend |

## 4. Category Management

| Hạng mục | Hiện trạng | Yêu cầu | Gap |
|---|---|---|---|
| Cấu trúc | Phẳng (`id, name, description`) | Cây cha/con (`parent_id`) | Thiếu `parent_id`, thiếu `slug` |

## 5. Product Image Management

Hiện tại: `Product.image` là 1 cột `VARCHAR(255)` lưu tên file, không có object storage, không có upload API thật.
Yêu cầu: bảng `product_images` riêng (`image_url, storage_key, alt_text, sort_order`), luồng `Frontend → Upload API → Object Storage → URL → Database`.
**→ Thiếu hoàn toàn, cần xây mới.**

## 6. Inventory Management

| Hạng mục | Hiện trạng | Yêu cầu | Gap |
|---|---|---|---|
| Available vs Reserved stock | Chỉ có 1 cột `stockQuantity`, trừ thẳng lúc checkout | Phải tách `available_quantity` và `reserved_quantity` | **Thiếu hoàn toàn mô hình reservation** |
| Giải phóng reservation khi thanh toán fail | Không áp dụng được vì không có reservation | Bắt buộc | Thiếu |
| Bảng lịch sử giao dịch kho | Không có (bảng `inventory_transactions` chúng ta từng thêm ở bài tập load-test là cho mục đích khác, không phải tính năng nghiệp vụ thật) | Bắt buộc, phục vụ truy vết | Thiếu |
| Race condition khi 2 người mua cùng lúc | `OrderService.checkout()` chỉ đọc-rồi-ghi (read-then-write) trong 1 transaction DB thường — **không có row-level lock tường minh** (`SELECT ... FOR UPDATE`) | DoD yêu cầu "không oversell do race condition cơ bản" | Rủi ro thật: 2 transaction đọc cùng `stockQuantity` trước khi transaction đầu commit vẫn có thể oversell tuỳ mức cô lập (isolation level) mặc định của Postgres (READ COMMITTED) | **Cao** |

## 7. Shopping Cart & 8. Checkout

Về cơ bản **khớp khá tốt** với yêu cầu: giỏ hàng server-side, re-validate giá/tồn kho/status lúc checkout thay vì tin frontend — đúng nguyên tắc Section 17 ("Frontend must not be the source of truth"). Khoảng trống: chưa có bước "Apply eligible discounts" (không có khái niệm coupon/discount nào trong code hiện tại) và chưa có "Calculate shipping cost" (shipping luôn ẩn, không tính phí ship).

## 8. Order Management — state machine

| | Hiện tại | Yêu cầu |
|---|---|---|
| Số trạng thái | 4: `PENDING, CONFIRMED, PAID, CANCELLED` | ≥10: `PENDING_PAYMENT, PAID, PROCESSING, PACKED, SHIPPED, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED` |
| Validate transition | **Không có** — `updateStatus()` nhận bất kỳ string nào hợp enum, không kiểm tra đường đi hợp lệ (có thể từ `PENDING` nhảy thẳng `PAID` hoặc đổi trạng thái của đơn đã `CANCELLED`) | Bắt buộc phải validate transition hợp lệ (Section 4.8) |
| `order_status_history` | Không tồn tại trong nghiệp vụ thật (chỉ có ở script test-data riêng, không nối với `OrderService`) | Bắt buộc ghi lại mọi lần đổi trạng thái | Thiếu tích hợp thật |

**→ Đây là domain cần viết lại gần như hoàn toàn**, không phải vá thêm.

## 9. Payment

Hiện tại: `PaymentService` mock 100%, `payNow()` luôn trả `SUCCESS` ngay lập tức, không có webhook, không gọi ra ngoài.
Yêu cầu: tích hợp cổng thật (VNPay/MoMo/Stripe), luồng `Frontend → Backend → Gateway → Webhook/IPN → Backend cập nhật trạng thái`, đủ 5 status (`PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED` — hiện chỉ có 3, thiếu `CANCELLED`, `REFUNDED`).
**→ Phải viết lại toàn bộ `PaymentService` + thêm endpoint webhook (hiện không tồn tại endpoint nào nhận callback từ bên ngoài).**

## 10. Return & Refund

Đây là domain **khớp tốt nhất** với yêu cầu trong toàn bộ hệ thống hiện tại — đã có `ReturnRequest` với `reason/description/imageUrl/status/adminNote`, đúng tinh thần Section 4.10. Khoảng trống: chỉ hỗ trợ **1 ảnh** (yêu cầu "supporting images" số nhiều), chưa có bảng `return_items` (đổi/trả từng sản phẩm riêng trong đơn, hiện chỉ đổi/trả theo cả đơn), và duyệt `APPROVED` chưa tự tạo `refund`/hoàn kho (đã nêu ở audit trước).

## 11. Customer Management

Hiện tại: `UserController`/`UserService` chỉ có list + delete (xoá cứng). Thiếu: xem chi tiết lịch sử đơn của 1 khách từ phía admin, enable/disable account (có field `active` trên `User` nhưng **không có endpoint nào set nó thành `false`** — chỉ dùng để tự động set `true` lúc tạo), không có bảng địa chỉ (`addresses`) riêng — "quản lý địa chỉ" trong yêu cầu 3.1 chưa tồn tại.

## 12. Admin Dashboard & Analytics

Khớp tương đối tốt về phần "doanh thu theo ngày/tháng, top sản phẩm, tóm tắt" (`ReportService`/`StatsController`). Thiếu: low-stock products, pending orders count, số tiền refund, customer purchase frequency, order trends chi tiết hơn.

## 13. Analytics Events

**Thiếu hoàn toàn.** Không có bảng `analytics_events`, không có bất kỳ event nào được ghi lại (`product_view`, `add_to_cart`, `checkout_started`...). Đây là nền tảng cho AI/BI sau này (đúng hướng bạn muốn ở các lượt trò chuyện trước) — cần xây từ đầu.

## 14. Notification

**Thiếu hoàn toàn.** Không gửi email/SMS cho bất kỳ sự kiện nào (đăng ký, đặt hàng, thanh toán...). Không có bảng `notifications`, không có hàng đợi xử lý async.

## 15. Search

`GET /api/products` không nhận tham số nào — lọc tìm kiếm (`searchTerm`) hiện chạy **hoàn toàn ở client** trong `Home.jsx` (lọc mảng JS sau khi tải hết sản phẩm về). Cách này chỉ chịu được vài trăm sản phẩm; với catalog lớn sẽ tải chậm và lãng phí băng thông. **Thiếu toàn bộ tầng search/filter/pagination ở backend.**

## 16. Cấu trúc API

Hiện tại dùng đúng nhóm domain (`/api/auth, /api/products, /api/cart, /api/orders...`) — khớp tinh thần Section 5. Thiếu: không có versioning (`/api/v1`), không có nhóm `/api/inventory` và `/api/analytics` riêng (đang gộp vào `/api/admin/reports`).

## 17. Bảo mật (Section 9)

| Yêu cầu | Hiện trạng |
|---|---|
| HTTPS | Chỉ dev, chưa cấu hình |
| Rate limiting | Không có — `/api/auth/login` có thể bị brute-force |
| CORS policy | Hard-code 1 origin `localhost:5173` |
| CSRF | Không áp dụng (dùng Bearer token nên rủi ro thấp hơn, nhưng cần xác nhận lại khi đổi sang cookie nếu chuyển sang Next.js SSR) |
| SQL injection | An toàn — toàn bộ dùng JPQL tham số hoá, không có raw SQL nối chuỗi |
| Secret management | `application.properties` hiện có thể chứa giá trị thật trực tiếp (cần kiểm tra lại, không commit `.env`) |

## 18. Testing

Toàn bộ backend chỉ có **1 file test duy nhất** (`BackendApplicationTests.java`), và bên trong **rỗng** (`contextLoads()` không assert gì). Frontend **không có test nào**. Yêu cầu Section 12 cần unit test (tính giá, tồn kho, permission, payment state), integration test, và E2E test. **→ Gap gần như 100%, đây là hạng mục cần đầu tư công sức riêng, độc lập với các domain khác.**

## 19. Observability

Không có logging có cấu trúc, không có health check endpoint, không có Prometheus/Sentry. **Thiếu hoàn toàn.**

---

## Tổng kết độ ưu tiên (để đưa vào kế hoạch ở file `02_DEVELOPMENT_PLAN.md`)

| Mức độ | Domain |
|---|---|
| **Phải sửa trước khi làm gì khác (bảo mật)** | Google OAuth verify token, rate limiting login |
| **Lớn — viết lại gần như hoàn toàn** | Order state machine, Payment (gateway thật), RBAC permission-based, Inventory reservation |
| **Trung bình — mở rộng trên nền hiện có** | Product (thêm field + image table), Category (cây cha/con), Return/Refund (hoàn thiện), Customer management, Admin dashboard |
| **Xây mới hoàn toàn, độc lập, song song được** | Analytics events, Notification, Search, Audit log, Testing, CI/CD, Observability |
