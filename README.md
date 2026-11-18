# EcoGreen E-Commerce

EcoGreen là ứng dụng thương mại điện tử bán các sản phẩm thân thiện với môi trường. Khách hàng có thể duyệt sản phẩm, quản lý giỏ hàng, đặt hàng, thanh toán và theo dõi đơn; nhân viên quản lý danh mục, kho, đơn hàng, đổi trả và khách hàng trong khu vực quản trị.

Ứng dụng gồm giao diện React, REST API Spring Boot và cơ sở dữ liệu PostgreSQL. Giao diện hiện hỗ trợ tiếng Việt.

## 1. Tính năng hiện có

### Khách hàng

- Duyệt, tìm kiếm và lọc sản phẩm theo danh mục; xem chi tiết và hình ảnh sản phẩm.
- Đăng ký, đăng nhập bằng tài khoản/mật khẩu; hỗ trợ đăng nhập Google hoặc Facebook khi được cấu hình.
- Giỏ hàng gắn với tài khoản, kiểm tra tồn kho khi cập nhật số lượng.
- Sổ địa chỉ giao hàng; xem trước tổng tiền, mã giảm giá, phí vận chuyển trước khi đặt hàng.
- Quản lý hồ sơ cá nhân; gửi đánh giá 1–5 sao cho sản phẩm trong đơn đã giao.
- Theo dõi đơn hàng và lịch sử trạng thái; hủy đơn chưa thanh toán khi đáp ứng điều kiện.
- Gửi yêu cầu đổi/trả cho đơn đã giao, chọn sản phẩm/số lượng và đính kèm ảnh.
- Khôi phục mật khẩu qua email khi SMTP được cấu hình.
- Trang FAQ, liên hệ, điều khoản, bảo mật và chính sách đổi trả.

### Quản trị

- Tổng quan về đơn chờ thanh toán, đổi/trả, tồn kho thấp, hoàn tiền và khách hàng.
- Quản lý sản phẩm, ảnh, trạng thái đăng bán và danh mục phân cấp.
- Theo dõi tồn kho, lịch sử giao dịch kho và điều chỉnh tồn có ghi lý do.
- Tra cứu đơn, cập nhật trạng thái; xác nhận thanh toán và hoàn tiền theo quyền.
- Xét duyệt yêu cầu đổi/trả, ghi chú, tiếp nhận hàng và khởi tạo hoàn tiền.
- Tìm kiếm khách hàng, xem lịch sử đơn và quản lý trạng thái tài khoản theo quyền.
- Quản lý mã giảm giá và xem nhật ký gửi thông báo.
- Xem nhật ký thao tác quản trị và thay đổi trạng thái đơn theo quyền `audit:view`.

### Giới hạn cần biết

- VNPay dùng cấu hình sandbox nếu được cung cấp thông tin kết nối. VietQR và MoMo hiện chỉ hiển thị QR tĩnh, không có tích hợp xác nhận tự động. Thanh toán giả lập chỉ dành cho phát triển; tắt `PAYMENT_MOCK_ENABLED` ngoài môi trường local.
- Hoàn tiền được nhân viên ghi nhận/xử lý trong ứng dụng; chưa gọi API hoàn tiền của cổng thanh toán.
- Biểu đồ doanh thu dùng API quản trị cũ, chỉ dành cho `ADMIN`. Các báo cáo tổng quan khác được kiểm tra theo permission tương ứng.
- Email thông báo cho tài khoản, đơn hàng, thanh toán và đổi trả chỉ gửi khi SMTP được cấu hình. Chat hỗ trợ hiện trả lời theo kịch bản, không kết nối tổng đài.
- Đây là ứng dụng học tập/portfolio; cần cấu hình và rà soát bảo mật trước khi triển khai công khai.

## 2. Thiết lập và chạy lần đầu

### Yêu cầu

- Docker Desktop hoặc Docker Engine có Docker Compose (cách chạy khuyến nghị).
- Nếu chạy từng dịch vụ riêng: JDK 21, Node.js 20.19+ hoặc 22.12+, npm, PostgreSQL 16 và `psql`.

