import React, { useState } from 'react';
import './ContactUs.css';

const ContactUs = () => {
  const [form, setForm] = useState({ name: '', email: '', subject: '', message: '' });
  const [sent, setSent] = useState(false);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = (e) => {
    e.preventDefault();
    // Trong đồ án: hiển thị thông báo giả lập gửi thành công
    setSent(true);
    setTimeout(() => setSent(false), 4000);
    setForm({ name: '', email: '', subject: '', message: '' });
  };

  return (
    <div className="contact-page">
      <div className="contact-header">
        <div className="contact-badge">📬 Liên hệ</div>
        <h1>Chúng tôi luôn lắng nghe bạn</h1>
        <p>Gửi câu hỏi, góp ý hoặc yêu cầu hỗ trợ — đội ngũ EcoGreen sẽ phản hồi trong vòng 24 giờ.</p>
      </div>

      <div className="contact-wrapper">
        {/* Info cards */}
        <div className="contact-info">
          <div className="contact-card">
            <span className="contact-card-icon">📧</span>
            <h3>Email</h3>
            <p>support@ecogreen.vn</p>
          </div>
          <div className="contact-card">
            <span className="contact-card-icon">📞</span>
            <h3>Hotline</h3>
            <p>1800 6789 (miễn phí)<br />8:00 – 22:00 hằng ngày</p>
          </div>
          <div className="contact-card">
            <span className="contact-card-icon">🏢</span>
            <h3>Địa chỉ</h3>
            <p>123 Đường Xanh, Q. 1<br />TP. Hồ Chí Minh</p>
          </div>
          <div className="contact-card">
            <span className="contact-card-icon">🕒</span>
            <h3>Giờ làm việc</h3>
            <p>Thứ 2 – Thứ 7<br />8:00 – 18:00</p>
          </div>
        </div>

        {/* Form */}
        <form className="contact-form" onSubmit={handleSubmit}>
          <h2>Gửi tin nhắn</h2>

          {sent && (
            <div className="contact-success">
              ✅ Tin nhắn của bạn đã được gửi! Chúng tôi sẽ phản hồi sớm nhất.
            </div>
          )}

          <div className="contact-row">
            <div className="contact-field">
              <label>Họ và tên *</label>
              <input name="name" value={form.name} onChange={handleChange} required placeholder="Nguyễn Văn A" />
            </div>
            <div className="contact-field">
              <label>Email *</label>
              <input name="email" type="email" value={form.email} onChange={handleChange} required placeholder="email@example.com" />
            </div>
          </div>

          <div className="contact-field">
            <label>Chủ đề</label>
            <select name="subject" value={form.subject} onChange={handleChange}>
              <option value="">-- Chọn chủ đề --</option>
              <option value="order">Đơn hàng</option>
              <option value="return">Đổi/Trả hàng</option>
              <option value="product">Thông tin sản phẩm</option>
              <option value="payment">Thanh toán</option>
              <option value="other">Khác</option>
            </select>
          </div>

          <div className="contact-field">
            <label>Nội dung *</label>
            <textarea
              name="message"
              value={form.message}
              onChange={handleChange}
              required
              rows={5}
              placeholder="Mô tả chi tiết vấn đề hoặc câu hỏi của bạn..."
            />
          </div>

          <button type="submit" className="contact-submit-btn">
            Gửi tin nhắn →
          </button>
        </form>
      </div>
    </div>
  );
};

export default ContactUs;
