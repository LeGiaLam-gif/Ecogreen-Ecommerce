# Bàn giao trạng thái phiên làm việc — 18–19/11/2026

## Trạng thái

Commit `70fb475` đã ghi nhận các phần backend/frontend/Docker từ phiên trước (cookie auth, hồ sơ khách hàng, đánh giá sản phẩm, storefront analytics, tự hủy đơn `PENDING_PAYMENT` quá hạn, audit log, email đăng ký, Docker Compose và README).

Các thay đổi bổ sung trong phiên hiện tại **đang nằm trong working tree (chưa commit)**, không có thay đổi nào bị rollback hay xóa ngoài ý muốn.

## Đã hoàn thành và xác minh trong phiên này

- **Sửa unit test fail sau commit `70fb475`:**
  - Cập nhật `backend/src/test/java/com/example/backend/service/AnalyticsEventServiceTest.java` (đổi `"product_view"` sang `"unknown_event"` trong `track_unknownEventType_isIgnoredOrRejectedWithoutThrowing` vì `"product_view"` đã thuộc `AnalyticsEventTypes.ALL`).
- **Áp dụng migration và kiểm thử live trên Docker (`ecogreen-postgres`, `ecogreen-backend`, `ecogreen-frontend`):**
  - Đã chạy `V91__customer_profiles_product_reviews.sql` và `V92__audit_logs.sql` lên container `ecogreen-postgres`.
  - Đã rebuild `backend` và `frontend` trong Docker Compose và kiểm thử live qua `http://localhost:5173/api`:
    - Cookie Auth + CSRF: `GET /v1/auth/csrf`, `POST /v1/auth/login`, `GET /v1/auth/me`, `POST /v1/auth/refresh` trả `200`.
    - Hồ sơ khách hàng: `GET /v1/users/me` và `PUT /v1/users/me` cập nhật `phone` thành công.
    - Storefront Analytics: `POST /v1/analytics/events` (`product_view`) trả `204` và đọc lại được từ `GET /v1/admin/analytics/events`.
    - Đánh giá sản phẩm: `GET /v1/products/1/reviews` trả `200`.
    - Audit Log: `POST /v1/admin/discounts` ghi nhận bản ghi trong `GET /v1/admin/audit-logs`.
- **Hoàn thiện các màn hình UI còn thiếu:**
  - **Phân quyền vai trò người dùng (`user:update`):** cập nhật `frontend/src/pages/admin/tabs/UserDetailModal.jsx` và `UsersTab.jsx` để chỉnh vai trò qua `PATCH /api/v1/admin/users/{id}/roles` (khóa checkbox `ADMIN` trên UI nếu người thao tác không phải `ADMIN`).
  - **Tab Audit Logs (`audit:view`):** thêm `AUDIT_VIEW` vào `frontend/src/utils/permissions.js`, tạo `frontend/src/services/auditLogApi.js` và `frontend/src/pages/admin/tabs/AuditLogsTab.jsx`, gắn vào `AdminDashboard.jsx`.
  - **Tab Analytics Events (`analytics:view`):** bổ sung `listAnalyticsEvents` trong `frontend/src/services/analyticsApi.js`, tạo `frontend/src/pages/admin/tabs/AnalyticsEventsTab.jsx`, gắn vào `AdminDashboard.jsx`.
  - **Trang Thông báo của tôi (Khách hàng):** bổ sung `listMyNotifications` trong `frontend/src/services/notificationApi.js`, tạo `frontend/src/pages/MyNotifications.jsx`, đăng ký route `/account/notifications` trong `frontend/src/App.jsx` và thêm link trong `frontend/src/components/Navbar.jsx`.

## Kiểm thử và xác minh

- **Backend (`cd backend && .\mvnw.cmd test`):** `Tests run: 882, Failures: 0, Errors: 0, Skipped: 36` — `BUILD SUCCESS`. (36 test skipped là các integration test PostgreSQL gắn `@EnabledIfEnvironmentVariable` với `ECOGREEN_TEST_PG_URL` / `DB_PASSWORD`).
- **Frontend (`cd frontend && npm run lint && npm run build`):**
  - `npm run lint`: PASS (0 lỗi, 0 cảnh báo).
  - `npm run build`: PASS (`vite build` 3.11s; vẫn còn cảnh báo chunk JS > 500 kB của Vite).
- **Docker Compose (`docker compose up -d --build`):** `ecogreen-postgres` (healthy), `ecogreen-backend` (healthy), `ecogreen-frontend` (started, `http://localhost:5173`).

## Việc còn lại và giới hạn đã biết

- **Đã hoàn thành & xác minh thêm sau `a68cf85`:**
  - Đã kiểm thử live `POST /v1/products/{id}/reviews` trên đơn hàng `DELIVERED` (`201`, trùng lặp `409`) và xác nhận đơn `CANCELLED` từ chối thanh toán đến muộn (`400`, giữ nguyên `CANCELLED`).
  - Đã chuyển 5 endpoint legacy trong `AdminController` và `StatsController` sang `/api/v1/admin/reports/*` (`AdminReportV1Controller`, dùng `Permissions.ANALYTICS_VIEW` và `ApiResponse`), cập nhật `reportApi.js` + `OverviewTab.jsx` và xóa 2 controller legacy.
  - Đã bổ sung endpoint sử dụng `Permissions.INVENTORY_UPDATE` (`PUT /api/v1/admin/inventory/{productId}`) và `Permissions.SYSTEM_CONFIGURE` (`GET /api/v1/admin/system/config`).
- **Kiểm thử tích hợp chưa chạy trên Maven:**
  - 36 Postgres integration tests trong `backend/src/test` chưa được chạy với `ECOGREEN_TEST_PG_URL`.
- **Hạ tầng & tài liệu:**
  - Flyway chưa được kích hoạt (`ddl-auto=update` vẫn bật); migration vẫn áp dụng qua `docker/postgres-init/01-init.sh` (khi volume DB mới) hoặc chạy `psql` thủ công trên volume cũ.
  - Chưa có Spring Actuator (`/actuator/health`), CI/CD pipeline, và quy trình backup/restore tự động theo `docs/ai/EcoGreen_Project_Requirements.md`.
  - `README.md` chưa cập nhật thêm route `/account/notifications` và 2 tab mới (`audit-logs`, `analytics-events`) trong bảng mô tả giao diện.
- **Bảo mật / vận hành:** không commit `.env` thật, token hoặc credential lên git.
