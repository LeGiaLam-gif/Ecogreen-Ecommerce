# EcoGreen — 13 Prompt chi tiết theo khối công việc (Hướng B: Spring Boot + React, mở rộng)

> Đọc `00_GAP_ANALYSIS.md`, `01_CONVENTIONS.md`, `02_DEVELOPMENT_PLAN.md` trước. Mỗi mục bên dưới là **1 prompt hoàn chỉnh**, copy nguyên khối (từ `## PROMPT B0X` tới hết mục) dán cho 1 agent/account riêng. Mỗi prompt đã tự chứa đủ ngữ cảnh cần thiết — agent không cần đọc code người khác đang viết song song.

## Quy ước version migration (BẮT BUỘC — tránh đụng số file giữa các agent)

Dự án chuyển từ chạy thẳng `init-postgres.sql` sang **Flyway** (`backend/src/main/resources/db/migration/V<n>__<ten>.sql`). Mỗi block được cấp sẵn 1 dải số, **không được dùng số ngoài dải của mình**:

| Block | Dải version Flyway |
|---|---|
| Baseline (convert `init-postgres.sql` hiện có thành `V1__baseline.sql`) | V1 |
| B01 Auth/RBAC | V2 – V9 |
| B02 Catalog | V10 – V19 |
| B03 Inventory | V20 – V29 |
| B04 Cart/Checkout | V30 – V39 |
| B05 Order | V40 – V49 |
| B06 Payment | V50 – V59 |
| B07 Return/Refund | V60 – V69 |
| B08 Customer | V70 – V79 |
| B09 Analytics | V80 – V89 |
| B10 Notification | V90 – V99 |
| Dự phòng | V100+ |

---

## PROMPT B01 — Authentication & RBAC

**Bối cảnh**: Bạn đang làm việc trên backend Spring Boot package `com.example.backend`. Hệ thống hiện có cơ chế tự viết: `security/AuthTokenStore.java` (token UUID lưu `ConcurrentHashMap` trong RAM), `security/AuthInterceptor.java`, `security/AuthGuard.java` (có `requireUser()`/`requireAdmin()`), entity `Role`/`User` (2 role cứng USER/ADMIN), và `AuthService.loginWithGoogle()` **không verify chữ ký token với Google** (lỗ hổng bảo mật thật). Nhiệm vụ của bạn là nâng cấp toàn bộ tầng này.

**KHÔNG được sửa**: bất kỳ file trong `controller/ProductController.java, CartController.java, OrderController.java...` — các controller khác sẽ tự cập nhật để gọi `AuthGuard.requirePermission()` mới của bạn ở các block sau, bạn chỉ cung cấp hạ tầng.

### Việc cần làm

1. **Thêm dependency** `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (phiên bản 0.12.x) vào `pom.xml`, và `com.google.api-client:google-api-client` để verify Google ID token.

2. **Migration** `V2__add_permissions.sql`:
```sql
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,   -- vd 'product:create'
    description VARCHAR(255)
);
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
```
`V3__rename_roles_add_manager.sql`: đổi tên role `USER` thành `CUSTOMER` (UPDATE, không xoá/tạo lại để giữ nguyên `id` đang được tham chiếu), thêm role mới `MANAGER`.
`V4__seed_permissions.sql`: insert đủ danh sách permission ở `01_CONVENTIONS.md` mục 2.3; gán **toàn bộ** permission cho `ADMIN`; gán cho `MANAGER` mọi permission **trừ** `system:configure` và `audit:view`.
`V5__refresh_tokens.sql`:
```sql
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

3. **Entity mới**: `Permission.java` (id, code, description), sửa `Role.java` thêm `@ManyToMany Set<Permission> permissions`, thêm `RefreshToken.java`.

4. **`JwtService.java`** (class mới trong `security/`):
   - `String generateAccessToken(User user)` — claims: `sub`=userId (String), `permissions`=List&lt;String&gt; (từ `user.getRoles()` gộp permission code, loại trùng), `exp` = now + 15 phút. Ký bằng `HS256`, secret đọc từ biến môi trường `JWT_SECRET` (tối thiểu 32 ký tự).
   - `Jws<Claims> parseAndValidate(String token)` — ném `UnauthorizedException` nếu hết hạn/sai chữ ký.
   - `String generateRefreshToken()` — chuỗi ngẫu nhiên 64 byte base64, **không phải JWT** (chỉ là opaque token, lưu bản băm SHA-256 vào cột `token_hash`, KHÔNG lưu plaintext).

5. **Sửa `AuthInterceptor.java`**: thay vì tra `AuthTokenStore`, parse Bearer token bằng `JwtService.parseAndValidate()`, đọc `permissions` từ claims, gắn vào `CurrentUser` (sửa `CurrentUser.java` thêm field `Set<String> permissions`).

6. **Sửa `AuthGuard.java`**, thêm 2 method mới (giữ nguyên `requireUser()`/`requireAdmin()` cho code cũ chưa migrate kịp, đánh dấu `@Deprecated`):
```java
public CurrentUser requirePermission(HttpServletRequest request, String permissionCode) {
    CurrentUser user = requireUser(request);
    if (!user.getPermissions().contains(permissionCode)) {
        throw new ForbiddenException("Missing permission: " + permissionCode);
    }
    return user;
}
public CurrentUser requireAnyPermission(HttpServletRequest request, String... codes) { ... }
```

7. **Sửa `AuthService.java`**:
   - `login()`: sau khi xác thực mật khẩu đúng, gọi `jwtService.generateAccessToken()` + tạo `refreshToken` lưu DB, trả cả 2 trong `AuthResponse` (thêm field `refreshToken`, `expiresIn`).
   - **`loginWithGoogle()` — SỬA LỖ HỔNG**: không tự parse base64 nữa. Dùng `GoogleIdTokenVerifier` (từ `google-api-client`) với `audience` = `GOOGLE_CLIENT_ID` (biến môi trường), gọi `verifier.verify(idTokenString)`; nếu `null` → ném `UnauthorizedException("Invalid Google token")`. Chỉ lấy email/name/sub **từ payload đã verify**, không nhận các field này nếu client tự gửi rời rạc trong request body nữa — sửa `GoogleAuthRequest.java` chỉ còn 1 field `idToken`.
   - Thêm `refreshToken(String rawRefreshToken): AuthResponse` — tìm theo `token_hash` (băm input rồi so sánh), kiểm tra `!revoked && expires_at > now()`, issue access token mới (**không** issue refresh token mới — giữ nguyên để đơn giản ở bản đầu).
   - Thêm `logout(String rawRefreshToken): void` — set `revoked = true`.

8. **Endpoint** (`AuthController.java`):

