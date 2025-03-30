package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.BillingResponse;
import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
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

    @Transactional
    public void ensureBillingKey(AppUser user, SubscriptionRequestDTO requestDto) {
        BillingResponse billingResponse = requestBillingKey(requestDto);
        user.updateBillingKey(billingResponse.billingKey());

        log.info(billingResponse.toString() + " " + user.getUsername());
        log.info(user.getBillingKey() + " " + user.getUsername());
    }


    public void processPayment(SubscriptionRequestDTO requestDto, String billingKey) {
        requestPayment(requestDto, billingKey);
        log.info("결제 성공" + requestDto.getCustomerName());
    }

    public PaymentResponse requestPayment(SubscriptionRequestDTO requestDto, String billingKey) {

        try {
            // Base64 인코딩
            String encodedAuth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
            String orderId = UUID.randomUUID().toString();

            // HTTP 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedAuth);
            headers.setContentType(MediaType.APPLICATION_JSON);

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

            slackService.sendPaymentSuccessNotification(orderId, requestDto.getCustomerName(), requestDto.getAmount());

            return response.getBody();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new CustomException("토스 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE);
            }
            slackService.sendApiFailureNotification("결제 실패", requestDto.getCustomerEmail(), e.getResponseBodyAsString(),
                    requestDto.toString());
            throw new CustomException("결제 실패: " + e.getResponseBodyAsString(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            slackService.sendApiFailureNotification("결제 실패", requestDto.getCustomerEmail(), e.getMessage().toString(),
                    requestDto.toString());
            throw new CustomException("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
            throw new CustomException("빌링키 발급 실패: " + e.getResponseBodyAsString(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}