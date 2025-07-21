package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
import com.silverithm.vehicleplacementsystem.repository.PaymentFailureLogRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("구독 스케줄러 통합 테스트")
class SubscriptionSchedulerIntegrationTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private BillingService billingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SlackService slackService;

    @Mock
    private PaymentFailureService paymentFailureService;

    @Mock
    private PaymentFailureLogRepository paymentFailureLogRepository;
    
    @Mock
    private BillingKeyEncryptionService billingKeyEncryptionService;

    @InjectMocks
    private SubscriptionScheduler subscriptionScheduler;

    private AppUser testUser;
    private Subscription testSubscription;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        testCompany = new Company("Test Company", "서울시 강남구", null);
        testUser = new AppUser("testUser", "test@example.com", "encodedPassword", 
                              UserRole.ROLE_CLIENT, "refreshToken", testCompany, "customerKey");
        testUser.updateBillingKey("encrypted_billing_key");
        
        // BillingKeyEncryptionService mock 설정 (lenient로 설정하여 UnnecessaryStubbingException 방지)
        lenient().when(billingKeyEncryptionService.decryptBillingKey("encrypted_billing_key"))
                .thenReturn("test_billing_key");
        
        // Set user ID using reflection since it's typically set by JPA
        try {
            java.lang.reflect.Field idField = testUser.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(testUser, 1L);
        } catch (Exception e) {
            // ID setting failed
        }
        
        testSubscription = Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusMonths(1))
                .status(SubscriptionStatus.ACTIVE)
                .amount(10000)
                .user(testUser)
                .build();
        
        testUser.setSubscription(testSubscription);
        
        // 기본 실패 카운트 stub 설정 (모든 테스트에서 공통으로 사용)
        lenient().when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                any(Long.class), any(PaymentFailureReason.class), any(LocalDateTime.class)))
                .thenReturn(0L);
    }

    @Test
    @DisplayName("스케줄링 결제 실패 시 실패 로그 저장")
    void processScheduledPayments_PaymentFails_SavesFailureLog() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        // 결제 실패 응답 생성
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "EXCEED_MAX_CARD_LIMIT");
        failureMap.put("message", "카드 한도 초과");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 연속 실패 카운트는 2번으로 설정 (비활성화되지 않도록)
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED), any(LocalDateTime.class)))
                .thenReturn(2L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
        
        verify(slackService).sendSlackMessage(contains("정기결제 실패 알림"));
    }

    @Test
    @DisplayName("3번 연속 실패 시 구독 비활성화")
    void processScheduledPayments_ThreeConsecutiveFailures_DeactivatesSubscription() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        // 결제 실패 응답 생성
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "EXCEED_MAX_CARD_LIMIT");
        failureMap.put("message", "카드 한도 초과");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 3번째 연속 실패로 설정
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED), any(LocalDateTime.class)))
                .thenReturn(3L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
        
        verify(subscriptionService).deactivateSubscriptionDueToPaymentFailures(
                eq(testUser), contains("연속 스케줄링 결제 실패")
        );
        
        verify(slackService, times(2)).sendSlackMessage(anyString()); // 실패 알림 + 비활성화 알림
    }

    @Test
    @DisplayName("결제 예외 발생 시 실패 로그 저장")
    void processScheduledPayments_PaymentException_SavesFailureLog() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenThrow(new RuntimeException("결제 시스템 오류"));
        
        // 2번째 OTHER 실패로 설정 (비활성화되지 않도록)
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(testUser.getId()), eq(PaymentFailureReason.OTHER), any(LocalDateTime.class)))
                .thenReturn(2L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.OTHER),
                eq("결제 시스템 오류"), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                contains("Scheduled payment exception"), eq(true)
        );
        
        verify(slackService).sendSlackMessage(contains("정기결제 시스템 오류"));
    }

    @Test
    @DisplayName("구독 활성화 후 정기결제 정상 처리")
    void processScheduledPayments_ActiveSubscriptionAfterActivation_Success() {
        // given
        testSubscription.updateStatus(SubscriptionStatus.ACTIVE);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        PaymentResponse successResponse = new PaymentResponse(
                null, null, "test_payment_key", "DONE", null, "order_123", "정기결제", 
                null, null, false, false, null, null, null, 0, false, null, null, 
                false, null, null, null, 10000L, null, null, null, null, null, 
                null, null, null, null, null, null, null, null, false, null, null, 
                "KRW", 10000L, 10000L, 10000L, 0L, 0L, null, 0L, "CARD"
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(successResponse);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(billingService).requestPayment(any(SubscriptionRequestDTO.class), eq("test_billing_key"));
        verify(subscriptionService).processSubscription(eq(testUser), any(SubscriptionRequestDTO.class));
        verify(paymentFailureService, never()).savePaymentFailure(any(AppUser.class), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("취소된 구독은 정기결제 처리하지 않음")
    void processScheduledPayments_CancelledSubscription_NoProcessing() {
        // given
        testSubscription.updateStatus(SubscriptionStatus.CANCELLED);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(billingService, never()).requestPayment(any(), any());
        verify(subscriptionService, never()).processSubscription(any(), any());
        verify(paymentFailureService, never()).savePaymentFailure(any(AppUser.class), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("비활성화된 구독은 정기결제 처리하지 않음")
    void processScheduledPayments_InactiveSubscription_NoProcessing() {
        // given
        testSubscription.updateStatus(SubscriptionStatus.INACTIVE);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(billingService, never()).requestPayment(any(), any());
        verify(subscriptionService, never()).processSubscription(any(), any());
        verify(paymentFailureService, never()).savePaymentFailure(any(AppUser.class), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("활성화된 구독과 비활성화된 구독 혼재 시 활성화된 구독만 처리")
    void processScheduledPayments_MixedSubscriptionStatus_OnlyActiveProcessed() {
        // given
        AppUser cancelledUser = new AppUser("cancelled", "cancelled@example.com", "password", 
                                           UserRole.ROLE_CLIENT, "refresh", testCompany, "customer2");
        cancelledUser.updateBillingKey("encrypted_billing_key_2");
        
        // Set cancelled user ID using reflection
        try {
            java.lang.reflect.Field idField = cancelledUser.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(cancelledUser, 2L);
        } catch (Exception e) {
            // ID setting failed
        }
        
        Subscription cancelledSubscription = Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusMonths(1))
                .status(SubscriptionStatus.CANCELLED)
                .amount(10000)
                .user(cancelledUser)
                .build();
        cancelledUser.setSubscription(cancelledSubscription);

        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser, cancelledUser));
        
        PaymentResponse successResponse = new PaymentResponse(
                null, null, "test_payment_key", "DONE", null, "order_123", "정기결제", 
                null, null, false, false, null, null, null, 0, false, null, null, 
                false, null, null, null, 10000L, null, null, null, null, null, 
                null, null, null, null, null, null, null, null, false, null, null, 
                "KRW", 10000L, 10000L, 10000L, 0L, 0L, null, 0L, "CARD"
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), eq("test_billing_key")))
                .thenReturn(successResponse);

        lenient().when(billingKeyEncryptionService.decryptBillingKey("encrypted_billing_key_2"))
                .thenReturn("test_billing_key_2");

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(billingService, times(1)).requestPayment(any(SubscriptionRequestDTO.class), eq("test_billing_key"));
        verify(billingService, never()).requestPayment(any(SubscriptionRequestDTO.class), eq("test_billing_key_2"));
        verify(subscriptionService, times(1)).processSubscription(eq(testUser), any(SubscriptionRequestDTO.class));
        verify(subscriptionService, never()).processSubscription(eq(cancelledUser), any());
    }

    @Test
    @DisplayName("카드 한도 초과 실패 시 정확한 실패 원인 분류")
    void processScheduledPayments_CardLimitExceeded_CorrectFailureReason() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "EXCEED_MAX_CARD_LIMIT");
        failureMap.put("message", "카드 한도를 초과했습니다.");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED), any(LocalDateTime.class)))
                .thenReturn(1L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED),
                contains("카드 한도"), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
    }

    @Test
    @DisplayName("잔액 부족 실패 시 정확한 실패 원인 분류")
    void processScheduledPayments_InsufficientBalance_CorrectFailureReason() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "INSUFFICIENT_BALANCE");
        failureMap.put("message", "잔액이 부족합니다.");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.INSUFFICIENT_BALANCE), any(LocalDateTime.class)))
                .thenReturn(1L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.INSUFFICIENT_BALANCE),
                contains("잔액"), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
    }

    @Test
    @DisplayName("유효하지 않은 카드 실패 시 정확한 실패 원인 분류")
    void processScheduledPayments_InvalidCard_CorrectFailureReason() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "INVALID_CARD");
        failureMap.put("message", "유효하지 않은 카드입니다.");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.INVALID_CARD), any(LocalDateTime.class)))
                .thenReturn(1L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.INVALID_CARD),
                contains("유효하지 않은"), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
    }

    @Test
    @DisplayName("서로 다른 실패 원인들이 독립적으로 카운트됨")
    void processScheduledPayments_DifferentFailureReasons_IndependentCounting() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "EXCEED_MAX_CARD_LIMIT");
        failureMap.put("message", "카드 한도를 초과했습니다.");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 카드 한도 초과는 2번째로 설정
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED), any(LocalDateTime.class)))
                .thenReturn(2L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
        
        // 아직 3번에 도달하지 않았으므로 비활성화되지 않음
        verify(subscriptionService, never()).deactivateSubscriptionDueToPaymentFailures(any(), any());
    }

    @Test
    @DisplayName("동일한 실패 원인으로 3번 연속 실패 시 구독 비활성화")
    void processScheduledPayments_SameReasonThreeFailures_DeactivatesSubscription() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "INSUFFICIENT_BALANCE");
        failureMap.put("message", "잔액이 부족합니다.");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 잔액 부족으로 3번째 실패
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.INSUFFICIENT_BALANCE), any(LocalDateTime.class)))
                .thenReturn(3L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.INSUFFICIENT_BALANCE),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
        
        verify(subscriptionService).deactivateSubscriptionDueToPaymentFailures(
                eq(testUser), contains("연속 스케줄링 결제 실패 (잔액 부족) 3회")
        );
    }

    @Test
    @DisplayName("같은 실패 원인 2번 + 다른 실패 원인 3번인 경우 비활성화 안됨")
    void processScheduledPayments_MixedFailureReasons_NoDeactivation() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "INSUFFICIENT_BALANCE");
        failureMap.put("message", "잔액이 부족합니다.");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 잔액 부족은 2번으로 설정
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.INSUFFICIENT_BALANCE), any(LocalDateTime.class)))
                .thenReturn(2L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.INSUFFICIENT_BALANCE),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
        
        // 현재 실패한 원인(잔액 부족)은 아직 2번이므로 비활성화되지 않음
        verify(subscriptionService, never()).deactivateSubscriptionDueToPaymentFailures(any(), any());
    }

    @Test
    @DisplayName("7일 이전의 실패는 카운트에 포함되지 않음")
    void processScheduledPayments_OldFailures_NotCounted() {
        // given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(List.of(testUser));
        
        Map<String, Object> failureMap = new HashMap<>();
        failureMap.put("code", "INSUFFICIENT_BALANCE");
        failureMap.put("message", "잔액이 부족합니다.");
        
        PaymentResponse failureResponse = new PaymentResponse(
                null, null, null, "FAILED", null, null, null, null, null, false, false,
                null, null, null, 0, false, null, null, false, null, null, null, 
                0L, null, null, null, null, null, null, null, null, null, null, 
                null, null, failureMap, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 최근 7일 이내의 실패는 2번만 (3번에 도달하지 않음)
        when(paymentFailureLogRepository.countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.INSUFFICIENT_BALANCE), any(LocalDateTime.class)))
                .thenReturn(2L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.INSUFFICIENT_BALANCE),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString(), eq(true)
        );
        
        // 7일 이내 실패가 3번 미만이므로 비활성화되지 않음
        verify(subscriptionService, never()).deactivateSubscriptionDueToPaymentFailures(any(), any());
        
        // countRecentScheduledFailuresByUserAndReason이 7일 전 날짜로 호출되었는지 확인
        verify(paymentFailureLogRepository).countRecentScheduledFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.INSUFFICIENT_BALANCE), any(LocalDateTime.class)
        );
    }
}