| Method | Path | Request body | Response | Permission |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | `{username, email, password}` | `{data: {accessToken, refreshToken, expiresIn, user: {id,username,email,roles,permissions}}}` | Public |
| POST | `/api/v1/auth/login` | `{username, password}` | như trên | Public |
| POST | `/api/v1/auth/google` | `{idToken}` | như trên | Public |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` | `{data: {accessToken, expiresIn}}` | Public |
| POST | `/api/v1/auth/logout` | `{refreshToken}` | `{data: null}` | requireUser |
| GET | `/api/v1/auth/me` | — | `{data: {id,username,email,roles,permissions}}` | requireUser |

### Test bắt buộc (JUnit5 + Mockito, đặt trong `backend/src/test/java/.../service/AuthServiceTest.java`)
- `login_withCorrectPassword_returnsValidJwt()`
- `login_withWrongPassword_throwsUnauthorized()`
- `loginWithGoogle_withForgedToken_throwsUnauthorized()` — mock `GoogleIdTokenVerifier.verify()` trả `null`
- `refreshToken_withRevokedToken_throwsUnauthorized()`
- `jwtService_parseExpiredToken_throwsUnauthorized()`
- `authGuard_requirePermission_withoutPermission_throwsForbidden()`

### Definition of Done riêng block này
- [ ] Migration V2–V9 chạy sạch trên DB rỗng lẫn DB đã có dữ liệu cũ (role USER đổi tên thành CUSTOMER không mất liên kết `user_roles`)
- [ ] `/api/v1/auth/google` không còn nhận field email/name/googleId rời rạc từ client
- [ ] `JWT_SECRET`, `GOOGLE_CLIENT_ID` có trong `.env.example`
- [ ] Toàn bộ 6 test trên pass
- [ ] Viết `modules/auth/CONTRACT.md` công bố chữ ký `AuthGuard.requirePermission()` để B02–B10 gọi

---

## PROMPT B02 — Catalog (Product + Category + Image)

**Bối cảnh**: `entity/Product.java` hiện chỉ có `name, description, price, stockQuantity, image(1 ảnh dạng string), status(ACTIVE/INACTIVE), category`. `entity/Category.java` phẳng, không cây cha/con. `GET /api/products` không có tham số nào (không search/filter/pagination). Bạn mở rộng domain này.

**Phụ thuộc**: cần `AuthGuard.requirePermission()` từ B01 đã xong (dùng permission `product:create`, `product:update`, `product:delete`, `product:publish`, `category:create`, `category:update`, `category:delete`).

### Migration `V10__extend_products.sql`
```sql
ALTER TABLE products
    ADD COLUMN slug VARCHAR(160) UNIQUE,
    ADD COLUMN compare_price DECIMAL(12,2),
    ADD COLUMN sku VARCHAR(64) UNIQUE,
    ADD COLUMN brand VARCHAR(100);
ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_status;
ALTER TABLE products ADD CONSTRAINT chk_products_status
    CHECK (status IN ('DRAFT','ACTIVE','OUT_OF_STOCK','INACTIVE','ARCHIVED'));
-- Dữ liệu cũ: mọi sản phẩm ACTIVE/INACTIVE hiện có giữ nguyên giá trị (2 giá trị này vẫn hợp lệ trong tập mới)
UPDATE products SET slug = lower(regexp_replace(name, '[^a-zA-Z0-9]+', '-', 'g')) || '-' || id WHERE slug IS NULL;
ALTER TABLE products ALTER COLUMN slug SET NOT NULL;
```
`V11__product_images.sql`:
```sql
CREATE TABLE product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    storage_key VARCHAR(255),
    alt_text VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_product_images_product_id ON product_images(product_id);
-- Di chuyển dữ liệu ảnh cũ (cột products.image) sang bảng mới, sort_order = 0
INSERT INTO product_images (product_id, image_url, sort_order)
    SELECT id, image, 0 FROM products WHERE image IS NOT NULL;
```
`V12__category_tree.sql`:
```sql
ALTER TABLE categories
    ADD COLUMN parent_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    ADD COLUMN slug VARCHAR(120) UNIQUE;
UPDATE categories SET slug = lower(regexp_replace(name, '[^a-zA-Z0-9]+', '-', 'g')) || '-' || id WHERE slug IS NULL;
ALTER TABLE categories ALTER COLUMN slug SET NOT NULL;
```

### Entity & Service
- `Product.java`: thêm field `slug, comparePrice, sku, brand`. Giữ `image` (đánh dấu `@Deprecated`, không xoá cột để tránh vỡ dữ liệu cũ) + thêm `@OneToMany List<ProductImage> images`.
- `ProductImage.java` entity mới.
- `Category.java`: thêm `slug`, `@ManyToOne Category parent`, `@OneToMany List<Category> children`.

**Chữ ký hàm bắt buộc** (service layer, các block khác sẽ gọi):
```java
// ProductService
Page<Product> search(ProductSearchCriteria criteria, Pageable pageable);
Product getBySlugOrId(String slugOrId);          // dùng cho trang chi tiết public
Product publish(Long productId);                 // throws BusinessRuleViolationException nếu thiếu field bắt buộc (name, price, sku, category, >=1 image)
Product create(ProductCreateRequest req);         // status mặc định DRAFT
Product update(Long id, ProductUpdateRequest req);
void archive(Long id);                            // soft-delete: status = ARCHIVED (giữ nguyên hành vi cũ, không đổi tên hàm cũ "delete" gây nhầm)

