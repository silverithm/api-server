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
            throw new CustomException("이메일 전송에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
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

    /** 비밀번호 재설정 링크 메일 — 링크를 누르기 전에는 아무것도 바뀌지 않는다 */
    public void sendPasswordResetEmail(String toEmail, String link, int validMinutes) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("케어브이 비밀번호 재설정 안내");
            helper.setText(String.format("""
                    <div style="margin:40px auto;max-width:480px;font-family:sans-serif;">
                        <h2>비밀번호 재설정</h2>
                        <p>아래 버튼을 눌러 새 비밀번호를 정해 주세요. 링크는 %d분 동안만 쓸 수 있고, 한 번 쓰면 사라집니다.</p>
                        <p style="margin:24px 0"><a href="%s" style="background:#0f766e;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none">새 비밀번호 정하기</a></p>
                        <p style="color:#666;font-size:13px">직접 요청하지 않으셨다면 이 메일은 무시하셔도 됩니다. 링크를 누르기 전에는 비밀번호가 바뀌지 않습니다.</p>
                    </div>
                    """, validMinutes, link), true);
            emailSender.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new CustomException("이메일 전송에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 비동기 이메일 전송
    @Async
    public void sendEmailAsync(String toEmail, String subject, String body) {
        sendEmail(toEmail, subject, body);
    }

    // 회원가입 승인 이메일 전송
    public void sendJoinApprovalEmail(String toEmail, String userName, String companyName) {
        try {
            String subject = "🎉 회원가입 승인 완료 - " + companyName;
            String htmlContent = createJoinApprovalTemplate(userName, companyName);
            
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            emailSender.send(message);
            
            log.info("[Email Service] 가입 승인 이메일 전송 완료: {}", toEmail);
        } catch (Exception e) {
            log.error("[Email Service] 가입 승인 이메일 전송 실패: {}", e.getMessage());
            throw new CustomException("이메일 전송 실패", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    // 회원가입 거부 이메일 전송
    public void sendJoinRejectionEmail(String toEmail, String userName, String companyName, String rejectReason) {
        try {
            String subject = "❌ 회원가입 거부 알림 - " + companyName;
            String htmlContent = createJoinRejectionTemplate(userName, companyName, rejectReason);
            
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            emailSender.send(message);
            
            log.info("[Email Service] 가입 거부 이메일 전송 완료: {}", toEmail);
        } catch (Exception e) {
            log.error("[Email Service] 가입 거부 이메일 전송 실패: {}", e.getMessage());
            throw new CustomException("이메일 전송 실패", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    // 가입 승인 HTML 템플릿
    private String createJoinApprovalTemplate(String userName, String companyName) {
        return String.format("""
                <div style="max-width: 600px; margin: 0 auto; font-family: Arial, sans-serif;">
                    <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                        <h1 style="margin: 0; font-size: 28px;">🎉 회원가입 승인!</h1>
                    </div>
                    
                    <div style="background-color: #ffffff; padding: 40px; border-radius: 0 0 10px 10px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
                        <h2 style="color: #333; margin-bottom: 20px;">안녕하세요, %s님!</h2>
                        
                        <div style="background-color: #f8f9fa; padding: 25px; border-radius: 8px; border-left: 4px solid #28a745; margin: 20px 0;">
                            <p style="font-size: 16px; line-height: 1.6; margin: 0; color: #333;">
                                <strong>%s</strong>에서의 회원가입이 <span style="color: #28a745; font-weight: bold;">승인</span>되었습니다!
                            </p>
                        </div>
                        
                        <div style="margin: 30px 0;">
                            <h3 style="color: #333; margin-bottom: 15px;">다음 단계</h3>
                            <ul style="color: #666; line-height: 1.8;">
                                <li>가입하신 계정으로 로그인하실 수 있습니다</li>
                                <li>서비스 이용 중 문의사항이 있으시면 관리자에게 연락해주세요</li>
                            </ul>
                        </div>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <div style="background-color: #e3f2fd; padding: 20px; border-radius: 8px;">
                                <p style="margin: 0; color: #1976d2; font-weight: bold;">
                                    환영합니다! 좋은 서비스를 제공하겠습니다.
                                </p>
                            </div>
                        </div>
                        
                        <hr style="border: none; height: 1px; background-color: #eee; margin: 30px 0;">
                        
                        <p style="color: #999; font-size: 14px; text-align: center; margin: 0;">
                            이 메일은 자동 발송된 메일입니다. 회신하지 마세요.<br>
                            © 2024 Silverithm. All rights reserved.
                        </p>
                    </div>
                </div>
                """, userName, companyName);
    }
    
    // 가입 거부 HTML 템플릿
    private String createJoinRejectionTemplate(String userName, String companyName, String rejectReason) {
        return String.format("""
                <div style="max-width: 600px; margin: 0 auto; font-family: Arial, sans-serif;">
                    <div style="background: linear-gradient(135deg, #ff6b6b 0%%, #ee5a24 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                        <h1 style="margin: 0; font-size: 28px;">❌ 회원가입 거부</h1>
                    </div>
                    
                    <div style="background-color: #ffffff; padding: 40px; border-radius: 0 0 10px 10px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
                        <h2 style="color: #333; margin-bottom: 20px;">안녕하세요, %s님</h2>
                        
                        <div style="background-color: #fff5f5; padding: 25px; border-radius: 8px; border-left: 4px solid #e53e3e; margin: 20px 0;">
                            <p style="font-size: 16px; line-height: 1.6; margin: 0; color: #333;">
                                죄송합니다. <strong>%s</strong>에서의 회원가입이 <span style="color: #e53e3e; font-weight: bold;">거부</span>되었습니다.
                            </p>
                        </div>
                        
                        %s
                        
                        <div style="margin: 30px 0;">
                            <h3 style="color: #333; margin-bottom: 15px;">추가 문의</h3>
                            <p style="color: #666; line-height: 1.6;">
                                가입 거부 사유에 대해 문의사항이 있으시거나 재신청을 원하시는 경우, 
                                회사 관리자에게 직접 연락해주시기 바랍니다.
                            </p>
                        </div>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <div style="background-color: #fff3cd; padding: 20px; border-radius: 8px; border: 1px solid #ffeaa7;">
                                <p style="margin: 0; color: #856404; font-weight: bold;">
                                    다시 신청해주세요! 언제든 재신청 가능합니다.
                                </p>
                            </div>
                        </div>
                        
                        <hr style="border: none; height: 1px; background-color: #eee; margin: 30px 0;">
                        
                        <p style="color: #999; font-size: 14px; text-align: center; margin: 0;">
                            이 메일은 자동 발송된 메일입니다. 회신하지 마세요.<br>
                            © 2024 Silverithm. All rights reserved.
                        </p>
                    </div>
                </div>
                """, userName, companyName, 
                rejectReason != null && !rejectReason.trim().isEmpty() 
                    ? String.format("""
                        <div style="margin: 20px 0;">
                            <h3 style="color: #333; margin-bottom: 15px;">거부 사유</h3>
                            <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; border: 1px solid #dee2e6;">
                                <p style="color: #495057; line-height: 1.6; margin: 0;">%s</p>
                            </div>
                        </div>
                        """, rejectReason)
                    : "");
    }
}