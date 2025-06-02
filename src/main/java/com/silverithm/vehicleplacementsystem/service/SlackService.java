package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class SlackService {
    private final RestTemplate restTemplate;
    private final String paymentUrl;
    private final String signupUrl;
    private final String apiFailureUrl;

    public SlackService(@Value("${slack.webhook.payment_url}") String paymentUrl,
                        @Value("${slack.webhook.signup_url}") String signupUrl,
                        @Value("${slack.webhook.api_failure_url}") String apiFailureUrl) {
        this.paymentUrl = paymentUrl;
        this.signupUrl = signupUrl;
        this.apiFailureUrl = apiFailureUrl;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 결제 성공 알림 전송
     */
    public void sendPaymentSuccessNotification(String orderId, String customerName, double amount) {
        try {
            String message = String.format(
                    "{\"text\":\":white_check_mark: *결제 성공* :white_check_mark:\\n" +
                            "• 주문번호: %s\\n" +
                            "• 고객명: %s\\n" +
                            "• 결제금액: %.2f원\"}",
                    orderId, customerName, amount);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(message, headers);

            restTemplate.postForObject(paymentUrl, request, String.class);
        } catch (Exception e) {
            log.error("슬랙 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 회원가입 성공 알림 전송 (관리자용)
     */
    public void sendSignupSuccessNotification(String email, String userName, String companyName) {
        try {
            String message = String.format(
                    "{\"text\":\":crown: *새 관리자 가입* :crown:\\n" +
                            "• 사용자 이메일: %s\\n" +
                            "• 사용자 이름: %s\\n" +
                            "• 회사명: %s\\n" +
                            "• 가입일시: %s\"}",
                    email, userName, companyName, java.time.LocalDateTime.now());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(message, headers);

            restTemplate.postForObject(signupUrl, request, String.class);
        } catch (Exception e) {
            log.error("슬랙 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 멤버 가입 승인 알림 전송 (직원용)
     */
    public void sendMemberApprovalNotification(String email, String userName, String companyName, 
                                               String department, String position, String role) {
        try {
            String message = String.format(
                    "{\"text\":\":busts_in_silhouette: *새 직원 가입 승인* :busts_in_silhouette:\\n" +
                            "• 사용자 이메일: %s\\n" +
                            "• 사용자 이름: %s\\n" +
                            "• 소속 회사: %s\\n" +
                            "• 부서: %s\\n" +
                            "• 직급: %s\\n" +
                            "• 역할: %s\\n" +
                            "• 승인일시: %s\"}",
                    email, userName, companyName, 
                    department != null ? department : "미지정",
                    position != null ? position : "미지정",
                    getRoleDisplayName(role),
                    java.time.LocalDateTime.now());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(message, headers);

            restTemplate.postForObject(signupUrl, request, String.class);
        } catch (Exception e) {
            log.error("슬랙 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * API 요청 실패 알림 전송
     */
    public void sendApiFailureNotification(String apiName, String userEmail, String errorMessage,
                                           String requestParams) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> payload = new HashMap<>();
            payload.put("text", String.format(
                    ":x: *API 요청 실패* :x:\n" +
                            "• API: %s\n" +
                            "• 사용자 이메일: %s\n" +
                            "• 에러 메시지: %s\n" +
                            "• 요청 파라미터: %s\n" +
                            "• 발생시간: %s",
                    apiName, userEmail, errorMessage, requestParams, java.time.LocalDateTime.now()));

            String jsonPayload = mapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            restTemplate.postForObject(apiFailureUrl, request, String.class);
        } catch (Exception e) {
            log.error("슬랙 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 역할명을 한글로 변환
     */
    private String getRoleDisplayName(String role) {
        return switch (role.toUpperCase()) {
            case "CAREGIVER" -> "요양보호사";
            case "OFFICE" -> "사무직";
            case "USER" -> "일반 사용자";
            case "ADMIN" -> "관리자";
            default -> role;
        };
    }
}
