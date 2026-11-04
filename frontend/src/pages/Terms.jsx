import React from 'react';
import LegalPage from '../components/LegalPage';

const SECTIONS = [
  {
    title: 'Phạm vi áp dụng',
    paragraphs: [
      'Điều khoản này áp dụng khi bạn truy cập website EcoGreen, tạo tài khoản và mua sản phẩm tại EcoGreen. Khi đăng ký hoặc đặt hàng, bạn xác nhận đã đọc và đồng ý với các điều khoản dưới đây.'
    ]
  },
  {
    title: 'Tài khoản',
    paragraphs: [
      'Bạn cần cung cấp thông tin chính xác khi đăng ký và chịu trách nhiệm về mọi hoạt động diễn ra dưới tài khoản của mình. Hãy giữ bí mật mật khẩu và báo ngay cho EcoGreen nếu nghi ngờ tài khoản bị truy cập trái phép.',
      'Bạn có thể đăng nhập bằng email và mật khẩu, hoặc bằng tài khoản Google hoặc Facebook. EcoGreen có quyền tạm khóa tài khoản có dấu hiệu gian lận hoặc vi phạm điều khoản.'
    ]
  },
  {
    title: 'Đặt hàng và thanh toán',
    paragraphs: [
      'Giá và tình trạng còn hàng hiển thị tại thời điểm đặt hàng. Đơn hàng chỉ được xác nhận khi hệ thống ghi nhận đơn hợp lệ; EcoGreen có thể hủy đơn khi sản phẩm hết hàng hoặc thông tin giao nhận không đủ để thực hiện.',
      'Thanh toán trực tuyến được xử lý qua cổng thanh toán của bên thứ ba. EcoGreen không lưu số thẻ của bạn.'
    ]
  },
  {
    title: 'Đổi trả và hoàn tiền',
    paragraphs: ['Việc đổi trả, hoàn tiền tuân theo Chính sách đổi trả của EcoGreen (xem mục Chính sách đổi trả trên website).']
  },
  {
    title: 'Hành vi bị cấm',
    paragraphs: [
      'Bạn không được dùng website để gian lận, truy cập trái phép hệ thống hoặc dữ liệu của người khác, gửi mã độc, hoặc gây gián đoạn hoạt động của website.'
    ]
  },
  {
    title: 'Thay đổi điều khoản',
    paragraphs: ['EcoGreen có thể cập nhật điều khoản này. Phiên bản mới có hiệu lực khi được đăng tải trên website; việc tiếp tục sử dụng đồng nghĩa với việc bạn chấp nhận thay đổi.']
  },
  {
    title: 'Liên hệ',
    paragraphs: ['Mọi thắc mắc về điều khoản, vui lòng liên hệ EcoGreen qua trang Liên hệ trên website.']
  }
];

const Terms = () => <LegalPage title="Điều khoản dịch vụ" updated="30/10/2026" sections={SECTIONS} />;

export default Terms;
