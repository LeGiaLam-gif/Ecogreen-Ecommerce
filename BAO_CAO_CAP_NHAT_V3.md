# BÁO CÁO CẬP NHẬT — EcoGreen E-commerce (phiên làm việc theo MASTER PROMPT V3)

## 1. Tóm tắt

Phiên này thực hiện **một phần** của MASTER PROMPT V3 (Phase D mục 1–2, và
phần khởi tạo hạ tầng cho Phase H). **Phase C (CSS/responsive cho 8 trang),
phần còn lại của Phase D (validate field, test 403) và phần còn lại của
Phase H (thay `alert()`, dùng LoadingSpinner) CHƯA được thực hiện** — xem
mục 3 và file `MASTER_PROMPT_V4.md` đính kèm để tiếp tục.

Không có gì trong Phase A, Phase B, hay phần lõi state machine COD (đã xong
từ trước) bị sửa lại trong phiên này.

---

## 2. Các thay đổi đã thực hiện trong phiên này

### Backend

**`security/AuthTokenStore.java`** — thêm cơ chế hết hạn token
- Trước đây: token sống vĩnh viễn cho tới khi logout thủ công hoặc restart
  server (không có TTL).
- Sau khi sửa: mỗi token có `lastUsedAt`, hết hạn theo kiểu **sliding
  window 24 giờ không hoạt động** — mỗi lần token được dùng để gọi API
  thành công, đồng hồ hết hạn được làm mới; nếu token không được dùng liên
  tục trong 24 giờ, lần gọi tiếp theo sẽ bị coi là không hợp lệ (`resolveUserId`
  trả về `null`) và bị xoá khỏi bộ nhớ.
- `AuthInterceptor`, `AuthGuard`, `WebConfig` **không bị sửa** — toàn bộ logic
  hết hạn nằm gọn trong `AuthTokenStore`, không phá vỡ luồng xác thực hiện có.

### Frontend

**`services/http.js`** — tự động xử lý lỗi 401
- Phân biệt hai trường hợp bị `401`:
  - Sai username/password khi gọi `POST /auth/login` (request **không** có
    header `Authorization`) → chỉ hiển thị thông báo lỗi thông thường, không
    đăng xuất, không điều hướng.
  - Token hết hạn/không hợp lệ khi gọi một API cần đăng nhập (request **có**
    header `Authorization`) → tự động xoá `token`/`user` khỏi `localStorage`
    và điều hướng cứng (`window.location.href`) về `/login`.
- Đây là điều kiện bắt buộc phải phân biệt đúng, nếu không sẽ đá luôn người
  dùng ra khỏi trang đăng nhập mỗi khi họ gõ sai mật khẩu.

**`services/orderApi.js`** — thêm hàm `confirmCodPayment(id)` gọi
`POST /api/orders/{id}/confirm-cod-payment`. Hàm này **chưa được gọi ở đâu**
trong UI — `AdminDashboard.jsx` chưa có nút gọi nó (thuộc Phase C, chưa làm).

**Component dùng chung mới (Phase H) — đã tạo, `CHƯA được lắp vào ứng dụng`:**

| File | Mô tả | Đã dùng ở đâu chưa? |
|---|---|---|
| `components/LoadingSpinner.jsx` + `.css` | Spinner dùng chung, 2 biến thể `page` (toàn trang) và `inline` | Chưa — các trang vẫn dùng markup `<div className="pd-loading"><div className="spinner"></div></div>` cũ, dựa vào CSS bị "rò rỉ" toàn cục từ `ProductDetail.css` |
| `components/ErrorMessage.jsx` + `.css` | Banner lỗi/info dùng chung, hỗ trợ `tone="error"/"info"` và nút "Try again" | Chưa — các trang vẫn dùng `<p style={{color: '#c62828'}}>{error}</p>` |
| `components/Toast.jsx` + `.css` (`ToastProvider`, `useToast`) | Hệ thống thông báo dạng toast (góc phải trên, tự biến mất), thay thế `window.alert()` | Chưa — **chưa được wrap vào `App.jsx`**, và `alert()` trong `ProductCard.jsx` (1 chỗ) và `AdminDashboard.jsx` (5 chỗ) **vẫn còn nguyên**, chưa bị thay thế |

