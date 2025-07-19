package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;
    private final BillingService billingService;
    private final UserRepository userRepository;
    private final SlackService slackService;

    public SubscriptionScheduler(SubscriptionService subscriptionService, BillingService billingService,
                                 UserRepository userRepository, SlackService slackService) {
        this.subscriptionService = subscriptionService;
        this.billingService = billingService;
        this.userRepository = userRepository;
        this.slackService = slackService;
    }


    @Scheduled(cron = "0 0 6 * * *")
    public void processScheduledPayments() {
        LocalDateTime currentDate = LocalDateTime.now();
        log.info("🔄 스케줄러 실행됨 - 현재 시간: {}", currentDate);
        
        List<AppUser> users = userRepository.findUsersRequiringSubscriptionBilling(currentDate);
        log.info("🔍 결제 대상 유저 수: {}", users.size());

        int successCount = 0;
        int failureCount = 0;

        for (AppUser user : users) {
            try {
                if (user.isEmptyBillingKey()) {
                    log.warn("⚠️ 빌링키가 없는 사용자 스킵: {}", user.getUsername());
                    failureCount++;
                    continue;
                }

                SubscriptionRequestDTO requestDto = SubscriptionRequestDTO.of(
                        user.getSubscription().getPlanName(),
                        user.getSubscription().getBillingType(),
                        user.getSubscription().getAmount(),
                        user.getCustomerKey(),
                        user.getBillingKey(),
                        user.getSubscription().getPlanName().name() + "_" + user.getSubscription().getBillingType().name(),
                        user.getEmail(),
                        user.getUsername(),
                        0
                );

                PaymentResponse paymentResponse = billingService.requestPayment(requestDto, user.getBillingKey());

                if (paymentResponse.status().equals("DONE")) {
                    log.info("✅ 스케줄링 결제 성공: {} (금액: {}원)", user.getUsername(), requestDto.getAmount());
                    subscriptionService.processSubscription(user, requestDto);
                    successCount++;
                } else {
                    log.error("❌ 스케줄링 결제 실패: {} - 상태: {}", user.getUsername(), paymentResponse.status());
                    handlePaymentFailure(user, paymentResponse);
                    failureCount++;
                }
            } catch (Exception e) {
                log.error("💥 스케줄링 결제 처리 중 예외 발생: {} - 에러: {}", user.getUsername(), e.getMessage(), e);
                handlePaymentException(user, e);
                failureCount++;
            }
        }

        log.info("📊 스케줄링 결제 완료 - 성공: {}건, 실패: {}건", successCount, failureCount);
    }

    private void handlePaymentFailure(AppUser user, PaymentResponse paymentResponse) {
        try {
            Map<String, Object> slackData = new HashMap<>();
            slackData.put("username", user.getUsername());
            slackData.put("email", user.getEmail());
            slackData.put("status", paymentResponse.status());
            String failureCode = "UNKNOWN";
            String failureMessage = "알 수 없는 오류";
            
            if (paymentResponse.failure() != null && paymentResponse.failure() instanceof Map) {
                Map<String, Object> failureMap = (Map<String, Object>) paymentResponse.failure();
                failureCode = (String) failureMap.getOrDefault("code", "UNKNOWN");
                failureMessage = (String) failureMap.getOrDefault("message", "알 수 없는 오류");
            }
            
            slackData.put("failure_code", failureCode);
            slackData.put("failure_message", failureMessage);
            
            String message = String.format("🚨 정기결제 실패 알림\n사용자: %s\n이메일: %s\n실패 코드: %s\n실패 사유: %s", 
                    user.getUsername(), user.getEmail(), 
                    slackData.get("failure_code"), slackData.get("failure_message"));
            
            slackService.sendSlackMessage(message);
            log.warn("⚠️ 결제 실패 알림 전송: {}", user.getUsername());
        } catch (Exception e) {
            log.error("슬랙 알림 전송 실패: {}", e.getMessage());
        }
    }

    private void handlePaymentException(AppUser user, Exception e) {
        try {
            String message = String.format("💥 정기결제 시스템 오류\n사용자: %s\n이메일: %s\n오류: %s", 
                    user.getUsername(), user.getEmail(), e.getMessage());
            
            slackService.sendSlackMessage(message);
            log.error("💥 결제 예외 알림 전송: {}", user.getUsername());
        } catch (Exception slackException) {
            log.error("슬랙 알림 전송 실패: {}", slackException.getMessage());
        }
    }
}