// CategoryService
List<CategoryNode> getTree();                     // CategoryNode: {id, name, slug, children: List<CategoryNode>}
```

`ProductSearchCriteria`: `{ String keyword; Long categoryId; BigDecimal minPrice; BigDecimal maxPrice; String status; }` — `keyword` tìm trên `name` VÀ `description` (dùng `ILIKE '%keyword%'` cho bản đầu, có thể nâng cấp full-text sau, không chặn tiến độ vì việc này).

### API

| Method | Path | Query/Body | Permission |
|---|---|---|---|
| GET | `/api/v1/products` | `?keyword=&categoryId=&minPrice=&maxPrice=&page=&size=&sort=` | Public (chỉ trả `status=ACTIVE`) |
| GET | `/api/v1/products/{slugOrId}` | — | Public |
| GET | `/api/v1/admin/products` | `?status=&page=&size=` (thấy mọi status) | `product:view` |
| POST | `/api/v1/admin/products` | `ProductCreateRequest` | `product:create` |
| PATCH | `/api/v1/admin/products/{id}` | `ProductUpdateRequest` | `product:update` |
| POST | `/api/v1/admin/products/{id}/publish` | — | `product:publish` |
| DELETE | `/api/v1/admin/products/{id}` | — (soft-delete → ARCHIVED) | `product:delete` |
| POST | `/api/v1/admin/products/{id}/images` | multipart file + `altText, sortOrder` | `product:update` |
| DELETE | `/api/v1/admin/products/{id}/images/{imageId}` | — | `product:update` |
| GET | `/api/v1/categories` | `?tree=true` trả cây, mặc định trả phẳng | Public |
| POST/PATCH/DELETE | `/api/v1/admin/categories...` | | `category:create/update/delete` |

**Upload ảnh**: nếu `03_INFRA` (B13) đã cấu hình object storage client (S3/R2) thì dùng; nếu CHƯA sẵn sàng lúc bạn làm block này, tạm thời lưu file vào thư mục local `uploads/` phục vụ serve tĩnh qua Spring (`WebMvcConfigurer.addResourceHandlers`), **nhưng** code upload phải nằm sau 1 interface `ObjectStorageClient { String upload(MultipartFile file); void delete(String key); }` để B13 thay implementation sau mà không sửa `ProductService`.

### Test bắt buộc
- `productService_publish_missingRequiredField_throwsBusinessRuleViolation()`
- `productService_search_filterByPriceRange_returnsCorrectSubset()`
- `categoryService_getTree_buildsCorrectParentChildStructure()`
- Test API: public endpoint không bao giờ trả sản phẩm `DRAFT`/`ARCHIVED`

### Definition of Done
- [ ] Migration chạy sạch, dữ liệu ảnh cũ đã migrate sang `product_images`
- [ ] `GET /api/v1/products` hỗ trợ đủ query param, trả đúng envelope phân trang (`01_CONVENTIONS.md` mục 2.2)
- [ ] `modules/catalog/CONTRACT.md` công bố `ProductSearchCriteria`, enum status, chữ ký `ProductService.*` cho B03/B04 dùng

---

## PROMPT B03 — Inventory (Reservation model)

**Bối cảnh**: Hiện tại tồn kho chỉ là 1 cột `products.stockQuantity`, trừ thẳng trong `OrderService.checkout()` — không có khái niệm "giữ chỗ" (reservation), không chống được race condition khi 2 người mua cùng lúc sản phẩm sắp hết hàng. Bạn xây tầng inventory độc lập mà B04 (Checkout) và B05 (Order) sẽ gọi.

**Phụ thuộc**: cần B02 có `Product` entity đã ổn định (field `id`).

### Migration `V20__inventory.sql`
```sql
CREATE TABLE inventory (
    product_id BIGINT PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    quantity_on_hand INT NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
-- Khởi tạo từ dữ liệu products.stockQuantity hiện có
INSERT INTO inventory (product_id, quantity_on_hand, reserved_quantity)
    SELECT id, stock_quantity, 0 FROM products;

CREATE TABLE inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    change_type VARCHAR(20) NOT NULL CHECK (change_type IN ('RESERVE','RELEASE','COMMIT','ADJUST','RESTOCK')),
    quantity INT NOT NULL,
    reference_type VARCHAR(30),   -- 'ORDER', 'RETURN', 'MANUAL'
    reference_id BIGINT,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_tx_product_id ON inventory_transactions(product_id);
```

### Ngữ nghĩa BẮT BUỘC hiểu đúng trước khi code
- `quantity_on_hand` = tổng tồn kho vật lý thật.
- `reserved_quantity` = phần đang bị "giữ chỗ" cho các đơn `PENDING_PAYMENT`, chưa rời kho thật.
- **Available (để hiển thị/so sánh)** = `quantity_on_hand - reserved_quantity` — **không lưu cột riêng**, luôn tính runtime để tránh 2 cột lệch nhau.
- `RESERVE`: giữ chỗ lúc checkout tạo đơn → `reserved_quantity += qty`. KHÔNG đổi `quantity_on_hand`.
- `RELEASE`: huỷ giữ chỗ (đơn bị huỷ/thanh toán fail trước khi PAID) → `reserved_quantity -= qty`.
- `COMMIT`: thanh toán thành công, hàng thật sự rời kho → `quantity_on_hand -= qty` VÀ `reserved_quantity -= qty` cùng lúc.
- `RESTOCK`: hàng trả về kho (return được duyệt) → `quantity_on_hand += qty`.
- `ADJUST`: admin tự tay chỉnh tồn kho (kiểm kê) → `quantity_on_hand += qty` (qty có thể âm).
- **Mỗi lần đổi số đều phải insert 1 dòng vào `inventory_transactions`** — đây là nguồn duy nhất để truy vết, không được update `inventory` mà bỏ qua ghi log.

### Chữ ký hàm bắt buộc (`InventoryService.java`)
```java
/** Giữ chỗ tồn kho. Ném InsufficientStockException nếu available < quantity.
 *  BẮT BUỘC dùng pessimistic lock để chống race condition. */
void reserveStock(Long productId, int quantity, String referenceType, Long referenceId);

void releaseStock(Long productId, int quantity, String referenceType, Long referenceId);

void commitStock(Long productId, int quantity, String referenceType, Long referenceId);

void restock(Long productId, int quantity, String reason, String referenceType, Long referenceId);

void adjustStock(Long productId, int quantityDelta, String reason); // admin thủ công

int getAvailableQuantity(Long productId);

Page<InventoryView> listLowStock(int threshold, Pageable pageable);
```

**Cách chống race condition (bắt buộc dùng đúng kỹ thuật này)**: trong `InventoryRepository`, thêm:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
Optional<Inventory> findByProductIdForUpdate(Long productId);
```
`reserveStock()` phải: mở trong `@Transactional`, gọi `findByProductIdForUpdate()` (khoá dòng tới khi transaction kết thúc), kiểm tra `quantityOnHand - reservedQuantity >= quantity` **sau khi đã có khoá** (không kiểm tra trước khi lock — đó chính là race condition hiện tại), rồi mới update + insert transaction log.

### API
| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/admin/inventory?lowStock=true&threshold=10&page=&size=` | `inventory:view` |
| GET | `/api/v1/admin/inventory/{productId}/transactions?page=&size=` | `inventory:view` |
| PATCH | `/api/v1/admin/inventory/{productId}/adjust` body `{quantityDelta, reason}` | `inventory:adjust` |

### Test bắt buộc
- `reserveStock_whenInsufficientAvailable_throwsException()`
- `reserveStock_concurrentRequests_doesNotOversell()` — test đa luồng thật (2 thread cùng gọi `reserveStock` khi available=1, qty=1 mỗi lần) — **đây là test quan trọng nhất của toàn block**, phải assert đúng 1 request thành công, 1 request bị từ chối.
- `commitStock_reducesQuantityOnHandAndReservedCorrectly()`
- `restock_increasesQuantityOnHand_andLogsTransaction()`

### Definition of Done
- [ ] Test race-condition đa luồng pass ổn định (chạy lại ≥5 lần không flaky)
- [ ] `modules/inventory/CONTRACT.md` công bố đủ 6 hàm trên cho B04/B05/B07 gọi

---

## PROMPT B04 — Cart & Checkout

**Bối cảnh**: `CartService`/`CartController` hiện tại đã đúng tinh thần (giỏ hàng server-side, không lưu giá/tên trong `cart_items`) — **giữ nguyên phần này**. `OrderService.checkout()` hiện tự trừ `product.stockQuantity` trực tiếp — bạn thay bằng gọi sang `InventoryService` (B03). Thêm bước discount + shipping còn thiếu.

**Phụ thuộc**: B02 (Product ổn định), B03 (`InventoryService.reserveStock()`).

### Migration `V30__discounts_and_addresses_ref.sql`
```sql
CREATE TABLE discounts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    percent_off SMALLINT CHECK (percent_off BETWEEN 1 AND 100),
    amount_off DECIMAL(12,2),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT chk_discount_type CHECK (
        (percent_off IS NOT NULL AND amount_off IS NULL) OR
        (percent_off IS NULL AND amount_off IS NOT NULL)
    )
);
ALTER TABLE carts ADD COLUMN discount_code VARCHAR(30) REFERENCES discounts(code);
```
(Bảng `addresses` thuộc B08 — nếu B08 chưa xong khi bạn tới đây, dùng tạm `shippingAddress: String` tự do nhập trong `CheckoutRequest`, giữ đúng hành vi hiện tại, KHÔNG block tiến độ chờ B08.)

### Chữ ký hàm
```java
// CartService — THÊM, không đổi hàm cũ
Cart applyDiscountCode(Long cartId, String code); // throws NotFoundException nếu code sai/hết hạn/inactive

