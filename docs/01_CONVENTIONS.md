# EcoGreen — Quy ước chung (CONVENTIONS)

> **Mọi agent/account tham gia dự án BẮT BUỘC đọc file này trước khi viết bất kỳ dòng code nào.** Mục tiêu duy nhất của file này: để code do nhiều agent khác nhau viết ra, trong các phiên làm việc khác nhau, **ghép lại được với nhau** mà không cần viết lại.

## 1. Nguyên tắc tối thượng (không được vi phạm, từ SRS Section 17)

1. Frontend không bao giờ truy cập database trực tiếp.
2. Frontend không bao giờ là nguồn sự thật (source of truth) cho: giá, tổng tiền, trạng thái thanh toán, trạng thái đơn, tồn kho. Mọi con số hiển thị chỉ là "bản sao để xem", backend luôn tính lại từ đầu.
3. Mọi permission nhạy cảm được enforce ở **backend**, route guard ở frontend chỉ là UX.
4. Một agent chỉ được sửa file **trong phạm vi module được giao** (xem mục 7 — Bảng phân vùng sở hữu). Muốn sửa file dùng chung (shared/core), phải nêu rõ trong PR description và gắn nhãn `shared-change`.
5. Không agent nào được tự ý đổi quy ước ở file này. Nếu thấy quy ước không hợp lý, ghi vào mục "Đề xuất thay đổi quy ước" ở cuối file, không tự sửa rồi code theo cách khác.

## 2. Quy ước đặt tên

### 2.1. Database (PostgreSQL)

- Tên bảng: số nhiều, `snake_case` — `product_images`, `order_status_history`, `return_items`.
- Tên cột: `snake_case` — `created_at`, `stock_quantity`, `order_id`.
- Khoá chính: luôn tên `id`. Khoá ngoại: `<tên_bảng_số_ít>_id` — `product_id`, `order_id`, `user_id`.
- Timestamp: mọi bảng nghiệp vụ đều có `created_at TIMESTAMP NOT NULL DEFAULT now()`; bảng có thể sửa thì thêm `updated_at TIMESTAMP`. Không dùng tên khác (`createdDate`, `date_created`...).
- Enum lưu dưới dạng `VARCHAR` + `CHECK constraint` liệt kê rõ giá trị hợp lệ — không dùng kiểu `ENUM` native của Postgres (khó migrate thêm giá trị sau này).
- Khoá chính dùng `BIGSERIAL`/`IDENTITY` (số nguyên tự tăng) cho mọi bảng nghiệp vụ nội bộ. Chỉ dùng `UUID` cho các giá trị **lộ ra ngoài qua API công khai** nếu cần tránh đoán được ID tuần tự (ví dụ token, `storage_key`) — quyết định theo từng bảng, ghi rõ trong migration.

### 2.2. API

- Base path: `/api/v1/...` — **bắt buộc versioning ngay từ đầu** (hệ thống cũ thiếu cái này, đây là lúc sửa).
- Resource dùng danh từ số nhiều, `kebab-case` nếu nhiều từ: `/api/v1/order-items`, không dùng động từ trong path (`/api/v1/getOrders` là SAI).
- Method HTTP đúng chuẩn REST: `GET` đọc, `POST` tạo, `PUT` thay toàn bộ, `PATCH` sửa 1 phần, `DELETE` xoá/soft-delete.
- Mọi endpoint **list** phải hỗ trợ query param: `page` (mặc định 0), `size` (mặc định 20, tối đa 100), `sort` (`field,asc|desc`). Không có ngoại lệ, kể cả khi hiện tại ít dữ liệu — thiếu cái này là gap đã nêu ở `00_GAP_ANALYSIS.md` mục 15.
- Envelope response thống nhất cho **mọi** endpoint:

```json
// Thành công — 1 object
{ "data": { ... }, "meta": null }

// Thành công — list có phân trang
{ "data": [ ... ], "meta": { "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 } }

// Lỗi — LUÔN theo đúng hình dạng này, mọi mã lỗi
{ "error": { "code": "VALIDATION_ERROR", "message": "Giá sản phẩm phải lớn hơn 0.", "fields": { "price": "must be >= 0" } } }
```

- Bảng mã lỗi chuẩn hoá (dùng field `error.code`, agent nào thêm lỗi mới phải bổ sung vào bảng này):

