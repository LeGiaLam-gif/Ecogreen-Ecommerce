# EcoGreen — Kế hoạch phát triển theo khối công việc (Work Breakdown)

> Đọc sau `00_GAP_ANALYSIS.md` và `01_CONVENTIONS.md`. File này chia dự án thành 13 khối công việc (Block) độc lập đủ để giao cho các agent/account khác nhau, kèm thứ tự phụ thuộc bắt buộc.

## 1. Sơ đồ phụ thuộc giữa các Block

```
B01 Auth/RBAC ──┬──▶ B02 Catalog ──┬──▶ B03 Inventory ──┐
                │                  │                     ▼
                ├──▶ B08 Customer  │                 B04 Cart/Checkout
                │                  │                     │
                │                  │                     ▼
                │                  │                 B05 Order ──┬──▶ B06 Payment
                │                  │                             │
                │                  │                             └──▶ B07 Return/Refund
                │                  │
                └──────────────────┴────────────────▶ B09 Analytics/Dashboard (đọc dữ liệu từ B05/B06/B07)

B10 Notification   — độc lập, chỉ LẮNG NGHE event từ các block khác, chạy song song bất cứ lúc nào
B11 Frontend Storefront — phụ thuộc B02, B04, B05 có API thật (mock API trước nếu cần chạy song song sớm hơn)
B12 Frontend Admin      — phụ thuộc toàn bộ API block
B13 Infra/DevOps        — độc lập hoàn toàn, chạy song song từ ngày đầu tiên
```

**Quy tắc vàng**: một Block chỉ bắt đầu code thật khi Block nó phụ thuộc đã công bố xong `CONTRACT.md` (không cần đợi code xong hẳn, chỉ cần hợp đồng giao diện ổn định) — nhờ vậy nhiều agent chạy song song được thay vì xếp hàng chờ nhau.

## 2. Mô tả từng Block

### B01 — Authentication & RBAC (nền tảng, làm đầu tiên)
**Mục tiêu**: thay thế token RAM bằng JWT, xây bảng `permissions` thật, vá lỗ hổng Google OAuth.
**Đầu ra**: `users, roles, permissions, role_permissions, user_roles` migration; API `/api/v1/auth/*`; middleware/guard kiểm tra permission theo chuỗi `resource:action`; test cho từng permission.
**Rủi ro cao nhất cần né**: không verify Google token giống hệ thống cũ (xem gap analysis mục 2).

### B02 — Catalog (Product + Category + Image)
**Mục tiêu**: mở rộng Product đủ field SRS yêu cầu, category cây cha/con, bảng `product_images`, API search/filter/pagination thật ở backend.
**Đầu ra**: migration `products, categories, product_images`; API `/api/v1/products`, `/api/v1/categories` (public + admin); upload ảnh qua object storage.

### B03 — Inventory (reservation model)
**Mục tiêu**: tách `available_quantity`/`reserved_quantity`, bảng `inventory_transactions`, hàm `reserveStock()`/`releaseStock()`/`commitStock()`, xử lý race condition bằng lock tường minh.
**Đầu ra**: migration `inventory, inventory_transactions`; các hàm nghiệp vụ core mà B04/B05 sẽ gọi.

### B04 — Cart & Checkout
**Mục tiêu**: giữ nguyên tinh thần đúng của hệ thống cũ (server-side cart, re-validate mọi thứ), thêm bước discount + shipping cost, gọi `B03.reserveStock()` thay vì tự trừ kho.
**Đầu ra**: `carts, cart_items`; API `/api/v1/cart`, `/api/v1/checkout`.

### B05 — Order (state machine)
**Mục tiêu**: viết lại hoàn toàn state machine (10 trạng thái), validate transition, ghi `order_status_history` mỗi lần đổi.
**Đầu ra**: `orders, order_items, order_status_history`; API `/api/v1/orders`; hàm `transitionOrderStatus(orderId, newStatus)` là nơi DUY NHẤT được phép đổi `orders.status` trong toàn hệ thống (B06, B07 gọi hàm này, không tự `UPDATE` thẳng).

### B06 — Payment (gateway thật)
**Mục tiêu**: tích hợp 1 cổng thật (đề xuất VNPay hoặc MoMo — phổ biến tại VN, sandbox miễn phí để test), endpoint webhook nhận IPN, validate chữ ký webhook.
**Đầu ra**: `payments`; API `/api/v1/payments`, `/api/v1/payments/webhook/:provider` (endpoint này KHÔNG qua auth thường, tự validate bằng chữ ký riêng của gateway).