// CheckoutService (SERVICE MỚI, tách khỏi OrderService — OrderService chỉ còn lo state machine ở B05)
OrderResponse checkout(Long userId, CheckoutRequest request);
```

`CheckoutRequest`: `{ Long addressId; String shippingAddressFreeText; String discountCode; String paymentMethod; }` (có `addressId` HOẶC `shippingAddressFreeText`, không bắt buộc cả 2).

**Trình tự bắt buộc trong `checkout()`** (đúng theo SRS Section 4.7, bọc `@Transactional`):
1. Lấy cart + cart_items từ DB.
2. Với mỗi item: load `Product` mới nhất, kiểm tra `status == ACTIVE`, kiểm tra `inventoryService.getAvailableQuantity(productId) >= quantity` (kiểm tra sớm để báo lỗi thân thiện trước khi lock).
3. Tính `subtotal = Σ(product.price × quantity)`.
4. Nếu có `discountCode`: validate còn hiệu lực, tính `discountAmount`.
5. Tính `shippingFee`: đọc từ cấu hình (`SHIPPING_FLAT_FEE` env, mặc định 30000đ; miễn phí nếu `subtotal >= FREE_SHIPPING_THRESHOLD` env, mặc định 500000đ).
6. `total = subtotal - discountAmount + shippingFee`.
7. Gọi `orderService.createPendingOrder(userId, items, subtotal, discountAmount, shippingFee, total, shippingAddress)` (hàm này thuộc B05 — nếu B05 chưa xong, tạo tạm 1 bản `OrderService.createPendingOrder()` tối giản chỉ insert `Order` status=`PENDING_PAYMENT` + `order_items` snapshot giá, và B05 sẽ mở rộng thêm state machine sau; **ghi rõ điều này trong `CONTRACT.md` của bạn để B05 biết mình đang kế thừa gì**).
8. Với mỗi item: `inventoryService.reserveStock(productId, quantity, "ORDER", order.getId())` — nếu bất kỳ item nào ném `InsufficientStockException`, để exception bay ra ngoài → `@Transactional` tự rollback toàn bộ (kể cả order vừa tạo ở bước 7).
9. Xoá cart_items (giữ nguyên hành vi đúng của code cũ: xoá SAU CÙNG).

### API
| Method | Path | Body | Permission |
|---|---|---|---|
| POST | `/api/v1/cart/apply-discount` | `{code}` | requireUser |
| POST | `/api/v1/checkout` | `CheckoutRequest` | requireUser |

### Test bắt buộc
- `checkout_insufficientStock_rollsBackOrderCreation()` — assert sau khi exception, KHÔNG có order nào được tạo trong DB.
- `checkout_withValidDiscountCode_reducesTotal()`
- `checkout_belowFreeShippingThreshold_addsShippingFee()`

### Definition of Done
- [ ] `modules/checkout/CONTRACT.md` công bố `CheckoutRequest`/`OrderResponse` shape cho B11 (frontend) dùng

---

## PROMPT B05 — Order (State Machine)

**Bối cảnh**: `Order.status` hiện chỉ có 4 giá trị (`PENDING/CONFIRMED/PAID/CANCELLED`), `OrderService.updateStatus()` không kiểm tra transition hợp lệ. Bạn viết lại hoàn toàn phần quản lý trạng thái đơn — đây là module quan trọng nhất, mọi block khác (B06, B07) chỉ được đổi trạng thái đơn **thông qua hàm bạn cung cấp**, không bao giờ tự `UPDATE orders SET status=...`.

**Phụ thuộc**: B03 (`InventoryService.releaseStock/commitStock`), B04 (đã tạo `Order` ở trạng thái `PENDING_PAYMENT`).

### Migration `V40__order_state_machine.sql`
```sql
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (status IN (
    'PENDING_PAYMENT','PAID','PROCESSING','PACKED','SHIPPED','DELIVERED',
    'CANCELLED','RETURN_REQUESTED','RETURNED','REFUNDED'
));
-- Map dữ liệu cũ sang trạng thái mới tương ứng gần nhất
UPDATE orders SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';
UPDATE orders SET status = 'PENDING_PAYMENT' WHERE status = 'CONFIRMED';
-- PAID và CANCELLED giữ nguyên tên, không cần đổi

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note VARCHAR(500),
    changed_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_osh_order_id ON order_status_history(order_id);
```

### Bảng chuyển trạng thái hợp lệ (hard-code thành `Map<OrderStatus, Set<OrderStatus>>` trong code, đây là nguồn chân lý duy nhất)

```
PENDING_PAYMENT  -> { PAID, CANCELLED }
PAID             -> { PROCESSING, CANCELLED }
PROCESSING       -> { PACKED, CANCELLED }
PACKED           -> { SHIPPED }
SHIPPED          -> { DELIVERED }
DELIVERED        -> { RETURN_REQUESTED }
RETURN_REQUESTED -> { RETURNED, DELIVERED }   -- DELIVERED = admin từ chối yêu cầu trả hàng, quay lại trạng thái trước đó
RETURNED         -> { REFUNDED }
CANCELLED, REFUNDED -> {}                      -- trạng thái cuối, không đi tiếp được nữa
```

### Chữ ký hàm bắt buộc
```java
/** HÀM DUY NHẤT được phép đổi orders.status trong toàn hệ thống.
 *  Ném BusinessRuleViolationException nếu transition không hợp lệ theo bảng trên. */
Order transitionStatus(Long orderId, OrderStatus newStatus, Long changedByUserId, String note);

Order createPendingOrder(Long userId, List<OrderItemDraft> items, BigDecimal subtotal,
                          BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal total,
                          String shippingAddress);