| HTTP Status | `error.code` | Khi nào dùng |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Input sai định dạng/thiếu field bắt buộc |
| 400 | `BUSINESS_RULE_VIOLATION` | Đúng định dạng nhưng vi phạm luật nghiệp vụ (vd hết hàng) |
| 401 | `UNAUTHENTICATED` | Chưa đăng nhập / token hết hạn |
| 403 | `FORBIDDEN` | Đã đăng nhập nhưng thiếu permission |
| 404 | `NOT_FOUND` | Không tìm thấy resource |
| 409 | `CONFLICT` | Trùng unique constraint (username, email, sku...) |
| 429 | `RATE_LIMITED` | Vượt rate limit |
| 500 | `INTERNAL_ERROR` | Lỗi hệ thống không lường trước — KHÔNG BAO GIỜ lộ chi tiết exception thật ra ngoài |

### 2.3. Permission string (RBAC)

Format bắt buộc: `<resource>:<action>` — toàn chữ thường, nối bằng dấu `:`. Danh sách permission **phải** định nghĩa trong 1 file duy nhất (`permissions.const.ts` hoặc `Permissions.java` tuỳ stack), không được hard-code chuỗi permission rải rác trong code.

Danh sách permission tối thiểu (SRS Section 4.1 + bổ sung theo gap analysis):

```
product:create   product:update   product:delete   product:view   product:publish
category:create  category:update  category:delete  category:view
inventory:view   inventory:update inventory:adjust
order:view       order:view_all   order:update     order:cancel
payment:view     payment:refund
return:view      return:process
user:view        user:update      user:disable
analytics:view
audit:view
system:configure
```

### 2.4. Code (áp dụng theo stack đã chốt — xem `03_AGENT_PROMPTS.md`)

- Tên class: `PascalCase`. Tên biến/hàm: `camelCase`. Hằng số: `UPPER_SNAKE_CASE`.
- Tên file **khớp chính xác** tên class/component bên trong (không có ngoại lệ).
- Mỗi module nghiệp vụ có cấu trúc thư mục giống nhau tuyệt đối (xem mục 7), để agent khác mở vào là biết ngay chỗ tìm gì mà không cần hỏi.
- Hàm xử lý nghiệp vụ không bao giờ đặt tên mơ hồ (`doStuff`, `handle`, `process`) — luôn là `<verb><Noun>`: `calculateOrderTotal`, `reserveInventory`, `validateStateTransition`.

### 2.5. Git

- Nhánh: `feature/<ma-khoi-cong-viec>-<mo-ta-ngan>` — ví dụ `feature/B04-inventory-reservation`. Mã khối công việc (`B01, B02...`) lấy từ `02_DEVELOPMENT_PLAN.md`.
- Commit message: `<B-code>: <mô tả ngắn, thì hiện tại>` — ví dụ `B04: add reserve/release inventory functions`.
- Không bao giờ commit trực tiếp vào `main`/`develop`. Mọi thay đổi qua Pull Request, dù chỉ 1 agent làm việc.
- PR description bắt buộc có mục "Phạm vi file đã sửa" liệt kê đường dẫn — để người review (hoặc agent review) biết ngay có đụng vào file ngoài phạm vi được giao hay không.

### 2.6. Biến môi trường

- Format: `UPPER_SNAKE_CASE`, nhóm theo prefix domain: `DB_*, REDIS_*, JWT_*, PAYMENT_*, STORAGE_*, SMTP_*`.
- Mọi biến môi trường **bắt buộc** phải có dòng tương ứng trong `.env.example` (không giá trị thật) — agent thêm biến mới mà quên cập nhật `.env.example` coi như chưa xong việc.
- Không bao giờ: commit `.env` thật, hard-code secret trong code, log giá trị secret ra console/log file.

## 3. Quy ước dữ liệu dùng chung (mọi agent phải tuân theo khi thiết kế entity/model)

| Khái niệm | Quy ước |
|---|---|
| Tiền tệ | Luôn lưu dạng số nguyên **đơn vị nhỏ nhất** (VND không có phần thập phân, nên lưu thẳng VND dạng `BIGINT`, không dùng `DECIMAL` để tránh sai số dấu phẩy động khi tính toán nhiều bước) |
| Thời gian | Lưu UTC trong DB, convert sang giờ VN (`Asia/Ho_Chi_Minh`) chỉ ở tầng hiển thị (frontend) |
| Trạng thái (status) | Luôn là chuỗi hoa, có `CHECK constraint`, danh sách giá trị hợp lệ định nghĩa 1 nơi duy nhất, dùng chung giữa DB/backend/frontend (không để mỗi tầng tự liệt kê 1 danh sách) |
| Soft-delete | Dùng cột `status = 'ARCHIVED'`/`'INACTIVE'`, không xoá cứng dữ liệu đã từng xuất hiện trong đơn hàng (product, category) |
| Phân trang | `page` bắt đầu từ `0`, không phải `1` |

