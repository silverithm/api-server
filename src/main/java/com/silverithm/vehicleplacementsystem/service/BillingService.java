package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.BillingResponse;
import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    @Value("${toss.secret-key}")
    private String secretKey;

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final SlackService slackService;
    private final PaymentFailureService paymentFailureService;
    private final BillingKeyEncryptionService billingKeyEncryptionService;

    @Transactional
    public void ensureBillingKey(AppUser user, SubscriptionRequestDTO requestDto) {
        BillingResponse billingResponse = requestBillingKey(requestDto);
        String encryptedBillingKey = billingKeyEncryptionService.encryptBillingKey(billingResponse.billingKey());
        user.updateBillingKey(encryptedBillingKey);

        log.info("빌링키 발급 및 암호화 완료 - 사용자: {}", user.getUsername());
    }


    public void processPayment(SubscriptionRequestDTO requestDto, String encryptedBillingKey) {
        String decryptedBillingKey = billingKeyEncryptionService.decryptBillingKey(encryptedBillingKey);
        requestPayment(requestDto, decryptedBillingKey);
        log.info("결제 성공" + requestDto.getCustomerName());
    }

    public PaymentResponse requestPayment(SubscriptionRequestDTO requestDto, String billingKey) {
        return requestPaymentWithRetry(requestDto, billingKey, 3);
    }

    private PaymentResponse requestPaymentWithRetry(SubscriptionRequestDTO requestDto, String billingKey, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            attempts++;
            try {
                log.info("💳 결제 시도 ({}/{}) - 사용자: {}, 금액: {}원", attempts, maxRetries, requestDto.getCustomerName(), requestDto.getAmount());
                
                // 빌링키 유효성 검증
                if (billingKey == null || billingKey.trim().isEmpty()) {
                    throw new CustomException("유효하지 않은 빌링키입니다.", HttpStatus.BAD_REQUEST);
                }

                // Base64 인코딩
                String encodedAuth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
                String orderId = UUID.randomUUID().toString();

                // HTTP 요청 헤더 설정
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Basic " + encodedAuth);
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Idempotency-Key", orderId);

                // 요청 바디 생성
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("customerKey", requestDto.getCustomerKey());
                requestBody.put("amount", requestDto.getAmount());
                requestBody.put("orderId", orderId);
                requestBody.put("orderName", requestDto.getOrderName());
                requestBody.put("customerEmail", requestDto.getCustomerEmail());
                requestBody.put("customerName", requestDto.getCustomerName());
                requestBody.put("taxFreeAmount", requestDto.getTaxFreeAmount());

                // HTTP 엔티티 생성
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                // 토스 API 호출
                ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                        "https://api.tosspayments.com/v1/billing/" + billingKey, HttpMethod.POST, entity,
                        PaymentResponse.class);

                PaymentResponse paymentResponse = response.getBody();
                
                if (paymentResponse != null && "DONE".equals(paymentResponse.status())) {
                    log.info("✅ 결제 성공 - 주문ID: {}, 사용자: {}, 금액: {}원", orderId, requestDto.getCustomerName(), requestDto.getAmount());
                    slackService.sendPaymentSuccessNotification(orderId, requestDto.getCustomerName(), requestDto.getAmount());
                    return paymentResponse;
                } else {
                    log.warn("⚠️ 결제 응답 상태 이상 - 상태: {}, 사용자: {}", 
                            paymentResponse != null ? paymentResponse.status() : "NULL", requestDto.getCustomerName());
                    return paymentResponse;
                }

            } catch (HttpClientErrorException e) {
                lastException = e;
                log.error("❌ 결제 API 오류 ({}/{}) - 상태코드: {}, 사용자: {}, 응답: {}", 
                        attempts, maxRetries, e.getStatusCode(), requestDto.getCustomerName(), e.getResponseBodyAsString());
                
                if (e.getStatusCode().is4xxClientError()) {
                    // 4xx 에러는 재시도하지 않음
                    PaymentFailureReason reason = determineFailureReason(e.getResponseBodyAsString());
                    paymentFailureService.savePaymentFailure(
                        requestDto.getCustomerEmail(), null, reason,
                        e.getResponseBodyAsString(), requestDto.getAmount(),
                        requestDto.getPlanName(), requestDto.getBillingType(),
                        e.getResponseBodyAsString()
                    );
                    slackService.sendApiFailureNotification("결제 실패 (클라이언트 오류)", requestDto.getCustomerEmail(), 
                            e.getResponseBodyAsString(), requestDto.toString());
                    throw new CustomException("결제 실패: " + e.getResponseBodyAsString(), HttpStatus.BAD_REQUEST);
                }
                
                if (e.getStatusCode().is5xxServerError() && attempts < maxRetries) {
                    // 5xx 에러는 재시도
                    log.warn("🔄 서버 오류로 인한 재시도 대기 중... ({}/{})", attempts, maxRetries);
                    try {
                        Thread.sleep(1000 * attempts); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new CustomException("결제 처리가 중단되었습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                } else {
                    paymentFailureService.savePaymentFailure(
                        requestDto.getCustomerEmail(), null, PaymentFailureReason.PAYMENT_GATEWAY_ERROR,
                        e.getResponseBodyAsString(), requestDto.getAmount(),
                        requestDto.getPlanName(), requestDto.getBillingType(),
                        e.getResponseBodyAsString()
                    );
                    slackService.sendApiFailureNotification("결제 실패 (서버 오류)", requestDto.getCustomerEmail(), 
                            e.getResponseBodyAsString(), requestDto.toString());
                    throw new CustomException("토스 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE);
                }
                
            } catch (Exception e) {
                lastException = e;
                log.error("💥 결제 처리 중 예외 발생 ({}/{}) - 사용자: {}, 오류: {}", 
                        attempts, maxRetries, requestDto.getCustomerName(), e.getMessage(), e);
                
                if (attempts < maxRetries) {
                    log.warn("🔄 예외로 인한 재시도 대기 중... ({}/{})", attempts, maxRetries);
                    try {
                        Thread.sleep(1000 * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new CustomException("결제 처리가 중단되었습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                } else {
                    paymentFailureService.savePaymentFailure(
                        requestDto.getCustomerEmail(), null, PaymentFailureReason.OTHER,
                        e.getMessage(), requestDto.getAmount(),
                        requestDto.getPlanName(), requestDto.getBillingType(),
                        "시스템 오류: " + e.getMessage()
                    );
                    slackService.sendApiFailureNotification("결제 실패 (시스템 오류)", requestDto.getCustomerEmail(), 
                            e.getMessage(), requestDto.toString());
                    throw new CustomException("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }

        // 모든 재시도 실패
        log.error("💥 모든 결제 재시도 실패 - 사용자: {}", requestDto.getCustomerName());
        paymentFailureService.savePaymentFailure(
            requestDto.getCustomerEmail(), null, PaymentFailureReason.OTHER,
            "모든 재시도 실패", requestDto.getAmount(),
            requestDto.getPlanName(), requestDto.getBillingType(),
            lastException != null ? lastException.getMessage() : "알 수 없는 오류"
        );
        slackService.sendApiFailureNotification("결제 최종 실패", requestDto.getCustomerEmail(), 
                lastException != null ? lastException.getMessage() : "알 수 없는 오류", requestDto.toString());
        throw new CustomException("결제 처리에 실패했습니다. 고객센터에 문의해주세요.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public BillingResponse requestBillingKey(SubscriptionRequestDTO requestDto) {
        try {
            // Base64 인코딩
            String encodedAuth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

            // HTTP 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedAuth);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 요청 바디 생성
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("authKey", requestDto.getAuthKey());
            requestBody.put("customerKey", requestDto.getCustomerKey());

            // HTTP 엔티티 생성
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            // 토스 API 호출
            ResponseEntity<BillingResponse> response = restTemplate.exchange(
                    "https://api.tosspayments.com/v1/billing/authorizations/issue", HttpMethod.POST, entity,
                    BillingResponse.class);

            log.info("빌링키 발급 결과" + response.getBody().billingKey());

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("❌ 빌링키 발급 실패 - 사용자: {}, 상태코드: {}, 응답: {}", 
                    requestDto.getCustomerEmail(), e.getStatusCode(), e.getResponseBodyAsString());
            
            PaymentFailureReason reason = determineFailureReason(e.getResponseBodyAsString());
            paymentFailureService.savePaymentFailure(
                requestDto.getCustomerEmail(), null, reason,
                "빌링키 발급 실패: " + e.getResponseBodyAsString(), 0,
                requestDto.getPlanName(), requestDto.getBillingType(),
                e.getResponseBodyAsString()
            );
            
            slackService.sendApiFailureNotification("빌링키 발급 실패", requestDto.getCustomerEmail(), 
                    e.getResponseBodyAsString(), requestDto.toString());
            
            throw new CustomException("빌링키 발급 실패: " + e.getResponseBodyAsString(), HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("💥 빌링키 발급 중 예외 발생 - 사용자: {}, 오류: {}", 
                    requestDto.getCustomerEmail(), e.getMessage(), e);
            
            paymentFailureService.savePaymentFailure(
                requestDto.getCustomerEmail(), null, PaymentFailureReason.OTHER,
                "빌링키 발급 시스템 오류: " + e.getMessage(), 0,
                requestDto.getPlanName(), requestDto.getBillingType(),
                "시스템 오류: " + e.getMessage()
            );
            
            slackService.sendApiFailureNotification("빌링키 발급 시스템 오류", requestDto.getCustomerEmail(), 
                    e.getMessage(), requestDto.toString());
            
            throw new CustomException("빌링키 발급 중 서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private PaymentFailureReason determineFailureReason(String errorResponse) {
        if (errorResponse == null) {
            return PaymentFailureReason.OTHER;
        }
        
        String lowerResponse = errorResponse.toLowerCase();
        
        if (lowerResponse.contains("limit") || lowerResponse.contains("한도")) {
            return PaymentFailureReason.CARD_LIMIT_EXCEEDED;
        } else if (lowerResponse.contains("suspend") || lowerResponse.contains("정지") || lowerResponse.contains("차단")) {
            return PaymentFailureReason.CARD_SUSPENDED;
        } else if (lowerResponse.contains("balance") || lowerResponse.contains("잔액") || lowerResponse.contains("부족")) {
            return PaymentFailureReason.INSUFFICIENT_BALANCE;
        } else if (lowerResponse.contains("invalid") || lowerResponse.contains("유효하지") || lowerResponse.contains("올바르지")) {
            return PaymentFailureReason.INVALID_CARD;
        } else if (lowerResponse.contains("expired") || lowerResponse.contains("만료")) {
            return PaymentFailureReason.EXPIRED_CARD;
        } else if (lowerResponse.contains("network") || lowerResponse.contains("네트워크") || lowerResponse.contains("통신")) {
            return PaymentFailureReason.NETWORK_ERROR;
        } else {
            return PaymentFailureReason.PAYMENT_GATEWAY_ERROR;
        }
    }

}