List<OrderStatusHistoryEntry> getHistory(Long orderId);
```

**Hiệu ứng phụ bắt buộc bên trong `transitionStatus()`** (không được quên bước nào):
- Mọi lần gọi: insert 1 dòng `order_status_history`.
- `-> CANCELLED` (từ `PENDING_PAYMENT` hoặc `PAID`): với mỗi `order_item`, gọi `inventoryService.releaseStock(productId, quantity, "ORDER", orderId)`.
- `-> PAID`: với mỗi `order_item`, gọi `inventoryService.commitStock(productId, quantity, "ORDER", orderId)`.
- `-> RETURNED`: **không** tự gọi `restock` ở đây — đó là việc của B07 khi xử lý return, tránh 2 nơi cùng cộng kho.

### API
| Method | Path | Body | Permission |
|---|---|---|---|
| GET | `/api/v1/orders/my?page=&size=` | — | requireUser |
| GET | `/api/v1/admin/orders?status=&page=&size=` | — | `order:view_all` |
| GET | `/api/v1/orders/{id}` | — | chủ đơn HOẶC `order:view` |
| GET | `/api/v1/orders/{id}/history` | — | chủ đơn HOẶC `order:view` |
| PATCH | `/api/v1/admin/orders/{id}/status` | `{newStatus, note}` | `order:update` |
| POST | `/api/v1/orders/{id}/cancel` | `{note}` | chủ đơn (chỉ khi status ∈ {PENDING_PAYMENT, PAID}) HOẶC `order:cancel` |

### Test bắt buộc
- `transitionStatus_invalidTransition_throwsBusinessRuleViolation()` — thử `DELIVERED -> PAID` phải fail.
- `transitionStatus_toCancelled_releasesInventory()` — mock `InventoryService`, assert `releaseStock` được gọi đúng tham số.
- `transitionStatus_toPaid_commitsInventory()`
- `transitionStatus_everyCall_writesHistoryRow()`

### Definition of Done
- [ ] `modules/order/CONTRACT.md` công bố enum `OrderStatus` đầy đủ 10 giá trị + bảng transition cho B06/B07 tuân theo

---

## PROMPT B06 — Payment (Cổng thanh toán thật)

**Bối cảnh**: `PaymentService` hiện tại mô phỏng 100% (`payNow()` luôn trả `SUCCESS` ngay lập tức). Bạn tích hợp cổng thật. **Khuyến nghị dùng VNPay sandbox** (phổ biến ở VN, tài liệu tiếng Việt đầy đủ, miễn phí test) — nếu bạn chọn cổng khác (MoMo/Stripe), giữ nguyên interface bên dưới, chỉ đổi phần implementation gọi API.

**Phụ thuộc**: B05 (`OrderService.transitionStatus()`).

### Migration `V50__payment_gateway_fields.sql`
```sql
ALTER TABLE payments
    ADD COLUMN provider VARCHAR(30),
    ADD COLUMN raw_response TEXT;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED'));
```

### Chữ ký hàm
```java
public interface PaymentGatewayClient {
    PaymentInitiationResult initiate(Order order, String returnUrl);  // trả về URL redirect sang cổng
    WebhookVerificationResult verifyAndParse(Map<String,String> rawParams); // verify chữ ký HMAC
}

// PaymentService
PaymentInitiationResult initiatePayment(Long orderId);
void handleWebhook(Map<String,String> rawParams);  // idempotent: nếu transaction_id đã xử lý rồi thì bỏ qua, không xử lý 2 lần
Payment getByOrderId(Long orderId);
```

**`handleWebhook()` BẮT BUỘC làm đúng thứ tự** (đây là endpoint public, không qua `AuthGuard`, nên phải tự bảo vệ bằng chữ ký):
1. `gatewayClient.verifyAndParse(rawParams)` — nếu chữ ký sai, trả `400` ngay, **không** đụng gì vào DB.
2. Tìm `Payment` theo `transactionId`; nếu đã có `status != PENDING` → trả `200 OK` luôn (idempotent — cổng thanh toán hay gọi lại webhook nhiều lần).
3. Nếu thành công: `payment.status = SUCCESS`, `paidAt = now()`, gọi `orderService.transitionStatus(orderId, PAID, null, "Auto by payment webhook")`.
4. Nếu thất bại: `payment.status = FAILED`, gọi `orderService.transitionStatus(orderId, CANCELLED, null, "Payment failed")` (giải phóng tồn kho qua hiệu ứng phụ đã có ở B05).

### API
| Method | Path | Body | Permission |
|---|---|---|---|
| POST | `/api/v1/payments/{orderId}/initiate` | `{returnUrl}` | chủ đơn |
| GET/POST | `/api/v1/payments/webhook/{provider}` | tham số tuỳ cổng (VNPay dùng query param GET) | **Public, tự verify chữ ký, KHÔNG qua AuthGuard** |
| GET | `/api/v1/payments/{orderId}` | — | chủ đơn HOẶC `payment:view` |

### Test bắt buộc
- `handleWebhook_invalidSignature_rejectsWithoutDbChange()`
- `handleWebhook_duplicateTransactionId_isIdempotent()` — gọi webhook 2 lần cùng `transactionId`, assert `transitionStatus` chỉ được gọi 1 lần.
- `handleWebhook_success_transitionsOrderToPaid()`

### Definition of Done
- [ ] Secret/API key cổng thanh toán đọc từ biến môi trường (`PAYMENT_VNPAY_SECRET`...), có trong `.env.example`
- [ ] `modules/payment/CONTRACT.md` công bố `PaymentStatus` enum cho B07 (refund) dùng

---

## PROMPT B07 — Return & Refund

**Bối cảnh**: `ReturnRequest` hiện có (`reason/description/imageUrl/status/adminNote`) khá gần yêu cầu nhưng chỉ hỗ trợ 1 ảnh, không có `return_items` (trả từng sản phẩm riêng), duyệt xong không tự hoàn tiền/hoàn kho. Bạn hoàn thiện domain này.

**Phụ thuộc**: B03 (`InventoryService.restock()`), B05 (`OrderService.transitionStatus()`, chỉ cho phép tạo return khi order đang `DELIVERED`), B06 (gọi refund qua gateway nếu có, hoặc đánh dấu hoàn tiền thủ công).

### Migration `V60__returns_full.sql`
```sql
ALTER TABLE return_requests RENAME TO returns;
ALTER TABLE returns ADD COLUMN status_new VARCHAR(20);
UPDATE returns SET status_new = status;  -- giữ nguyên PENDING/APPROVED/REJECTED
ALTER TABLE returns DROP COLUMN status;
ALTER TABLE returns RENAME COLUMN status_new TO status;
ALTER TABLE returns ADD CONSTRAINT chk_returns_status
    CHECK (status IN ('PENDING','INFO_REQUESTED','APPROVED','REJECTED','RETURNED','REFUNDED'));