### Cách A — Chạy toàn bộ bằng Docker Compose

Từ thư mục gốc repository, tạo file cấu hình riêng và sửa các giá trị bắt buộc:

```powershell
Copy-Item .env.example .env
```

Trong `.env`, đặt `DB_PASSWORD` và `JWT_SECRET` (khóa JWT tối thiểu 32 bytes). Có thể tạo khóa bằng `openssl rand -base64 48`. Không commit `.env` hoặc chia sẻ các khóa/mật khẩu thật.

Khởi chạy:

```powershell
docker compose up -d --build
docker compose ps
```

- Cửa hàng: <http://localhost:5173>
- API: <http://localhost:8081>
- PostgreSQL: `localhost:5432`

Compose tự khởi tạo schema, dữ liệu demo (mặc định `SEED_DEMO_DATA=true`) và các migration trên **volume PostgreSQL mới, rỗng**. Các lần chạy sau giữ nguyên dữ liệu; thay đổi script khởi tạo không tự áp dụng lại vào volume đã tồn tại.

Tài khoản demo mặc định trên dữ liệu seed: `admin / admin123` và `khachhang / 123456`. Chỉ dùng ở máy local; không đưa thông tin này lên môi trường công khai. Để không nạp dữ liệu demo, đặt `SEED_DEMO_DATA=false` trong `.env` trước lần khởi tạo database đầu tiên.

Một số lệnh hữu ích:

```powershell
docker compose logs -f backend
docker compose down
```

`docker compose down` không xóa dữ liệu trong named volume. Không dùng `docker compose down -v` nếu muốn giữ database.

### Cách B — Chạy frontend/backend riêng

