package com.example.backend.service;

import com.example.backend.config.NotificationAsyncConfig;
import com.example.backend.service.notification.EmailMessage;
import com.example.backend.service.notification.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Account e-mails (password reset). Calls {@link EmailSender} directly and NOT NotificationService on purpose:
 * NotificationService stores the title and body in the {@code notifications} table, which would put a live reset link in the
 * database. Runs on the B10 notification executor so a slow SMTP server never holds the request and the response time does not
 * reveal whether the address belongs to an account. Best effort: a failure is logged (user id only) and never reaches the caller.
 */
@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    @Autowired private EmailSender emailSender;

    @Async(NotificationAsyncConfig.EXECUTOR)
    public void sendPasswordReset(Long userId, String to, String resetLink, long ttlMinutes) {
        String subject = "EcoGreen - Đặt lại mật khẩu";
        String text = "Xin chào,\n\n"
                + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản EcoGreen của bạn.\n"
                + "Mở liên kết sau để đặt mật khẩu mới (có hiệu lực trong " + ttlMinutes + " phút, chỉ dùng được một lần):\n\n"
                + resetLink + "\n\n"
                + "Nếu bạn không yêu cầu, hãy bỏ qua email này - mật khẩu của bạn không thay đổi.\n\n"
                + "EcoGreen";
        String html = "<p>Xin chào,</p>"
                + "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản EcoGreen của bạn.</p>"
                + "<p><a href=\"" + resetLink + "\">Đặt mật khẩu mới</a><br>"
                + "Liên kết có hiệu lực trong " + ttlMinutes + " phút và chỉ dùng được một lần.</p>"
                + "<p>Nếu bạn không yêu cầu, hãy bỏ qua email này - mật khẩu của bạn không thay đổi.</p>"
                + "<p>EcoGreen</p>";
        try {
            // type = null: there is no NotificationType for account mail and the enum belongs to B10 (its renderer switches on it).
            EmailSender.Delivery delivery = emailSender.send(new EmailMessage(null, userId, to, subject, text, html));
            if (delivery == EmailSender.Delivery.NOT_CONFIGURED) {
                log.info("Password reset email not sent for user {}: SMTP_HOST is empty", userId);
            }
        } catch (RuntimeException e) {
            log.warn("Password reset email failed for user {}: {}", userId, e.getClass().getSimpleName());
        }
    }
}