CREATE TABLE return_items (
    id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL REFERENCES order_items(id),
    quantity INT NOT NULL CHECK (quantity > 0)
);
CREATE TABLE return_images (
    id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);
-- Di chuyển ảnh đơn lẻ cũ sang bảng mới
INSERT INTO return_images (return_id, image_url, sort_order)
    SELECT id, image_url, 0 FROM returns WHERE image_url IS NOT NULL;

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    return_id BIGINT REFERENCES returns(id),
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    provider_transaction_id VARCHAR(100),
    refunded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### Chữ ký hàm
```java
ReturnRequestDto create(Long userId, Long orderId, String reason, String description,
                         List<MultipartFile> images, List<ReturnItemDraft> items);
                         // throws BusinessRuleViolationException nếu order.status != DELIVERED

ReturnRequestDto updateStatus(Long returnId, ReturnStatus newStatus, String adminNote, Long changedByUserId);
```

**Hiệu ứng phụ bắt buộc trong `updateStatus()`**:
- `-> APPROVED`: không làm gì thêm ngoài đổi status (chờ xử lý vật lý nhận hàng trả về).
- `-> RETURNED` (hàng đã thật sự về kho): với mỗi `return_item`, gọi `inventoryService.restock(productId, quantity, "RETURN", returnId)`; gọi `orderService.transitionStatus(orderId, RETURNED, ...)`.
- `-> REFUNDED`: tạo `Refund` record (`status=PENDING`), gọi `paymentGatewayClient` hoàn tiền nếu cổng hỗ trợ hoàn tự động, nếu không hỗ trợ thì để `status=PENDING` và admin tự xử lý thủ công ngoài hệ thống rồi gọi 1 endpoint riêng xác nhận; gọi `orderService.transitionStatus(orderId, REFUNDED, ...)`.

### API
| Method | Path | Body | Permission |
|---|---|---|---|
| POST | `/api/v1/returns` | multipart: `{orderId, reason, description, items[], images[]}` | requireUser |
| GET | `/api/v1/returns/my` | — | requireUser |
| GET | `/api/v1/admin/returns?status=&page=&size=` | — | `return:view` |
| PATCH | `/api/v1/admin/returns/{id}/status` | `{newStatus, adminNote}` | `return:process` |

### Test bắt buộc
- `create_whenOrderNotDelivered_throwsBusinessRuleViolation()`
- `updateStatus_toReturned_restocksInventoryForEachItem()`
- `updateStatus_toRefunded_createsRefundRecord()`

### Definition of Done
- [ ] `modules/return/CONTRACT.md` công bố shape `ReturnRequestDto`

---

## PROMPT B08 — Customer Management

**Bối cảnh**: `UserService` hiện chỉ có list + xoá cứng. `User.active` tồn tại nhưng không có endpoint nào set `false`. Không có bảng địa chỉ.

**Phụ thuộc**: B01 (permission `user:view`, `user:update`, `user:disable`).

### Migration `V70__addresses.sql`
```sql
CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    ward VARCHAR(100),
    district VARCHAR(100),
    province VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user_id ON addresses(user_id);
```

### Chữ ký hàm
```java
// AddressService
Address create(Long userId, AddressRequest req);
Address update(Long userId, Long addressId, AddressRequest req);  // chỉ chủ địa chỉ được sửa
void delete(Long userId, Long addressId);
void setDefault(Long userId, Long addressId);

// UserService — THÊM
void disableAccount(Long userId);   // set active = false, đồng thời revoke toàn bộ refresh_tokens của user đó (gọi sang module B01)
void enableAccount(Long userId);
Page<Order> getOrderHistory(Long userId, Pageable pageable);  // dùng cho admin xem lịch sử đơn của 1 khách
```

### API
| Method | Path | Permission |
|---|---|---|
| GET/POST | `/api/v1/users/me/addresses` | requireUser |
| PUT/DELETE | `/api/v1/users/me/addresses/{id}` | requireUser (chủ sở hữu) |
| PATCH | `/api/v1/admin/users/{id}/disable` | `user:disable` |
| PATCH | `/api/v1/admin/users/{id}/enable` | `user:disable` |
| GET | `/api/v1/admin/users/{id}/orders?page=&size=` | `user:view` |

### Test bắt buộc
- `disableAccount_revokesAllRefreshTokens()`
- `updateAddress_byNonOwner_throwsForbidden()`

### Definition of Done
- [ ] `modules/user/CONTRACT.md` công bố `Address` shape cho B04/B11 dùng khi chọn địa chỉ giao hàng lúc checkout

---

## PROMPT B09 — Analytics Events & Dashboard mở rộng

**Bối cảnh**: Không có bảng `analytics_events`. `ReportService`/`StatsController` hiện chỉ có doanh thu/top-sản-phẩm cơ bản.

**Nguyên tắc quan trọng**: block này **chỉ đọc**, không bao giờ ghi ngược vào bảng của B02/B05/B06/B07. Để nhận biết sự kiện xảy ra ở các module khác mà không tạo phụ thuộc ngược, dùng `ApplicationEventPublisher` của Spring — các block khác publish event (xem danh sách ở dưới), bạn chỉ lắng nghe.

### Migration `V80__analytics_events.sql`
```sql
CREATE TABLE analytics_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(100),
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_analytics_events_type_created ON analytics_events(event_type, created_at);
```

### Chữ ký hàm
```java
// AnalyticsEventService — KHÔNG BAO GIỜ ném exception ra ngoài (wrap try/catch, chỉ log lỗi)
// để lỗi ghi analytics không bao giờ làm fail luồng nghiệp vụ chính gọi nó.
void track(String eventType, Long userId, String sessionId, Map<String, Object> payload);

@EventListener
void onOrderCreated(OrderCreatedEvent event);     // event_type = "order_created"
@EventListener
void onPaymentSuccess(PaymentSuccessEvent event); // event_type = "payment_success"
@EventListener
void onOrderCancelled(OrderCancelledEvent event);
@EventListener
void onReturnCreated(ReturnCreatedEvent event);
```

**Danh sách event class cần có** (đặt ở package dùng chung `com.example.backend.event`, đây là "hợp đồng chia sẻ" — B05/B06/B07/B02/B04 publish, bạn và B10 cùng lắng nghe, không block nào khác được sửa các class event này nếu không có sự đồng thuận):
```
ProductViewedEvent(productId, userId)
AddToCartEvent(productId, userId, quantity)
CheckoutStartedEvent(userId, cartId)
OrderCreatedEvent(orderId, userId, total)
PaymentSuccessEvent(orderId, amount)
OrderCancelledEvent(orderId, reason)
ReturnCreatedEvent(returnId, orderId)
```
Các block B02 (view sản phẩm), B04 (add to cart, checkout started), B05 (order created/cancelled), B06 (payment success), B07 (return created) chịu trách nhiệm **tự publish** đúng event tương ứng tại đúng điểm nghiệp vụ của mình bằng `applicationEventPublisher.publishEvent(new XxxEvent(...))` — không phải việc của block B09.

