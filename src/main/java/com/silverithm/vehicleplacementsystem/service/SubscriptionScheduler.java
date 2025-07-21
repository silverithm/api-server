package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.repository.PaymentFailureLogRepository;
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
    private final PaymentFailureService paymentFailureService;
    private final PaymentFailureLogRepository paymentFailureLogRepository;
    private final BillingKeyEncryptionService billingKeyEncryptionService;

    public SubscriptionScheduler(SubscriptionService subscriptionService, BillingService billingService,
                                 UserRepository userRepository, SlackService slackService, 
                                 PaymentFailureService paymentFailureService, PaymentFailureLogRepository paymentFailureLogRepository,
                                 BillingKeyEncryptionService billingKeyEncryptionService) {
        this.subscriptionService = subscriptionService;
        this.billingService = billingService;
        this.userRepository = userRepository;
        this.slackService = slackService;
        this.paymentFailureService = paymentFailureService;
        this.paymentFailureLogRepository = paymentFailureLogRepository;
        this.billingKeyEncryptionService = billingKeyEncryptionService;
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

                // 구독 상태 확인 - ACTIVE가 아닌 경우 스킵
                if (user.getSubscription() == null || !user.getSubscription().isActivated()) {
                    log.warn("⚠️ 비활성화된 구독을 가진 사용자 스킵: {} (상태: {})", 
                            user.getUsername(), 
                            user.getSubscription() != null ? user.getSubscription().getStatus() : "NULL");
                    continue;
                }

                String decryptedBillingKey = billingKeyEncryptionService.decryptBillingKey(user.getBillingKey());
                
                SubscriptionRequestDTO requestDto = SubscriptionRequestDTO.of(
                        user.getSubscription().getPlanName(),
                        user.getSubscription().getBillingType(),
                        user.getSubscription().getAmount(),
                        user.getCustomerKey(),
                        decryptedBillingKey,
                        user.getSubscription().getPlanName().name() + "_" + user.getSubscription().getBillingType().name(),
                        user.getEmail(),
                        user.getUsername(),
                        0
                );

                PaymentResponse paymentResponse = billingService.requestPayment(requestDto, decryptedBillingKey);

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
            
            // PaymentFailureLog 저장
            PaymentFailureReason reason = determineFailureReasonFromCode(failureCode);
            paymentFailureService.savePaymentFailure(
                user, user.getSubscription().getId(), reason,
                failureMessage, user.getSubscription().getAmount(),
                user.getSubscription().getPlanName(), user.getSubscription().getBillingType(),
                String.format("Scheduled payment failure - Code: %s", failureCode), true
            );
            
            // 연속 실패 검사 및 구독 비활성화
            checkConsecutiveFailuresAndDeactivate(user, reason);
            
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
            // PaymentFailureLog 저장
            paymentFailureService.savePaymentFailure(
                user, user.getSubscription().getId(), PaymentFailureReason.OTHER,
                e.getMessage(), user.getSubscription().getAmount(),
                user.getSubscription().getPlanName(), user.getSubscription().getBillingType(),
                String.format("Scheduled payment exception: %s", e.getClass().getSimpleName()), true
            );
            
            // 연속 실패 검사 및 구독 비활성화
            checkConsecutiveFailuresAndDeactivate(user, PaymentFailureReason.OTHER);
            
            String message = String.format("💥 정기결제 시스템 오류\n사용자: %s\n이메일: %s\n오류: %s", 
                    user.getUsername(), user.getEmail(), e.getMessage());
            
            slackService.sendSlackMessage(message);
            log.error("💥 결제 예외 알림 전송: {}", user.getUsername());
        } catch (Exception slackException) {
            log.error("슬랙 알림 전송 실패: {}", slackException.getMessage());
        }
    }
    
    private PaymentFailureReason determineFailureReasonFromCode(String failureCode) {
        if (failureCode == null) {
            return PaymentFailureReason.OTHER;
        }
        
        switch (failureCode.toUpperCase()) {
            case "EXCEED_MAX_CARD_LIMIT":
            case "EXCEED_DAILY_LIMIT":
            case "EXCEED_MONTHLY_LIMIT":
                return PaymentFailureReason.CARD_LIMIT_EXCEEDED;
            case "INVALID_CARD":
            case "NOT_FOUND_PAYMENT_SESSION":
                return PaymentFailureReason.INVALID_CARD;
            case "EXPIRED_CARD":
                return PaymentFailureReason.EXPIRED_CARD;
            case "INSUFFICIENT_FUNDS":
            case "INSUFFICIENT_BALANCE":
                return PaymentFailureReason.INSUFFICIENT_BALANCE;
            case "REJECT_CARD_COMPANY":
            case "FORBIDDEN_REQUEST":
                return PaymentFailureReason.CARD_SUSPENDED;
            case "PROVIDER_ERROR":
            case "PG_PROVIDER_ERROR":
                return PaymentFailureReason.PAYMENT_GATEWAY_ERROR;
            default:
                return PaymentFailureReason.OTHER;
        }
    }
    
    private void checkConsecutiveFailuresAndDeactivate(AppUser user, PaymentFailureReason reason) {
        try {
            // 최근 7일간 같은 원인으로 스케줄링에서 실패한 횟수만 조회
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            Long consecutiveFailures = paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                user.getId(), reason, sevenDaysAgo);
            
            log.info("연속 스케줄링 실패 검사 - 사용자: {}, 실패 원인: {}, 최근 7일간 스케줄링 실패 횟수: {}", 
                    user.getEmail(), reason, consecutiveFailures);
            
            if (consecutiveFailures >= 3) {
                // 3번 이상 연속 실패 시 구독 비활성화
                subscriptionService.deactivateSubscriptionDueToPaymentFailures(
                    user, 
                    String.format("연속 스케줄링 결제 실패 (%s) %d회", reason.getDescription(), consecutiveFailures)
                );
                
                // 슬랙 알림 전송
                String deactivationMessage = String.format(
                    "⚠️ 구독 자동 비활성화\n사용자: %s\n이메일: %s\n실패 원인: %s\n연속 스케줄링 실패 횟수: %d회\n비활성화 사유: 최근 7일간 같은 원인으로 3회 이상 스케줄링 결제 실패", 
                    user.getUsername(), user.getEmail(), reason.getDescription(), consecutiveFailures
                );
                
                slackService.sendSlackMessage(deactivationMessage);
                log.warn("구독 자동 비활성화 완료 - 사용자: {}, 원인: {}, 스케줄링 실패 횟수: {}", 
                        user.getEmail(), reason.getDescription(), consecutiveFailures);
            }
        } catch (Exception e) {
            log.error("연속 실패 검사 중 오류 발생 - 사용자: {}, 오류: {}", user.getEmail(), e.getMessage(), e);
        }
    }
}