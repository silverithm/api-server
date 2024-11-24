package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender emailSender;
    private final String senderEmail = "ggprgrkjh2@gmail.com";

    public void sendEmail(String toEmail, String subject, String body) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);

            // HTML 템플릿 사용
            String htmlContent = createHtmlTemplate(body);
            helper.setText(htmlContent, true);

            emailSender.send(message);
        } catch (MessagingException e) {
            log.info(e.toString());
            throw new CustomException("Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (jakarta.mail.MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    private String createHtmlTemplate(String body) {
        return String.format("""
                <div style="margin:100px;">
                    <h1>임시 비밀번호 발급</h1>
                    <br>
                    <div style="background-color:#f8f9fa;padding:20px;">
                        <br>
                        <p style="font-size:130%%">임시 비밀번호 : <strong>%s</strong></p>
                        <br>
                        <p>보안을 위해 로그인 후 반드시 비밀번호를 변경해주세요.</p>
                    </div>
                </div>
                """, body);
    }

    // 비동기 이메일 전송
    @Async
    public void sendEmailAsync(String toEmail, String subject, String body) {
        sendEmail(toEmail, subject, body);
    }
}