1. Cài và khởi động PostgreSQL, sau đó tạo database `ecogreen`, chạy schema, dữ liệu mẫu nếu cần và toàn bộ migration theo hướng dẫn ở mục [3. Database](#3-database).
2. Mở một PowerShell ở thư mục gốc và khai báo cấu hình cho backend. Thay password theo database local; `JWT_SECRET` phải dài ít nhất 32 bytes:

   ```powershell
   $env:DB_HOST = "localhost"
   $env:DB_PORT = "5432"
   $env:DB_NAME = "ecogreen"
   $env:DB_USERNAME = "postgres"
   $env:DB_PASSWORD = "mat_khau_postgres"
   $env:JWT_SECRET = "thay_bang_khoa_ngau_nhien_toi_thieu_32_bytes"
   ```

3. Chạy backend:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

   Backend lắng nghe tại `http://localhost:8081`.

4. Mở PowerShell khác tại thư mục gốc, cài dependencies và chạy frontend:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

   Mở `http://localhost:5173`. Vite proxy các request `/api` tới backend tại cổng `8081`.

Spring Boot không tự đọc file `.env`; khi chạy riêng, hãy đặt biến môi trường trong PowerShell hoặc cấu hình chạy của IDE. Các biến OAuth, VNPay và SMTP là tùy chọn.

### Kiểm tra nhanh

```powershell
Invoke-WebRequest http://localhost:8081/api/v1/products
```

Lệnh kiểm tra có thể trả về danh sách rỗng nếu chưa có sản phẩm. Kiểm tra backend:

```powershell
cd backend
.\mvnw.cmd test
```

Kiểm tra frontend:

```powershell
cd frontend
npm run lint
npm run build
```

## 3. Database

### Công nghệ và dữ liệu

- PostgreSQL 16, database mặc định `ecogreen`.
- Các bảng bao gồm người dùng/vai trò/quyền; danh mục/sản phẩm/ảnh/đánh giá; giỏ hàng; đơn hàng/lịch sử; tồn kho/sổ giao dịch; thanh toán/hoàn tiền; địa chỉ; đổi trả; mã giảm giá; sự kiện analytics, thông báo và audit log.
- Hình ảnh tải lên được lưu thành file trong `UPLOAD_DIR` (mặc định `./uploads`), không lưu binary trong PostgreSQL.
- Cấu hình kết nối: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`. Có thể thay bằng `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

### Khởi tạo thủ công database mới

`database/init-postgres.sql` tạo schema gốc. Các thay đổi tiếp theo nằm trong `backend/src/main/resources/db/migration/`. Dự án **không bật Flyway**; khi chạy ngoài Docker, phải áp dụng SQL theo thứ tự phiên bản trước khi chạy backend lần đầu.

Ví dụ PowerShell (yêu cầu PostgreSQL client nằm trong `PATH`):

```powershell
createdb -U postgres ecogreen
psql -v ON_ERROR_STOP=1 -U postgres -d ecogreen -f database/init-postgres.sql

# Tùy chọn: nạp sản phẩm/tài khoản demo. Chỉ chạy trên database phát triển.
psql -v ON_ERROR_STOP=1 -U postgres -d ecogreen -f database/seed-data.sql

$migrationDir = "backend\src\main\resources\db\migration"
Get-ChildItem "$migrationDir\V*.sql" |
  Sort-Object { [int]($_.Name -replace '^V(\d+)_.*$', '$1') } |
  ForEach-Object {
    psql -v ON_ERROR_STOP=1 -U postgres -d ecogreen -f $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "Migration thất bại: $($_.Name)" }
  }
```

Nếu PostgreSQL yêu cầu mật khẩu, đặt `$env:PGPASSWORD` cho phiên PowerShell trước khi chạy `psql`, rồi xóa biến sau khi hoàn tất. Với database đã có dữ liệu, sao lưu trước khi áp dụng migration; một số migration thay đổi cấu trúc/trạng thái đơn hàng. Đặc biệt, chạy `seed-data.sql` sẽ xóa dữ liệu đơn hàng, thanh toán, sản phẩm và danh mục hiện có để nạp lại dữ liệu mẫu.

Danh sách migration hiện tại gồm các phiên bản `V2`–`V8`, `V10`–`V12`, `V20`–`V22`, `V30`–`V31`, `V40`–`V41`, `V50`–`V51`, `V60`, `V70`, `V80`, `V90`, `V91` và `V92`. Nên để script Compose áp dụng trên database rỗng hoặc dùng lệnh PowerShell phía trên để sắp xếp theo số phiên bản.

`spring.jpa.hibernate.ddl-auto=update` giúp Hibernate cập nhật một phần cấu trúc entity trong quá trình phát triển; thiết lập này **không thay thế** các migration SQL chứa dữ liệu khởi tạo quyền và các thay đổi nghiệp vụ.

### Database Docker đã được khởi tạo trước đó

Các script trong `docker/postgres-init/` chỉ chạy tự động khi PostgreSQL tạo data directory lần đầu. Nếu cần áp dụng migration mới cho volume đang tồn tại, hãy sao lưu và chạy migration đó thủ công bằng `psql`; không xóa volume để ép khởi tạo lại nếu cần giữ dữ liệu.

## 4. Luồng nghiệp vụ

1. **Duyệt và chọn sản phẩm:** khách tìm kiếm, lọc theo danh mục/giá/tình trạng tồn, sắp xếp và xem sản phẩm; các lượt xem, tìm kiếm và thao tác giỏ được ghi nhận thành analytics event. Backend kiểm tra trạng thái sản phẩm và số lượng tồn khi thao tác giỏ.
2. **Đăng nhập và chuẩn bị đơn:** khách đăng nhập, chọn hoặc nhập địa chỉ giao hàng, áp dụng mã giảm giá nếu hợp lệ. Trang checkout yêu cầu đăng nhập.
3. **Xem trước và đặt hàng:** backend tính lại giá, giảm giá, phí giao hàng và tổng tiền; không tin tổng tiền do trình duyệt gửi lên. Khi xác nhận, backend kiểm tra tồn trong transaction, tạo đơn với bản chụp giá/thông tin giao hàng, giữ chỗ tồn kho, ghi lịch sử trạng thái và tạo giao dịch thanh toán.
4. **Thanh toán:** COD không cần chuyển cổng. VNPay chuyển khách tới cổng và chỉ xác nhận thanh toán khi callback phía server được xác minh. VietQR/MoMo hiện là QR tĩnh, nhân viên xác nhận thủ công. Mock payment chỉ dùng trong phát triển.
5. **Hết hạn và xử lý đơn:** đơn chưa thanh toán quá 3 ngày được tự hủy; tồn giữ chỗ được giải phóng và các mặt hàng được cộng lại vào giỏ của khách. Thanh toán đến muộn không thể chuyển đơn đã hủy sang đã thanh toán.
6. **Xử lý và giao đơn:** nhân viên có quyền cập nhật đơn qua các trạng thái hợp lệ; backend kiểm soát chuyển trạng thái, điều chỉnh tồn kho và ghi nhật ký thay đổi. Khách theo dõi tiến trình tại lịch sử/chi tiết đơn.
7. **Đổi trả và hoàn tiền:** khách gửi yêu cầu đổi/trả cho đơn đã giao, chọn mặt hàng và cung cấp lý do/ảnh. Nhân viên yêu cầu bổ sung thông tin, chấp nhận hoặc từ chối, xác nhận hàng nhận lại và ghi nhận hoàn tiền. Hoàn tiền cổng thanh toán không tự động.
8. **Thông báo:** sự kiện tài khoản, đơn hàng, thanh toán, giao hàng và đổi trả có thể tạo email bất đồng bộ khi SMTP được cấu hình; kết quả gửi được lưu trong nhật ký.

## 5. Màn hình, route và phân quyền

### Route cửa hàng

| Route | Màn hình | Truy cập |
|---|---|---|
| `/` | Trang chủ / danh sách sản phẩm | Công khai |
| `/product/:id` | Chi tiết sản phẩm (hỗ trợ id hoặc slug) | Công khai |
| `/login` | Đăng nhập | Công khai |
| `/register` | Đăng ký | Công khai |
| `/forgot-password` | Yêu cầu đặt lại mật khẩu | Công khai |
| `/reset-password` | Đặt mật khẩu mới | Công khai, cần token hợp lệ |
| `/cart` | Giỏ hàng | Route công khai; thao tác giỏ gắn với tài khoản |
| `/checkout` | Xác nhận thông tin và đặt hàng | Đăng nhập |
| `/payment/:orderId` | Thanh toán đơn hàng | Đăng nhập; chỉ chủ đơn hoặc nhân viên có quyền phù hợp |
| `/payment/return` | Nhận kết quả chuyển hướng từ cổng thanh toán | Đăng nhập |
| `/order-success/:orderId` | Kết quả đặt hàng | Đăng nhập |
| `/orders` | Lịch sử đơn hàng | Đăng nhập, dữ liệu của chính khách |
| `/orders/:id` | Chi tiết đơn hàng / tiến trình / yêu cầu đổi trả | Đăng nhập, kiểm tra quyền sở hữu |
| `/account/addresses` | Sổ địa chỉ | Đăng nhập |
| `/account/profile` | Hồ sơ cá nhân | Đăng nhập |
| `/faq` | Câu hỏi thường gặp | Công khai |
| `/contact` | Liên hệ | Công khai |
| `/policy/returns` | Chính sách đổi trả | Công khai |
| `/terms` | Điều khoản sử dụng | Công khai |
| `/privacy` | Chính sách quyền riêng tư | Công khai |

### Khu vực quản trị

Route quản trị là `/admin/*`. Đây là một giao diện gồm các tab chuyển đổi trong trang, **không phải các URL riêng cho từng tab**.

| Tab quản trị | Quyền để hiển thị tab | Quyền chính cho thao tác |
|---|---|---|
| Tổng quan | Tài khoản nhân viên (`ADMIN` hoặc `MANAGER`) | Các thẻ/báo cáo được lọc theo quyền. Biểu đồ doanh thu legacy chỉ `ADMIN`. |
| Sản phẩm | `product:view` | Tạo `product:create`; sửa/ảnh `product:update`; xóa `product:delete`; đăng bán `product:publish`. |
| Danh mục | Bất kỳ quyền `category:view/create/update/delete` | Quyền tương ứng với xem, tạo, sửa và xóa. |
| Kho hàng | `inventory:view` | Điều chỉnh tồn cần `inventory:adjust`. |
| Đơn hàng | `order:view_all` | Cập nhật trạng thái `order:update` (hủy theo `order:cancel`); xem/xác nhận thanh toán và hoàn tiền cần quyền `payment:*` tương ứng. |
| Đổi / Trả hàng | `return:view` | Xử lý yêu cầu cần `return:process`. |
| Khách hàng | `user:view` | Cập nhật vai trò `user:update`; khóa/mở khóa/xóa `user:disable`. Một số thao tác gán/gỡ ADMIN chỉ ADMIN được phép. |
| Mã giảm giá | `discount:view` | Tạo/sửa/xóa `discount:manage`. |
| Thông báo | `analytics:view` | Xem nhật ký thông báo theo API quản trị. |
| API nhật ký kiểm toán (`/api/v1/admin/audit-logs`) | `audit:view` | Xem thao tác quản trị và thay đổi trạng thái đơn; hiện chưa có tab giao diện riêng. |

### Vai trò

- **CUSTOMER:** thao tác trên tài khoản và dữ liệu của chính mình; không có quyền quản trị theo permission.
- **MANAGER:** có 26 permission quản trị, bao gồm các chức năng vận hành; không có `system:configure`, `audit:view`, `user:disable`. Không thể cấp/gỡ vai trò `ADMIN`.
- **ADMIN:** có toàn bộ 29 permission và được truy cập các báo cáo doanh thu legacy chỉ dành cho ADMIN.

Việc ẩn tab/nút ở frontend chỉ phục vụ giao diện. Backend xác thực token, quyền trên từng API và quyền sở hữu đơn hàng; không dựa vào việc frontend đã ẩn route hay nút.

## 6. Cấu trúc thư mục

```text
.
├── backend/
│   ├── src/main/java/com/example/backend/
│   │   ├── api/             # Định dạng phản hồi API
│   │   ├── config/          # Cấu hình Spring
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Request/response DTO
│   │   ├── entity/          # JPA entities
│   │   ├── event/           # Sự kiện nghiệp vụ
│   │   ├── exception/       # Xử lý lỗi
│   │   ├── repository/      # Truy cập dữ liệu
│   │   ├── security/        # JWT, xác thực, permission
│   │   ├── service/         # Nghiệp vụ
│   │   └── storage/         # Lưu và kiểm tra file tải lên
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/    # SQL migration áp dụng thủ công / qua init Docker
│   ├── src/test/            # Kiểm thử backend
│   ├── Dockerfile
│   └── mvnw.cmd             # Maven wrapper cho Windows
├── frontend/
│   ├── src/
│   │   ├── components/      # Thành phần giao diện dùng chung
│   │   ├── context/         # AuthContext, CartContext
│   │   ├── hooks/           # React hooks
│   │   ├── pages/           # Các màn hình cửa hàng
│   │   │   └── admin/
│   │   │       └── tabs/    # Các tab quản trị
│   │   ├── services/        # Gọi API
│   │   └── utils/           # Hằng số và hàm hỗ trợ
│   ├── public/              # Tài nguyên tĩnh
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── database/
│   ├── init-postgres.sql    # Schema ban đầu
│   └── seed-data.sql        # Dữ liệu demo (có thao tác xóa dữ liệu cũ)
├── docker/
│   └── postgres-init/       # Script khởi tạo DB lần đầu của Docker
├── docs/
│   └── ai/
│       ├── EcoGreen_Project_Requirements.md
│       └── EcoGreen_Project_Requirements.docx  # Bản gốc
├── docker-compose.yml       # Khởi chạy PostgreSQL, backend và frontend
└── .env.example             # Mẫu biến môi trường; tạo .env riêng để chạy
```

**Tài liệu requirements:** [`docs/ai/EcoGreen_Project_Requirements.md`](docs/ai/EcoGreen_Project_Requirements.md) (bản Markdown); bản Word gốc vẫn được giữ tại [`docs/ai/EcoGreen_Project_Requirements.docx`](docs/ai/EcoGreen_Project_Requirements.docx).
