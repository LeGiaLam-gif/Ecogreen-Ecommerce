import React, { useState } from 'react';
import './ReturnPolicy.css';

const POLICY = [
  {
    icon: '📅',
    title: 'Thời hạn đổi/trả',
    content: '7 ngày kể từ ngày nhận hàng. Sản phẩm phải còn nguyên vẹn, chưa qua sử dụng và còn đủ bao bì.',
  },
  {
    icon: '✅',
    title: 'Điều kiện được chấp nhận',
    content: 'Sản phẩm bị lỗi do nhà sản xuất, giao sai hàng so với đơn đặt, hoặc hàng bị hư hại trong quá trình vận chuyển.',
  },
  {
    icon: '❌',
    title: 'Không được đổi/trả',
    content: 'Sản phẩm đã qua sử dụng, sản phẩm vệ sinh cá nhân (vì lý do sức khỏe), hoặc hàng đã quá 7 ngày kể từ ngày nhận.',
  },
  {
    icon: '🔄',
    title: 'Quy trình đổi trả',
    content: 'Đăng nhập → Xem đơn hàng → Bấm "Yêu cầu đổi/trả" → Điền lý do + đính kèm ảnh → Chờ EcoGreen xác nhận trong 1–2 ngày làm việc.',
  },
  {
    icon: '💰',
    title: 'Hoàn tiền',
    content: 'Sau khi yêu cầu được phê duyệt, hoàn tiền 100% trong 3–5 ngày làm việc qua phương thức thanh toán ban đầu.',
  },
  {
    icon: '📦',
    title: 'Chi phí hoàn trả',
    content: 'EcoGreen chịu toàn bộ chi phí vận chuyển hàng hoàn trả nếu lỗi do chúng tôi. Trường hợp đổi ý, khách hàng chịu phí ship một chiều.',
  },
];

const ReturnPolicy = () => (
  <div className="return-policy-page">
    <div className="rp-header">
      <div className="rp-badge">♻️ Chính sách</div>
      <h1>Chính sách Đổi Trả EcoGreen</h1>
      <p>Chúng tôi cam kết mang đến trải nghiệm mua sắm không lo lắng. Hoàn trả dễ dàng, minh bạch.</p>
    </div>

    <div className="rp-grid">
      {POLICY.map((item, i) => (
        <div className="rp-card" key={i}>
          <div className="rp-card-icon">{item.icon}</div>
          <h3>{item.title}</h3>
          <p>{item.content}</p>
        </div>
      ))}
    </div>

    <div className="rp-cta">
      <h2>Cần hỗ trợ đổi trả?</h2>
      <p>Liên hệ với chúng tôi qua <strong>support@ecogreen.vn</strong> hoặc hotline <strong>1800 6789</strong> (miễn phí)</p>
    </div>
  </div>
);

export default ReturnPolicy;
