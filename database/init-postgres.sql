-- =====================================================================
-- EcoGreen E-Commerce - Database Schema (PostgreSQL version)
-- =====================================================================
-- Bản tương đương của init.sql (MySQL) cho PostgreSQL. Chuẩn hoá đến 3NF.
-- Không seed dữ liệu nghiệp vụ (users/products/orders) - chỉ seed roles.
--
-- CÁCH CHẠY:
--   1) Tạo database trước (Postgres không hỗ trợ CREATE DATABASE IF NOT
--      EXISTS trong cùng 1 script/transaction như MySQL):
--         createdb ecogreen
--         -- hoặc: psql -U postgres -c "CREATE DATABASE ecogreen;"
--   2) Chạy script này SAU KHI đã kết nối vào db "ecogreen":
--         psql -U postgres -d ecogreen -f init-postgres.sql
--
-- Khác biệt so với bản MySQL (init.sql):
--   - AUTO_INCREMENT           -> BIGSERIAL
--   - ENGINE=InnoDB / CHARSET  -> bỏ (Postgres không cần khai báo)
--   - ENUM('A','B') inline     -> VARCHAR + CHECK (khớp với cách Hibernate
--                                 map @Enumerated(EnumType.STRING) mặc định)
--   - ON UPDATE CURRENT_TIMESTAMP -> bỏ ở tầng DB; ứng dụng (JPA @PreUpdate)
--                                 đã tự cập nhật updated_at, nên không cần
--                                 trigger riêng cho phiên bản đầu này
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. roles
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- ---------------------------------------------------------------------
-- 2. users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50)  NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,      -- BCrypt hash, never plaintext
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- ---------------------------------------------------------------------
-- 3. user_roles
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 4. categories
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 5. products
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price DECIMAL(12,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    image VARCHAR(255),                       -- path/reference only, never binary data
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_stock CHECK (stock_quantity >= 0),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id);

-- ---------------------------------------------------------------------
-- 6. carts - mỗi user đã đăng nhập có đúng một cart (1-1)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS carts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 7. cart_items - KHÔNG lưu product_name/price/image (3NF)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT chk_cart_items_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_product_id ON cart_items(product_id);

-- ---------------------------------------------------------------------
-- 8. orders - snapshot giao hàng tại thời điểm đặt hàng
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    shipping_address TEXT NOT NULL,
    total_price DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING','CONFIRMED','PAID','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);

-- ---------------------------------------------------------------------
-- 9. order_items - price = giá tại thời điểm mua (lịch sử, không đổi)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_order_items_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items(product_id);

-- ---------------------------------------------------------------------
-- 10. payments - mô phỏng thanh toán (COD / MOCK_PAYMENT)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING','SUCCESS','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);

-- ---------------------------------------------------------------------
-- Dữ liệu cấu hình HỆ THỐNG duy nhất được seed
-- ---------------------------------------------------------------------
INSERT INTO roles (name) VALUES ('USER'), ('ADMIN')
    ON CONFLICT (name) DO NOTHING;

-- KHÔNG insert users / categories / products / orders / order_items / payments.