> **Lưu ý quan trọng:** 3 component trên hiện là code "chết" (chưa được
> import/dùng ở bất kỳ trang nào ngoài chính bản thân chúng). Chúng biên
> dịch được và không phá vỡ gì, nhưng **không tạo ra thay đổi nào có thể
> nhìn thấy trên giao diện** cho tới khi được lắp vào theo `MASTER_PROMPT_V4.md`.

---

## 3. Những gì trong MASTER PROMPT V3 vẫn CHƯA làm

Liệt kê đầy đủ, không che giấu, để tránh hiểu nhầm là "đã hoàn thành":

- **Phase C (toàn bộ)** — chưa có `Login.css`, `Register.css`, `Checkout.css`,
  `Payment.css`, `Orders.css`, `OrderDetail.css`, `OrderSuccess.css`,
  `AdminDashboard.css`. Tất cả 8 trang này **vẫn dùng `style={{...}}` inline**
  y như trước, chưa responsive, chưa có ô `deliveryNote` ở Checkout, chưa có
  badge trạng thái đơn/thanh toán ở Orders/OrderDetail, chưa có nút "Xác nhận
  đã thu tiền COD" ở AdminDashboard.
- **Checkout vẫn điều hướng tới `/payment/{id}` cho MỌI phương thức thanh
  toán, kể cả COD** — chưa sửa để COD đi thẳng tới `OrderSuccess` (đây là một
  yêu cầu rõ ràng trong V3 chưa được thực hiện).
- **Phase D mục 3** — Trang đăng ký chưa có validate lỗi theo từng field
  (username/email trùng, mật khẩu quá ngắn); vẫn hiển thị lỗi chung ở đầu form.
- **Phase D mục 4** — chưa xác nhận lại việc gọi API admin bằng token user
  thường trả về `403` sau các thay đổi ở AuthTokenStore (nhiều khả năng vẫn
  đúng vì không đụng tới `AuthGuard`, nhưng chưa test lại).
- **Phase H mục 1–2** — đã tạo component nhưng chưa lắp vào (xem bảng ở mục 2).
  `alert()` vẫn còn ở `ProductCard.jsx` và `AdminDashboard.jsx`.
- **`index.css` thiếu định nghĩa `.btn-primary`** — đây là một lỗ hổng có sẵn
  từ trước (không phải do phiên này gây ra): rất nhiều trang dùng
  `className="btn-primary"` nhưng class này chưa từng được định nghĩa ở bất
  kỳ file CSS nào trong dự án, nên nút chỉ hiển thị bằng style mặc định của
  trình duyệt. Cần định nghĩa khi làm Phase C.
- **Phase F (toàn bộ, vốn không bắt buộc)** — chưa làm: loading skeleton,
  "sản phẩm cùng danh mục", validate tồn kho tại UI giỏ hàng.
- **Mục 6 — `DATABASE.md`** chưa được cập nhật để mô tả state machine
  Order/Payment mới.
- **Mục 7 — bộ 12 test** chưa được chạy lại trong phiên này.

---

## 4. Dự án này có thể làm gì (tổng thể, tính đến thời điểm hiện tại)

### Khách hàng (chưa đăng nhập / đã đăng nhập)
- Xem trang chủ danh sách sản phẩm theo danh mục (`CategoryFilter`), banner
  slider, tìm kiếm theo tên sản phẩm (thanh search ở `Navbar`).
- Xem chi tiết sản phẩm: mô tả, giá, tồn kho, chọn số lượng, thêm vào giỏ
  hoặc mua ngay.
- Đăng ký / đăng nhập tài khoản (mật khẩu băm BCrypt, không bao giờ trả về
  trong API response).
- Giỏ hàng lưu **phía server** (bảng `carts`/`cart_items` theo user), không
  phải giỏ hàng giả trong `localStorage` — nghĩa là đăng nhập ở thiết bị khác
  vẫn thấy đúng giỏ hàng.
- Checkout: nhập tên người nhận / SĐT / địa chỉ giao hàng, chọn phương thức
  thanh toán COD hoặc thanh toán mô phỏng (`MOCK_PAYMENT`).
