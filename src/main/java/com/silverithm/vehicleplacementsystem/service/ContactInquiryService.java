package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.ContactInquiryRequest;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 공개 페이지 문의 접수.
 *
 * <p>기존에는 프론트가 {@code mailto:}로 사용자의 메일 앱을 열어, 방문자가 직접 보내기를
 * 눌러야 접수됐다. 메일 앱이 없으면 아예 보낼 수 없었다. 서버에서 바로 발송한다.
 *
 * <p>비로그인 공개 엔드포인트라 스팸 발송에 악용될 수 있어 IP 단위로 짧게 제한한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactInquiryService {

    /** 같은 IP에서 이 시간(분) 안에는 한 번만 접수한다. */
    private static final int RATE_LIMIT_MINUTES = 1;

    private static final String RATE_LIMIT_PREFIX = "contact:inquiry:";

    private final JavaMailSender emailSender;
    private final RedisUtils redisUtils;

    @Value("${contact.inquiry.recipient:ggprgrkjh2@gmail.com}")
    private String recipient;

    @Value("${contact.inquiry.sender:ggprgrkjh2@gmail.com}")
    private String sender;

    public void submit(ContactInquiryRequest request, String clientIp) {
        requireNotRateLimited(clientIp);

        String subject = "[케어브이 문의] %s - %s".formatted(
                sanitizeHeader(defaultIfBlank(request.inquiryType(), "일반 문의")),
                sanitizeHeader(request.name()));

        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(recipient);
            // 답장하면 문의자에게 바로 가도록
            helper.setReplyTo(request.email());
            helper.setSubject(subject);
            helper.setText(buildHtml(request), true);

            emailSender.send(message);
        } catch (Exception e) {
            log.error("[Contact] 문의 메일 발송 실패: {}", e.getMessage());
            throw new CustomException("문의 접수에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        markSubmitted(clientIp);
        log.info("[Contact] 문의 접수: 유형={}, 문의자={}", request.inquiryType(),
                PrivacyMask.email(request.email()));
    }

    private void requireNotRateLimited(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        try {
            if (redisUtils.get(RATE_LIMIT_PREFIX + clientIp) != null) {
                throw new CustomException("문의가 접수되었습니다. 잠시 후 다시 시도해주세요.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // Redis 장애로 문의 접수 자체가 막히면 안 된다.
            log.warn("[Contact] 접수 제한 조회 실패(무시): {}", e.getMessage());
        }
    }

    private void markSubmitted(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        try {
            redisUtils.set(RATE_LIMIT_PREFIX + clientIp, "1", RATE_LIMIT_MINUTES);
        } catch (Exception e) {
            log.warn("[Contact] 접수 제한 기록 실패(무시): {}", e.getMessage());
        }
    }

    /** 메일 제목에 개행이 들어가면 헤더가 조작될 수 있다. */
    private String sanitizeHeader(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", " ").trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String buildHtml(ContactInquiryRequest r) {
        return """
                <div style="font-family:sans-serif;line-height:1.6;">
                  <h2 style="margin:0 0 16px;">케어브이 문의</h2>
                  <table style="border-collapse:collapse;margin-bottom:16px;">
                    <tr><td style="padding:4px 12px 4px 0;color:#666;">이름</td><td>%s</td></tr>
                    <tr><td style="padding:4px 12px 4px 0;color:#666;">이메일</td><td>%s</td></tr>
                    <tr><td style="padding:4px 12px 4px 0;color:#666;">기관명</td><td>%s</td></tr>
                    <tr><td style="padding:4px 12px 4px 0;color:#666;">연락처</td><td>%s</td></tr>
                    <tr><td style="padding:4px 12px 4px 0;color:#666;">문의 유형</td><td>%s</td></tr>
                  </table>
                  <div style="background:#f8f9fa;padding:16px;white-space:pre-wrap;">%s</div>
                </div>
                """.formatted(
                escape(r.name()),
                escape(r.email()),
                escape(defaultIfBlank(r.organization(), "-")),
                escape(defaultIfBlank(r.phone(), "-")),
                escape(defaultIfBlank(r.inquiryType(), "-")),
                escape(r.message()));
    }

    /** 입력값이 그대로 HTML 메일에 들어가므로 태그를 무력화한다. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