## 4. Mỗi khối công việc (Work Block) PHẢI công bố "Hợp đồng giao diện" (Interface Contract)

Vì nhiều agent làm song song, một block không được tự ý đổi hình dạng dữ liệu mà block khác đang phụ thuộc vào. Trước khi code, mỗi block phải viết ra (trong file `CONTRACT.md` ngay trong thư mục module của mình):

- Entity/Model: tên field, kiểu dữ liệu, ràng buộc.
- Function signature public (input/output types) mà module khác được phép gọi.
- API endpoint: path, method, request body shape, response shape, mã lỗi có thể trả về.
- Event (nếu có publish lên queue): tên event, payload shape.

Nếu block B (phụ thuộc block A) thấy "Hợp đồng" của A thiếu gì đó mình cần, **không tự thêm vào code của A** — ghi vào mục "Yêu cầu bổ sung hợp đồng" trong `CONTRACT.md` của A và chờ A cập nhật.

## 5. Bảng phân vùng sở hữu (Module Ownership) — mẫu, điền cụ thể khi giao việc thật

| Block | Thư mục sở hữu | Agent/Account phụ trách | Phụ thuộc vào Block nào |
|---|---|---|---|
| B01 - Auth/RBAC | `modules/auth/**` | _(điền khi giao việc)_ | Không |
| B02 - Catalog (Product/Category) | `modules/catalog/**` | | B01 (permission check) |
| B03 - Inventory | `modules/inventory/**` | | B02 |
| B04 - Cart/Checkout | `modules/cart/**`, `modules/checkout/**` | | B02, B03 |
| B05 - Order | `modules/order/**` | | B04 |
| B06 - Payment | `modules/payment/**` | | B05 |
| B07 - Return/Refund | `modules/return/**` | | B05, B06 |
| B08 - Customer Mgmt | `modules/user/**` | | B01 |
| B09 - Analytics & Dashboard | `modules/analytics/**` | | B05, B06 (đọc dữ liệu, không sửa) |
| B10 - Notification | `modules/notification/**` | | Đọc event từ các block khác, không block nào phụ thuộc ngược lại nó |
| B11 - Frontend Storefront | `apps/web/app/(storefront)/**` | | B02, B04, B05 |
| B12 - Frontend Admin | `apps/web/app/(admin)/**` | | Tất cả API block |
| B13 - Infra/DevOps/Observability | `infra/**`, `.github/**` | | Không block nào (chạy song song toàn bộ) |

**Luật đụng file chung**: file nằm ngoài mọi thư mục module (`shared/`, `common/`, migration gốc, `package.json` gốc) — sửa phải thông báo trong PR, khuyến khích để 1 agent cố định (coi như "integration lead") gom các thay đổi shared lại.

## 6. Checklist Definition of Done cho MỖI block (không phải chỉ cho toàn dự án)

Mỗi block, trước khi coi là xong, dù block nhỏ tới đâu:

- [ ] Entity/migration đã tạo, chạy migration thành công trên DB sạch
- [ ] `CONTRACT.md` của module đã viết đầy đủ (mục 4)
- [ ] Mọi endpoint có validate input + trả đúng envelope lỗi chuẩn (mục 2.2)
- [ ] Mọi permission-sensitive endpoint có check permission, có unit test cho cả 2 trường hợp (có quyền / không có quyền)
- [ ] Unit test cho logic tính toán quan trọng (giá, tồn kho, state transition) — không chỉ test "chạy không lỗi" mà test đúng con số
- [ ] Không có secret/credential hard-code trong code đã commit
- [ ] README ngắn trong thư mục module: mục đích module, cách chạy test riêng module đó

## 7. Đề xuất thay đổi quy ước (agent điền vào đây, không tự sửa phần trên)

| Ngày | Agent đề xuất | Đề xuất | Trạng thái |
|---|---|---|---|
| | | | |