- Theo dõi đơn hàng của mình ("Đơn hàng của tôi" → chi tiết từng đơn, từng
  sản phẩm, giá tại thời điểm mua).
- Phiên đăng nhập giờ có **hết hạn tự động sau 24h không hoạt động** (mới
  thêm phiên này), và khi hết hạn, các API cần đăng nhập sẽ tự động đưa
  người dùng về trang đăng nhập thay vì hiển thị lỗi khó hiểu (mới thêm
  phiên này).

### State machine đơn hàng / thanh toán (đã hoàn thiện phần lõi backend từ
trước, giữ nguyên trong phiên này)
- **COD**: đặt hàng xong → `order.status = CONFIRMED` ngay (đơn được xác
  nhận xử lý), `payment.status = PENDING` (chưa thu tiền) cho tới khi **admin**
  bấm xác nhận đã thu tiền (API đã có: `POST /orders/{id}/confirm-cod-payment`,
  nút bấm ở UI admin thì **chưa có** — thuộc Phase C) → lúc đó
  `payment.status = SUCCESS`, `order.status = PAID`.
- **MOCK_PAYMENT**: đặt hàng xong → `order.status = PENDING`, khách vào
  trang `Payment` bấm "PAY NOW" (mô phỏng, không có cổng thanh toán thật)
  → `payment.status = SUCCESS`, `order.status = PAID`.
- Toàn bộ giá/tồn kho được kiểm tra lại từ database khi checkout, không tin
  dữ liệu frontend gửi lên; checkout là 1 transaction (thành hoặc bại toàn
  bộ, không tạo dữ liệu nửa vời).

### Quản trị viên (Admin CMS tại `/admin`)
- Quản lý sản phẩm: thêm/sửa/deactivate (soft delete, giữ nguyên đơn hàng
  cũ tham chiếu tới sản phẩm), tìm kiếm theo tên.
- Quản lý danh mục: thêm/sửa/xoá.
- Quản lý đơn hàng: xem tất cả đơn, đổi trạng thái đơn (`PENDING` /
  `CONFIRMED` / `PAID` / `CANCELLED`).
- Quản lý người dùng: xem danh sách, xoá tài khoản (trừ tài khoản ADMIN).
- Mọi thao tác ghi (create/update/delete) đều được `AuthGuard` xác thực lại
  vai trò **ở backend**, việc ẩn menu Admin ở frontend chỉ là UX, không phải
  ranh giới bảo mật.

### Bảo mật / hạ tầng (đã có từ Phase A, giữ nguyên)
- Không secret hard-code (đọc từ biến môi trường qua `.env`).
- Mật khẩu băm BCrypt, không bao giờ trả về trong response.
- Token phiên đăng nhập kiểu opaque token, xác minh trên mọi request qua
  `AuthInterceptor`, **nay có thêm hết hạn tự động** (điểm mới của phiên này).
- CORS giới hạn chỉ cho phép origin của Vite dev server.

### Đã biết là còn hạn chế (từ trước, chưa đổi)
- Token lưu **in-memory** ở backend — mất khi restart server, không chạy
  được nhiều instance cùng lúc (phù hợp cho đồ án, không phù hợp production
  thật).
- Thanh toán hoàn toàn là mô phỏng, không có cổng thanh toán thật.
- Ảnh sản phẩm là tên file tham chiếu / URL, chưa có upload ảnh từ Admin CMS.
- Giao diện **hầu hết vẫn chưa responsive thật sự và vẫn dùng style inline**
  ở 8 trang quan trọng (xem mục 3) — đây là phần việc lớn nhất còn lại.

---

## 5. File đính kèm

- `ecogreen-ecommerce-updated.zip` — toàn bộ mã nguồn ở trạng thái hiện tại
  (đã bao gồm các thay đổi ở mục 2), không kèm `node_modules`/`target`/`.git`.
- `MASTER_PROMPT_V4.md` — prompt tiếp theo, đã lược bỏ các mục đã hoàn thành
  ở phiên này, giữ nguyên toàn bộ phần còn lại từ V3 (không thêm yêu cầu mới).
