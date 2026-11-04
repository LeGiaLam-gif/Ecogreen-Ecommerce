import React from 'react';
import LegalPage from '../components/LegalPage';

const SECTIONS = [
  {
    title: 'Thông tin chúng tôi thu thập',
    paragraphs: [
      'Khi bạn đăng ký hoặc đăng nhập: tên đăng nhập, địa chỉ email và mật khẩu (mật khẩu được lưu dưới dạng mã băm, EcoGreen không đọc được mật khẩu gốc).',
      'Khi bạn đăng nhập bằng Google hoặc Facebook: mã định danh tài khoản, tên hiển thị và địa chỉ email mà nhà cung cấp trả về. EcoGreen không nhận mật khẩu của bạn tại Google hoặc Facebook và không đăng bài thay bạn.',
      'Khi bạn mua hàng: họ tên, số điện thoại, địa chỉ giao hàng, nội dung đơn hàng và trạng thái thanh toán.'
    ]
  },
  {
    title: 'Mục đích sử dụng',
    paragraphs: [
      'Tạo và bảo vệ tài khoản, xử lý đơn hàng, giao hàng, hoàn tiền, gửi email liên quan đến tài khoản và đơn hàng (ví dụ email đặt lại mật khẩu), hỗ trợ khách hàng và phòng chống gian lận.'
    ]
  },
  {
    title: 'Chia sẻ thông tin',
    paragraphs: [
      'EcoGreen chỉ chia sẻ thông tin cần thiết cho đơn vị vận chuyển và cổng thanh toán để hoàn tất đơn hàng, hoặc khi pháp luật yêu cầu. Chúng tôi không bán dữ liệu cá nhân của bạn.'
    ]
  },
  {
    title: 'Lưu trữ và bảo mật',
    paragraphs: [
      'Dữ liệu được lưu trên hệ thống của EcoGreen và được bảo vệ bằng các biện pháp kỹ thuật như mã hóa mật khẩu và giới hạn truy cập. Thông tin tài khoản được giữ trong thời gian tài khoản còn hoạt động; dữ liệu đơn hàng có thể được giữ lâu hơn theo yêu cầu kế toán và pháp luật.'
    ]
  },
  {
    title: 'Quyền của bạn',
    paragraphs: ['Bạn có quyền xem, chỉnh sửa và yêu cầu xóa dữ liệu cá nhân của mình, và có thể rút lại sự đồng ý bất kỳ lúc nào.']
  },
  {
    title: 'Xóa dữ liệu (bao gồm dữ liệu từ Facebook và Google)',
    paragraphs: [
      'Để yêu cầu xóa tài khoản và dữ liệu liên quan, hãy gửi yêu cầu qua trang Liên hệ trên website với tiêu đề "Xóa dữ liệu", kèm email đăng ký tài khoản. Chúng tôi sẽ xử lý và phản hồi trong thời gian hợp lý.',
      'Bạn cũng có thể gỡ quyền của EcoGreen bất kỳ lúc nào trong phần cài đặt bảo mật của tài khoản Facebook hoặc Google của mình.'
    ]
  },
  {
    title: 'Thay đổi chính sách',
    paragraphs: ['Chính sách có thể được cập nhật; phiên bản mới có hiệu lực khi được đăng tải trên website.']
  }
];

const PrivacyPolicy = () => <LegalPage title="Chính sách bảo mật" updated="30/10/2026" sections={SECTIONS} />;

export default PrivacyPolicy;
