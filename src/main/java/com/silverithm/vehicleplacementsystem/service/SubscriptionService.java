package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.BillingResponse;
import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SubscriptionService {

    @Value("${toss.secret-key}")
    private String secretKey;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Transactional(timeout = 15)
    public SubscriptionResponseDTO createOrUpdateSubscription(UserDetails userDetails,
                                                              SubscriptionRequestDTO requestDto) {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(
                        () -> new CustomException("User not found with email: " + userDetails.getUsername(),
                                HttpStatus.NOT_FOUND));

        if (user.getBillingKey() == null) {
            BillingResponse billingResponse = requestBillingKey(requestDto);
            user.updateBillingKey(billingResponse.billingKey());
        }

        requestPayment(requestDto, user.getBillingKey());

        return Optional.ofNullable(user.getSubscription())
                .map(subscription -> updateSubscription(subscription, requestDto))
                .orElseGet(() -> createSubscription(requestDto, user));
    }

    public PaymentResponse requestPayment(SubscriptionRequestDTO requestDto, String billingKey) {

        try {
            // Base64 인코딩
            String encodedAuth = Base64.getEncoder()
                    .encodeToString((secretKey + ":").getBytes());

            // HTTP 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedAuth);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 요청 바디 생성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("customerKey", requestDto.getCustomerKey());
            requestBody.put("amount", requestDto.getAmount());
            requestBody.put("orderId", UUID.randomUUID().toString());
            requestBody.put("orderName", requestDto.getOrderName());
            requestBody.put("customerEmail", requestDto.getCustomerEmail());
            requestBody.put("customerName", requestDto.getCustomerName());
            requestBody.put("taxFreeAmount", requestDto.getTaxFreeAmount());

            // HTTP 엔티티 생성
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 토스 API 호출
            ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                    "https://api.tosspayments.com/v1/billing/" + billingKey,
                    HttpMethod.POST,
                    entity,
                    PaymentResponse.class
            );

            return response.getBody();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new CustomException("토스 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE);
            }
            throw new CustomException("결제 실패: " + e.getResponseBodyAsString(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            throw new CustomException("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public BillingResponse requestBillingKey(SubscriptionRequestDTO requestDto) {
        try {
            // Base64 인코딩
            String encodedAuth = Base64.getEncoder()
                    .encodeToString((secretKey + ":").getBytes());

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
                    "https://api.tosspayments.com/v1/billing/authorizations/issue",
                    HttpMethod.POST,
                    entity,
                    BillingResponse.class
            );

            log.info("빌링키 발급 결과" + response.getBody().billingKey());
            log.info("빌링키 발급 결과" + response.getBody().toString());

            return response.getBody();

        } catch (HttpClientErrorException e) {
            throw new CustomException("빌링키 발급 실패: " + e.getResponseBodyAsString(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private SubscriptionResponseDTO updateSubscription(Subscription subscription, SubscriptionRequestDTO requestDto) {
        LocalDateTime endDate = calculateEndDate(requestDto.getBillingType());
        subscription.update(requestDto.getPlanName(), requestDto.getBillingType(),
                requestDto.getAmount(), endDate, SubscriptionStatus.ACTIVE);
        return new SubscriptionResponseDTO(subscription);
    }

    public SubscriptionResponseDTO createSubscription(SubscriptionRequestDTO requestDto, AppUser user) {
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(requestDto.getBillingType());

        Subscription subscription = Subscription.builder()
                .planName(requestDto.getPlanName())
                .billingType(requestDto.getBillingType())
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .amount(requestDto.getAmount())
                .user(user)
                .build();

        return new SubscriptionResponseDTO(subscriptionRepository.save(subscription));
    }

    @Transactional
    public SubscriptionResponseDTO createSubscriptionToUser(SubscriptionRequestDTO requestDto,
                                                            Long userId) {

        AppUser user = userRepository.findById(userId)
                .orElseThrow(
                        () -> new IllegalArgumentException("User not found with userId: " + userId));

        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(requestDto.getBillingType());

        Subscription subscription = Subscription.builder()
                .planName(requestDto.getPlanName())
                .billingType(requestDto.getBillingType())
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .amount(requestDto.getAmount())
                .user(user)
                .build();

        return new SubscriptionResponseDTO(subscriptionRepository.save(subscription));
    }

    private LocalDateTime calculateEndDate(SubscriptionBillingType billingType) {
        return billingType == SubscriptionBillingType.MONTHLY ?
                LocalDateTime.now().plusMonths(1) : LocalDateTime.now().plusYears(1);
    }

    public SubscriptionResponseDTO getSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));
        return new SubscriptionResponseDTO(subscription);
    }

    @Transactional
    public SubscriptionResponseDTO cancelSubscription(UserDetails userDetails, Long id) {

        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));

        if (!subscription.getUser().getEmail().equals(userDetails.getUsername())) {
            throw new IllegalArgumentException(
                    "User " + userDetails.getUsername() + " is not authorized to access subscription: " + id);
        }

        subscription.updateStatus(SubscriptionStatus.CANCELLED);
        subscription.updateEndDate(LocalDateTime.now());

        return new SubscriptionResponseDTO(subscription);
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptions() {
        return subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE).stream()
                .map(SubscriptionResponseDTO::new)
                .collect(Collectors.toList());
    }
}