### API mở rộng `ReportService`/`StatsController`
```java
Page<InventoryView> getLowStockProducts(int threshold, Pageable pageable);  // gọi InventoryService
long getPendingOrdersCount();
BigDecimal getTotalRefundAmount(LocalDate from, LocalDate to);
List<CustomerFrequency> getCustomerPurchaseFrequency(Pageable pageable);
```
| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/admin/analytics/events?type=&from=&to=&page=&size=` | `analytics:view` |
| GET | `/api/v1/admin/reports/low-stock` | `analytics:view` |
| GET | `/api/v1/admin/reports/pending-orders-count` | `analytics:view` |
| GET | `/api/v1/admin/reports/refund-summary?from=&to=` | `analytics:view` |
| GET | `/api/v1/admin/reports/customer-frequency` | `analytics:view` |

### Test bắt buộc
- `track_whenDbFails_doesNotThrow()` — mock repository ném exception, assert `track()` vẫn return bình thường, chỉ log lỗi.

### Definition of Done
- [ ] `modules/analytics/CONTRACT.md` công bố toàn bộ 7 event class ở package `event` cho các block khác publish đúng chữ ký

---

## PROMPT B10 — Notification

**Bối cảnh**: Không có bất kỳ cơ chế gửi email/SMS nào. Bạn xây tầng này, lắng nghe cùng bộ event như B09 (không phụ thuộc B09, chỉ dùng chung package `event`).

**Lưu ý kiến trúc**: bản đầu dùng `@Async` + `ApplicationEventPublisher` trong-process (đơn giản, đủ cho academic/production nhỏ). Khi B13 dựng xong RabbitMQ (Phase 6 theo roadmap), có thể nâng cấp thành consumer thật mà không đổi chữ ký `NotificationService.send()` — ghi rõ điều này trong `CONTRACT.md` của bạn.

### Migration `V90__notifications.sql`
```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,         -- 'ORDER_CREATED','PAYMENT_SUCCESS','RETURN_APPROVED'...
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### Chữ ký hàm
```java
@Async
void send(Long userId, NotificationType type, Map<String, Object> templateContext);

@EventListener
void onOrderCreated(OrderCreatedEvent event);       // gửi email "Đặt hàng thành công"
@EventListener
void onPaymentSuccess(PaymentSuccessEvent event);   // gửi email "Thanh toán thành công"
@EventListener
void onReturnStatusChanged(ReturnStatusChangedEvent event); // cần B07 publish thêm event này
```

Cấu hình bắt buộc: bật `@EnableAsync` ở main application class (nếu chưa bật — kiểm tra trước, không bật trùng 2 nơi); dùng `JavaMailSender` (Spring Boot starter mail) với SMTP config đọc từ `SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD` (biến môi trường, thêm vào `.env.example`). Nếu SMTP chưa cấu hình (môi trường dev/test), `send()` phải log ra console thay vì ném exception (không được làm fail toàn bộ đơn hàng chỉ vì gửi mail lỗi — đây là yêu cầu SRS "không chặn request quan trọng").

### API
| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/users/me/notifications?page=&size=` | requireUser |
| GET | `/api/v1/admin/notifications?status=&page=&size=` | `analytics:view` (xem log gửi, phục vụ debug) |

### Test bắt buộc
- `send_whenSmtpFails_doesNotThrowAndMarksFailed()`
- `onOrderCreated_createsNotificationRowWithCorrectTemplate()`

### Definition of Done
- [ ] `modules/notification/CONTRACT.md` ghi rõ danh sách `NotificationType` hỗ trợ và template tương ứng

---

## PROMPT B11 — Frontend Storefront (React/Vite)

**Bối cảnh**: Frontend hiện tại (`frontend/src/`) đã có cấu trúc đúng (`context/`, `services/`, `pages/`, `components/`) — giữ nguyên, mở rộng thêm. Thay đổi lớn nhất: chuyển từ token đơn giản sang access+refresh token (B01), và API giờ trả envelope `{data, meta}`/`{error}` thay vì trả thẳng object.

### Việc cần làm
1. **`services/http.js`**: sửa interceptor response để tự unwrap `response.data.data` (envelope mới); interceptor lỗi đọc `error.response.data.error.code` để hiện đúng message; thêm interceptor: khi nhận lỗi `401` với `error.code === 'UNAUTHENTICATED'`, tự gọi `POST /api/v1/auth/refresh` bằng refresh token lưu sẵn, nếu thành công thì gắn access token mới và **retry lại đúng 1 lần** request gốc; nếu refresh cũng fail thì mới điều hướng `/login`.
2. **`context/AuthContext.jsx`**: lưu thêm `permissions: string[]` (từ `/api/v1/auth/me`), expose hook `hasPermission(code)`.
3. **Trang catalog (`Home.jsx`)**: đổi từ lọc client-side sang gọi API `GET /api/v1/products?keyword=&categoryId=&minPrice=&maxPrice=&page=&size=` thật — xoá toàn bộ logic filter bằng JS trên mảng đã tải hết.
4. **`ProductDetail.jsx`**: hiển thị gallery nhiều ảnh (`product.images[]` thay vì 1 ảnh), hiển thị `comparePrice` (giá gạch ngang) nếu có.
5. **Trang mới `Addresses.jsx`**: CRUD địa chỉ giao hàng (gọi API B08), chọn địa chỉ mặc định lúc checkout thay vì nhập tay.
6. **`Checkout.jsx`**: thêm ô nhập `discountCode`, hiển thị rõ 3 dòng `Tạm tính / Giảm giá / Phí ship / Tổng cộng` (không gộp chung 1 số như hiện tại).
7. **`OrderDetail.jsx`**: hiển thị timeline trạng thái đơn (gọi `GET /api/v1/orders/:id/history`), nút "Huỷ đơn" chỉ hiện khi `status` nằm trong tập cho phép huỷ (lấy đúng theo bảng transition ở B05, không tự đoán).
8. **Form tạo return request**: cho chọn nhiều ảnh (input `multiple`), chọn từng sản phẩm trong đơn muốn trả (không phải trả cả đơn như hiện tại).

### Định dạng response mới cần biết khi viết code gọi API
```js
// Thành công — luôn unwrap .data trong http.js, code trong page component nhận thẳng giá trị thật
// Lỗi — http.js ném Error với .code và .message lấy từ error.code/error.message
try {
  await orderApi.cancel(orderId, note);
} catch (err) {
  if (err.code === 'BUSINESS_RULE_VIOLATION') { /* hiện message cụ thể */ }
}
```

### Definition of Done
- [ ] Không còn bất kỳ logic lọc sản phẩm bằng JS thuần trên mảng đầy đủ
- [ ] Refresh token tự động hoạt động, test thủ công: đợi access token hết hạn (đặt `JWT_SECRET` TTL ngắn lúc test), gọi 1 API, xác nhận tự refresh mà không bị văng ra `/login`

---

## PROMPT B12 — Frontend Admin (CMS)

**Phụ thuộc**: toàn bộ API block B01–B10 cần có `CONTRACT.md` ổn định trước khi hoàn thiện UI tương ứng (có thể bắt đầu UI sớm bằng mock response theo đúng shape trong `CONTRACT.md`).

### Việc cần làm
1. **Permission-based rendering**: tạo hook `usePermission(code)` đọc từ `AuthContext.permissions`; mọi nút hành động nhạy cảm (xoá sản phẩm, duyệt return, đổi trạng thái đơn) **phải** bọc điều kiện `hasPermission('product:delete')` — không hiện nút nếu thiếu quyền, dù vẫn phải hiểu đây chỉ là UX, backend đã tự chặn.
2. **Trang quản lý sản phẩm**: form tạo/sửa có đủ field mới (`slug` tự sinh từ `name` nhưng cho sửa tay, `sku`, `brand`, `comparePrice`), upload nhiều ảnh kéo-thả sắp xếp `sortOrder`, nút "Publish" riêng (gọi API publish, hiện lỗi rõ ràng nếu thiếu field bắt buộc).
3. **Trang quản lý category**: hiển thị dạng cây (thu gọn/mở rộng theo `parent_id`), kéo-thả đổi cha (tối thiểu: dropdown chọn category cha khi tạo/sửa).
4. **Trang Inventory mới**: danh sách tồn kho, lọc "sắp hết hàng", nút điều chỉnh thủ công (`ADJUST`) yêu cầu nhập `reason` bắt buộc, xem lịch sử transaction của 1 sản phẩm.
5. **Trang Orders**: hiển thị đúng 10 trạng thái mới, nút đổi trạng thái chỉ hiện các lựa chọn **hợp lệ theo bảng transition** (gọi API hoặc hard-code đúng bảng ở B05 phía frontend để disable nút sai, backend vẫn là chốt chặn cuối).
6. **Trang Returns/Refunds**: duyệt/từ chối, xem ảnh (gallery nhiều ảnh), xem trạng thái refund liên kết.
7. **Trang Analytics mở rộng**: thêm card "Low stock", "Pending orders", "Refund amount tháng này", "Tần suất mua hàng theo khách" (dùng API B09).
8. **Trang Customer Management**: nút Enable/Disable tài khoản, xem lịch sử đơn của khách ngay trong trang chi tiết khách hàng.

### Definition of Done
- [ ] Không có bất kỳ nút hành động nào hiện ra cho user thiếu permission tương ứng (kiểm tra bằng cách đăng nhập tài khoản MANAGER thiếu 1 permission cụ thể, xác nhận đúng nút biến mất)

---

## PROMPT B13 — Infra, CI/CD, Observability, Testing Framework

**Đây là block duy nhất chạy độc lập hoàn toàn ngay từ ngày đầu, không chờ block nào.**

### Việc cần làm

1. **Flyway**: thêm dependency `org.flywaydb:flyway-core` + `flyway-database-postgresql`, chuyển `database/init-postgres.sql` hiện có thành `backend/src/main/resources/db/migration/V1__baseline.sql` (copy nguyên văn, không sửa nội dung), xoá cơ chế Hibernate `ddl-auto` nếu đang bật (đổi `spring.jpa.hibernate.ddl-auto=validate` để Hibernate không tự ý đổi schema nữa — Flyway là nguồn chân lý duy nhất).

2. **Dockerfile backend** (`backend/Dockerfile`, multi-stage):
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
```