### B07 — Return & Refund
**Mục tiêu**: mở rộng `ReturnRequest` hiện có thành `returns + return_items`, nhiều ảnh, tự động gọi `B06` tạo refund + `B03.releaseStock()`/cộng kho khi `APPROVED`.

### B08 — Customer Management
**Mục tiêu**: thêm bảng `addresses`, endpoint enable/disable account (field `active` đã có nhưng chưa có endpoint set), xem lịch sử đơn của 1 khách từ phía admin.

### B09 — Analytics Events & Admin Dashboard
**Mục tiêu**: bảng `analytics_events`, ghi nhận các event SRS liệt kê (Section 4.13), mở rộng dashboard (low-stock, pending orders, refund amount, purchase frequency).
**Lưu ý**: đây là module CHỈ ĐỌC dữ liệu từ các block khác (hoặc đọc qua event queue), không bao giờ ghi ngược vào bảng của B05/B06/B07.

### B10 — Notification
**Mục tiêu**: bảng `notifications`, consumer lắng nghe event (order_created, payment_success, return_approved...) từ queue, gửi email (SMTP/Resend/SendGrid tuỳ hạ tầng chọn ở B13), không chặn luồng chính (phải là async).

### B11 — Frontend Storefront
**Mục tiêu**: route customer-facing, state layer, service layer gọi API đúng envelope ở `01_CONVENTIONS.md`.

### B12 — Frontend Admin
**Mục tiêu**: CMS quản trị, route group riêng, mọi permission-sensitive UI phải ẩn/hiện dựa theo permission thật trả về từ `/api/v1/auth/me` (không hard-code theo role tên suông).

### B13 — Infra, CI/CD, Observability, Testing framework
**Mục tiêu**: Dockerfile cho từng service, `docker-compose` đầy đủ (Postgres + Redis + RabbitMQ), GitHub Actions pipeline, cấu hình logging/health-check, dựng khung test (test runner, coverage threshold) để các block khác viết test vào.
**Chạy song song ngay từ Phase 1** — không phụ thuộc block nghiệp vụ nào.

## 3. Lộ trình theo Phase (ánh xạ 13 Block vào 6 Phase của SRS Section 14)

| Phase SRS | Block tương ứng | Có thể chạy song song |
|---|---|---|
| Phase 1 - Foundation | B01, B13 (khởi tạo) | B01 và B13 song song ngay từ đầu |
| Phase 2 - Commerce Core | B02, B03, B04, B05 | B02 xong hợp đồng → B03 bắt đầu; B04 chờ hợp đồng B02+B03 |
| Phase 3 - Payment | B06 | Chờ hợp đồng B05 |
| Phase 4 - Management | B07, B08, B09, B12 | B07/B08 song song nhau; B09 chờ B05/B06 có dữ liệu thật |
| Phase 5 - Production | B13 (hoàn thiện: CI/CD thật, deploy) | — |
| Phase 6 - Scale | B10 hoàn thiện async thật, Redis/Queue production-grade | — |

Frontend (B11, B12) chạy xuyên suốt Phase 2–4, bắt đầu ngay khi `CONTRACT.md` của API tương ứng công bố (không cần đợi backend code xong — dùng mock server generate từ `CONTRACT.md`).

## 4. Thứ tự khuyến nghị nếu chỉ có 1 agent làm tuần tự (không có nhiều account song song)

1. B13 (khung sườn: Docker, migration tool, test runner) — làm trước để mọi block sau có nền chạy test ngay.
2. B01 → B02 → B03 → B04 → B05 → B06 → B07 (đúng chuỗi phụ thuộc chính).
3. B08, B09, B10 (có thể xen kẽ, không chặn đường chính).
4. B11, B12 song song với bước 2–3 ngay khi từng API endpoint có hợp đồng.
5. Hoàn thiện B13 (CI/CD thật, observability, deploy) ở cuối.

## 5. Bước tiếp theo

Sau khi bạn xác nhận lựa chọn công nghệ (xem câu hỏi cuối cuộc trò chuyện), file `03_AGENT_PROMPTS.md` sẽ chứa **13 prompt chi tiết**, mỗi prompt tương ứng 1 Block ở trên, đủ để copy nguyên văn giao cho 1 agent/account riêng biệt — gồm chính xác tên entity/field/kiểu dữ liệu, chữ ký hàm, path + request/response của từng endpoint, và danh sách test case bắt buộc phải viết.
