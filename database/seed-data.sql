-- =====================================================================
-- EcoGreen E-Commerce - Seed Data Script
-- Dữ liệu từ recycled_products.xlsx - Sản phẩm tái chế xanh
-- =====================================================================

-- 1. Bật extension pgcrypto để sinh mật khẩu BCrypt chuẩn
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Đảm bảo 2 role hệ thống tồn tại
INSERT INTO roles (name) VALUES ('USER'), ('ADMIN')
ON CONFLICT (name) DO NOTHING;

-- 3. Tạo tài khoản mẫu:
--    - admin / admin123 (quản trị viên, có role ADMIN + USER)
--    - khachhang / 123456 (khách mua hàng, có role USER)
INSERT INTO users (username, email, password, is_active, created_at, updated_at)
VALUES 
  ('admin', 'admin@ecogreen.vn', crypt('admin123', gen_salt('bf', 10)), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('khachhang', 'khachhang@ecogreen.vn', crypt('123456', gen_salt('bf', 10)), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

-- 4. Phân quyền trong bảng user_roles
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE (u.username = 'admin' AND r.name IN ('USER', 'ADMIN'))
   OR (u.username = 'khachhang' AND r.name = 'USER')
ON CONFLICT DO NOTHING;

-- 5. Tạo giỏ hàng gắn với mỗi tài khoản (quan hệ 1-1)
INSERT INTO carts (user_id, created_at, updated_at)
SELECT u.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
WHERE u.username IN ('admin', 'khachhang')
  AND NOT EXISTS (SELECT 1 FROM carts c WHERE c.user_id = u.id);

-- 6. Xóa danh mục và sản phẩm cũ nếu có để nạp lại mới nhất
DELETE FROM cart_items;
DELETE FROM order_items;
DELETE FROM payments;
DELETE FROM orders;
DELETE FROM products;
DELETE FROM categories;

-- Reset sequence nếu có
ALTER SEQUENCE IF EXISTS categories_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS products_id_seq RESTART WITH 1;

-- 7. Danh mục sản phẩm từ recycled_products.xlsx (5 danh mục tái chế xanh)
INSERT INTO categories (id, name, description) VALUES
  (1, 'Chăm Sóc Cá Nhân & Thảo Mộc', 'Phụ kiện tóc từ nhựa tái chế, dụng cụ vệ sinh cá nhân từ vật liệu tái sinh thân thiện với môi trường và sức khỏe.'),
  (2, 'Nhà Cửa & Nội Thất Xanh', 'Rèm cửa, giỏ đựng đồ và nội thất gia đình được sản xuất từ sợi vải, nhựa và vật liệu tái chế.'),
  (3, 'Thời Trang & Phụ Kiện Xanh', 'Túi xách, balo và phụ kiện thời trang làm thủ công từ vải bao đay tái sử dụng và nguyên liệu tự nhiên.'),
  (4, 'Nhà Bếp Sinh Thái', 'Giấy vệ sinh, đĩa giấy và vật dụng nhà bếp được sản xuất từ 100% nguyên liệu tái chế, không khai thác cây xanh.'),
  (5, 'Bao Bì & Quà Tặng Xanh', 'Túi đóng gói, hộp quà tặng và bao bì sinh thái từ vật liệu tái chế hậu tiêu dùng, giảm rác thải nhựa.')
ON CONFLICT (name) DO NOTHING;

-- Đồng bộ lại sequence categories
SELECT setval('categories_id_seq', (SELECT MAX(id) FROM categories));

-- 8. Sản phẩm từ recycled_products.xlsx (8 sản phẩm tái chế xanh)
INSERT INTO products (category_id, name, description, price, stock_quantity, image, status) VALUES

  -- Danh mục 1: Chăm Sóc Cá Nhân & Thảo Mộc
  (1, 'Kẹp Tóc Lớn Nhựa Tái Chế Vân Đá Cẩm Thạch',
   'Kẹp tóc bản to làm từ nhựa acrylic tái chế bền chắc, kết hợp lò xo chịu lực cao cấp giữ chặt tóc dày mà không lo gãy. Thiết kế vân đá cẩm thạch sang trọng, phù hợp mọi kiểu tạo mẫu tóc hàng ngày. Mỗi sản phẩm góp phần giảm thiểu rác thải nhựa ra môi trường.',
   249000, 90, 'https://m.media-amazon.com/images/I/412-7p0BzLL._SX300_SY300_QL70_ML2_.jpg', 'ACTIVE'),

  -- Danh mục 2: Nhà Cửa & Nội Thất Xanh
  (2, 'Rèm Lót Phòng Tắm Vải Polyester Tái Chế Chống Thấm',
   'Rèm lót phòng tắm dệt 100% từ sợi polyester tái chế, chống thấm nước, không độc hại, khoen inox chống gỉ chắc chắn. Lựa chọn bền vững giúp giảm rác thải nhựa cho không gian phòng tắm. Dễ giặt máy, nhanh khô và bảo quản màu sắc lâu dài.',
   425000, 55, 'https://m.media-amazon.com/images/I/71N6sd00S+L._AC_SY300_SX300_.jpg', 'ACTIVE'),

  (2, 'Rèm Cửa Sổ Vải Cotton Tái Chế Họa Tiết Sọc Nông Trại',
   'Rèm valance trang trí cửa sổ dệt từ sợi cotton tái chế (75% cotton tiền tiêu dùng, 25% polyester hậu tiêu dùng), họa tiết sọc phong cách nông trại mộc mạc. Lọc ánh sáng dịu nhẹ, tôn lên vẻ ấm cúng cho bếp hoặc phòng tắm.',
   475000, 40, 'https://m.media-amazon.com/images/I/71c38eILDCL.__AC_SX300_SY300_QL70_ML2_.jpg', 'ACTIVE'),

  (2, 'Giỏ Đựng Đồ Giặt Dây Cotton Tái Chế Đan Thủ Công',
   'Giỏ đựng đồ đan thủ công từ dây cotton tái chế 100%, không chứa nhựa hay hóa chất độc hại. Kích thước rộng rãi, phù hợp đựng quần áo, chăn mền, khăn tắm hoặc đồ chơi. Tay đan thủ công cho độ bền và tính thẩm mỹ cao.',
   725000, 35, 'https://m.media-amazon.com/images/I/81-qhWrGO7L.__AC_SX300_SY300_QL70_ML2_.jpg', 'ACTIVE'),

  -- Danh mục 3: Thời Trang & Phụ Kiện Xanh
  (3, 'Balo Vải Bao Đay Đựng Gạo Tái Chế Thủ Công Nepal',
   'Balo độc bản làm thủ công từ bao đay đựng gạo đã qua sử dụng, mỗi chiếc mang họa tiết riêng không trùng lặp. Có nắp gài nút bấm, 3 túi ngoài và dây đeo điều chỉnh được. Sản phẩm thủ công của các nghệ nhân Nepal.',
   750000, 20, 'https://m.media-amazon.com/images/I/715XBovmouL.__AC_SX300_SY300_QL70_ML2_.jpg', 'ACTIVE'),

  -- Danh mục 4: Nhà Bếp Sinh Thái
  (4, 'Giấy Vệ Sinh Tái Chế 100% Không Dùng Gỗ Cây (16 Cuộn)',
   'Giấy vệ sinh sản xuất từ 100% giấy tái chế, không khai thác cây xanh, giúp giảm phát thải CO2 và tiết kiệm nước trong quá trình sản xuất. Chất giấy mềm mại nhưng vẫn dai chắc. Không tẩy trắng bằng clo độc hại.',
   862000, 65, 'https://m.media-amazon.com/images/I/61xrNLk1aQL.__AC_SX300_SY300_QL70_ML2_.jpg', 'ACTIVE'),

  (4, 'Đĩa Giấy Sợi Tái Chế 100% Dùng Một Lần (36 Cái)',
   'Đĩa dùng một lần làm từ 100% sợi giấy tái chế, mang lại vòng đời mới cho nguồn nguyên liệu sẵn có. An toàn với lò vi sóng, chống cắt và chống rò rỉ. Sau khi dùng có thể ủ phân compost 100%.',
   250000, 75, 'https://m.media-amazon.com/images/I/8126w+2bHbL._AC_SY300_SX300_.jpg', 'ACTIVE'),

  -- Danh mục 5: Bao Bì & Quà Tặng Xanh
  (5, 'Túi Gói Hàng Poly Tái Chế Tự Dán (300 Cái)',
   'Túi gói hàng làm hoàn toàn từ vật liệu tái chế hậu tiêu dùng được chứng nhận 100%, tiêu tốn ít năng lượng sản xuất hơn túi poly thông thường. Dày 2.5 mil, tự dán chắc chắn, phù hợp cho các cửa hàng kinh doanh nhỏ muốn đóng gói xanh.',
   924000, 50, 'https://m.media-amazon.com/images/I/517vVT0JPvS.__AC_SX300_SY300_QL70_ML2_.jpg', 'ACTIVE');

-- Đồng bộ lại sequence products
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));
