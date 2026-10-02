import React, { useState } from 'react';
import './FAQ.css';

const FAQS = [
  {
    q: 'Sản phẩm EcoGreen được làm từ nguyên liệu gì?',
    a: 'Tất cả sản phẩm trên EcoGreen được làm từ vật liệu tái chế (nhựa tái sinh, sợi bông/polyester tái chế, bao đay tái sử dụng) hoặc nguyên liệu tự nhiên phân hủy sinh học (tre, đay, xơ dừa, giấy kraft). Mỗi sản phẩm đều có thông tin chứng nhận tái chế.',
  },
  {
    q: 'Chính sách vận chuyển của EcoGreen như thế nào?',
    a: 'Chúng tôi giao hàng toàn quốc. Miễn phí giao hàng cho đơn từ 500,000₫. Đóng gói 100% bằng giấy tái chế và hộp carton, không dùng túi nilon hoặc xốp. Thời gian giao hàng 2–5 ngày làm việc.',
  },
  {
    q: 'Tôi có thể đổi trả hàng không?',
    a: 'Có! EcoGreen hỗ trợ đổi/trả trong 7 ngày kể từ ngày nhận hàng nếu sản phẩm bị lỗi hoặc không đúng đơn. Vào mục "Đơn hàng của tôi" → chọn đơn hàng → bấm "Yêu cầu đổi/trả" để gửi yêu cầu.',
  },
  {
    q: 'Làm thế nào để thanh toán bằng VietQR?',
    a: 'Sau khi đặt hàng, chọn phương thức "Chuyển khoản ngân hàng (VietQR)". Hệ thống sẽ hiện mã QR tự động điền sẵn số tiền và nội dung chuyển khoản. Quét bằng app ngân hàng bất kỳ là xong!',
  },
  {
    q: 'Sản phẩm tái chế có an toàn không?',
    a: 'Hoàn toàn an toàn. Tất cả sản phẩm đã qua kiểm định không chứa BPA, chì và các hóa chất độc hại. Sợi tái chế được xử lý ở nhiệt độ cao và rửa sạch trước khi sản xuất.',
  },
  {
    q: 'Tôi có thể mua sỉ không?',
    a: 'Có! EcoGreen hỗ trợ mua sỉ từ 10 sản phẩm trở lên với mức chiết khấu từ 10–20%. Liên hệ qua email wholesale@ecogreen.vn hoặc hotline 1800 6789 để được tư vấn.',
  },
  {
    q: 'Làm thế nào để bảo quản sản phẩm tái chế?',
    a: 'Sợi cotton và polyester tái chế: giặt tay hoặc giặt máy nước lạnh, phơi ngoài trời. Sản phẩm giấy tái chế: bảo quản nơi khô ráo. Balo đay: lau bằng khăn ẩm, tránh ướt nước nhiều.',
  },
  {
    q: 'Tôi có thể theo dõi đơn hàng không?',
    a: 'Có! Vào mục "Đơn hàng của tôi" để xem trạng thái theo thời gian thực: Đặt hàng → Xác nhận → Đang đóng gói → Đang giao → Thành công. Thông báo email cũng được gửi ở mỗi bước.',
  },
  {
    q: 'EcoGreen có chứng nhận xanh nào không?',
    a: 'EcoGreen hợp tác với các nhà cung cấp đạt chứng nhận: GRS (Global Recycled Standard) cho sợi tái chế, FSC cho sản phẩm giấy và gỗ, và OEKO-TEX cho sản phẩm dệt may.',
  },
  {
    q: 'Liên hệ hỗ trợ ở đâu?',
    a: 'Email: support@ecogreen.vn | Hotline: 1800 6789 (miễn phí, 8h–22h hằng ngày) | Chat trực tiếp: bấm biểu tượng chat ở góc phải màn hình.',
  },
];

const FAQ = () => {
  const [openIdx, setOpenIdx] = useState(null);

  return (
    <div className="faq-page">
      <div className="faq-header">
        <div className="faq-badge">❓ Hỗ trợ</div>
        <h1>Câu hỏi thường gặp</h1>
        <p>Tìm câu trả lời nhanh cho những thắc mắc phổ biến nhất về EcoGreen</p>
      </div>

      <div className="faq-list">
        {FAQS.map((item, i) => (
          <div
            key={i}
            className={`faq-item ${openIdx === i ? 'open' : ''}`}
            onClick={() => setOpenIdx(openIdx === i ? null : i)}
          >
            <div className="faq-q">
              <span>{item.q}</span>
              <span className="faq-icon">{openIdx === i ? '−' : '+'}</span>
            </div>
            {openIdx === i && <div className="faq-a">{item.a}</div>}
          </div>
        ))}
      </div>

      <div className="faq-contact-box">
        <p>Không tìm thấy câu trả lời? <a href="/contact">Liên hệ với chúng tôi →</a></p>
      </div>
    </div>
  );
};

export default FAQ;
