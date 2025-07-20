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
        testUser.updateBillingKey("test_billing_key");
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
                failureMap, null, null, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 연속 실패 카운트는 2번으로 설정 (비활성화되지 않도록)
        when(paymentFailureLogRepository.countRecentFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.OTHER), any(LocalDateTime.class)))
                .thenReturn(2L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.OTHER),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString()
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
                failureMap, null, null, false, null, null, null, 0L, 0L, 0L, 0L, 0L, null, 0L, null
        );
        
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), anyString()))
                .thenReturn(failureResponse);
        
        // 3번째 연속 실패로 설정
        when(paymentFailureLogRepository.countRecentFailuresByUserAndReason(
                eq(1L), eq(PaymentFailureReason.OTHER), any(LocalDateTime.class)))
                .thenReturn(3L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.OTHER),
                anyString(), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                anyString()
        );
        
        verify(subscriptionService).deactivateSubscriptionDueToPaymentFailures(
                eq(testUser), contains("연속 결제 실패")
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
        when(paymentFailureLogRepository.countRecentFailuresByUserAndReason(
                eq(testUser.getId()), eq(PaymentFailureReason.OTHER), any(LocalDateTime.class)))
                .thenReturn(2L);

        // when
        subscriptionScheduler.processScheduledPayments();

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq(testUser), eq(testSubscription.getId()), eq(PaymentFailureReason.OTHER),
                eq("결제 시스템 오류"), eq(10000), eq(SubscriptionType.BASIC), eq(SubscriptionBillingType.MONTHLY),
                contains("Scheduled payment exception")
        );
        
        verify(slackService).sendSlackMessage(contains("정기결제 시스템 오류"));
    }
}