3. **Dockerfile frontend** (`frontend/Dockerfile`, build tĩnh + Nginx):
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

4. **`docker-compose.yml` đầy đủ** (mở rộng file hiện có, không viết đè — chỉ thêm service): `postgres` (giữ nguyên), thêm `redis:7-alpine` (port 6379), thêm `rabbitmq:3-management-alpine` (port 5672 + UI 15672), thêm service `backend` (build từ Dockerfile, depends_on postgres/redis/rabbitmq, đọc `.env`), thêm service `frontend` (build từ Dockerfile, depends_on backend).

5. **GitHub Actions** (`.github/workflows/ci.yml`):
```yaml
name: CI
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: cd backend && mvn -B test
      - run: cd backend && mvn -B package -DskipTests
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd frontend && npm ci
      - run: cd frontend && npm run build
```
(Thêm bước build/push Docker image sau khi 2 job trên xanh, dùng `docker/build-push-action`, đẩy lên GitHub Container Registry — chỉ bật ở nhánh `main`.)

6. **Jacoco** (code coverage): thêm plugin `jacoco-maven-plugin` vào `pom.xml`, cấu hình fail build nếu coverage < 60% cho package `service/` (không áp cho `controller/`/`dto/` — ít logic, không đáng bắt buộc coverage cao). Mỗi block B01–B10 tự viết test của mình để đạt ngưỡng này, B13 chỉ dựng công cụ đo.

7. **Observability**: thêm `spring-boot-starter-actuator`, bật `/actuator/health`, `/actuator/info`; cấu hình Logback ghi log dạng JSON (dùng `logstash-logback-encoder`) để dễ đưa vào ELK sau này; thêm `logging.level.com.example.backend=INFO` mặc định, `DEBUG` qua biến môi trường `LOG_LEVEL`.

8. **`.env.example`** tổng hợp — gom tất cả biến môi trường mà các block B01–B10 đã khai báo rải rác, giữ file này làm nguồn tổng hợp duy nhất (B13 có trách nhiệm theo dõi và cập nhật file này mỗi khi có block mới thêm biến).

### Definition of Done
- [ ] `docker compose up` từ thư mục gốc chạy được toàn bộ stack (postgres+redis+rabbitmq+backend+frontend) không lỗi
- [ ] `mvn test` chạy được toàn bộ test của mọi block đã merge, báo cáo coverage xuất ra `target/site/jacoco/index.html`
- [ ] CI pipeline xanh trên GitHub Actions cho 1 PR thử nghiệm
- [ ] `/actuator/health` trả `200 {"status":"UP"}`

---

## Ghi chú cuối cùng cho người điều phối (bạn)

- Giao **B13 và B01** trước tiên, song song — mọi block khác phụ thuộc gián tiếp vào nền tảng 2 block này (migration framework + auth).
- Khi nhận lại kết quả từ 1 agent, việc đầu tiên luôn là: đọc `CONTRACT.md` block đó công bố, đối chiếu với `01_CONVENTIONS.md`, rồi mới cho agent của block phụ thuộc bắt đầu.
- Nếu 2 agent cùng lúc muốn sửa file ngoài phạm vi module của mình (file "dùng chung"), bạn là người duyệt cuối cùng — đừng để agent tự ý merge.
