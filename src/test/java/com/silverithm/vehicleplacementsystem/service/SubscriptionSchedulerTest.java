package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("구독 스케줄러 테스트")
class SubscriptionSchedulerTest {

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
    private PaymentResponse successPaymentResponse;
    private PaymentResponse failurePaymentResponse;

    @BeforeEach
    void setUp() {
        testUser = new AppUser("testuser", "test@example.com", "password", UserRole.ROLE_ADMIN,
                              "refresh_token", null, "customer_test_123");
        testUser.updateBillingKey("encrypted_billing_key_123");
        
        // BillingKeyEncryptionService mock 설정 (lenient로 설정하여 UnnecessaryStubbingException 방지)
        lenient().when(billingKeyEncryptionService.decryptBillingKey("encrypted_billing_key_123"))
                .thenReturn("billing_test_123");

        testSubscription = Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .amount(10000)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().minusDays(1))
                .user(testUser)
                .build();

        testUser.setSubscription(testSubscription);

        successPaymentResponse = new PaymentResponse(
                null,                    // mId
                null,                    // version
                "test_payment_123",      // paymentKey
                "DONE",                  // status
                null,                    // lastTransactionKey
                "order_123",             // orderId
                "테스트 결제",             // orderName
                null,                    // requestedAt
                null,                    // approvedAt
                false,                   // useEscrow
                false,                   // cultureExpense
                null,                    // issuerCode
                null,                    // acquirerCode
                null,                    // number
                0,                       // installmentPlanMonths
                false,                   // isInterestFree
                null,                    // interestPayer
                null,                    // approveNo
                false,                   // useCardPoint
                null,                    // cardType
                null,                    // ownerType
                null,                    // acquireStatus
                10000L,                  // amount
                null,                    // virtualAccount
                null,                    // transfer
                null,                    // mobilePhone
                null,                    // giftCertificate
                null,                    // cashReceipt
                null,                    // cashReceipts
                null,                    // discount
                null,                    // cancels
                null,                    // secret
                null,                    // type
                null,                    // easyPay
                null,                    // country
                null,                    // failure
                false,                   // isPartialCancelable
                null,                    // receiptUrl
                null,                    // checkoutUrl
                "KRW",                   // currency
                10000L,                  // totalAmount
                10000L,                  // balanceAmount
                10000L,                  // suppliedAmount
                0L,                      // vat
                0L,                      // taxFreeAmount
                null,                    // metadata
                0L,                      // taxExemptionAmount
                "CARD"                   // method
        );

        failurePaymentResponse = new PaymentResponse(
                null,                    // mId
                null,                    // version
                "test_payment_456",      // paymentKey
                "FAILED",                // status
                null,                    // lastTransactionKey
                "order_456",             // orderId
                "테스트 결제 실패",         // orderName
                null,                    // requestedAt
                null,                    // approvedAt
                false,                   // useEscrow
                false,                   // cultureExpense
                null,                    // issuerCode
                null,                    // acquirerCode
                null,                    // number
                0,                       // installmentPlanMonths
                false,                   // isInterestFree
                null,                    // interestPayer
                null,                    // approveNo
                false,                   // useCardPoint
                null,                    // cardType
                null,                    // ownerType
                null,                    // acquireStatus
                10000L,                  // amount
                null,                    // virtualAccount
                null,                    // transfer
                null,                    // mobilePhone
                null,                    // giftCertificate
                null,                    // cashReceipt
                null,                    // cashReceipts
                null,                    // discount
                null,                    // cancels
                null,                    // secret
                null,                    // type
                null,                    // easyPay
                null,                    // country
                null,                    // failure
                false,                   // isPartialCancelable
                null,                    // receiptUrl
                null,                    // checkoutUrl
                "KRW",                   // currency
                10000L,                  // totalAmount
                10000L,                  // balanceAmount
                10000L,                  // suppliedAmount
                0L,                      // vat
                0L,                      // taxFreeAmount
                null,                    // metadata
                0L,                      // taxExemptionAmount
                "CARD"                   // method
        );
    }

    @Test
    @DisplayName("결제 대상 사용자가 없을 때 정상 처리")
    void processScheduledPayments_NoUsers() {
        // Given
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, never()).requestPayment(any(), any());
        verify(subscriptionService, never()).processSubscription(any(), any());
    }

    @Test
    @DisplayName("정기결제 성공 시나리오")
    void processScheduledPayments_Success() {
        // Given
        List<AppUser> users = Arrays.asList(testUser);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123")))
                .thenReturn(successPaymentResponse);

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, times(1)).requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123"));
        verify(subscriptionService, times(1)).processSubscription(eq(testUser), any(SubscriptionRequestDTO.class));
    }

    @Test
    @DisplayName("정기결제 실패 시나리오")
    void processScheduledPayments_Failure() {
        // Given
        List<AppUser> users = Arrays.asList(testUser);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123")))
                .thenReturn(failurePaymentResponse);

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, times(1)).requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123"));
        verify(subscriptionService, never()).processSubscription(any(), any());
    }

    @Test
    @DisplayName("빌링키가 없는 사용자 스킵")
    void processScheduledPayments_EmptyBillingKey() {
        // Given
        AppUser userWithoutBillingKey = new AppUser("nobilling", "nobilling@example.com", "password", UserRole.ROLE_ADMIN,
                                                   "refresh_token", null, "customer_nobilling_123");
        userWithoutBillingKey.updateBillingKey("");
        userWithoutBillingKey.setSubscription(testSubscription);

        List<AppUser> users = Arrays.asList(userWithoutBillingKey);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, never()).requestPayment(any(), any());
        verify(subscriptionService, never()).processSubscription(any(), any());
    }

    @Test
    @DisplayName("결제 처리 중 예외 발생 시나리오")
    void processScheduledPayments_Exception() {
        // Given
        List<AppUser> users = Arrays.asList(testUser);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123")))
                .thenThrow(new RuntimeException("토스 API 오류"));

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, times(1)).requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123"));
        verify(subscriptionService, never()).processSubscription(any(), any());
    }

    @Test
    @DisplayName("다중 사용자 정기결제 처리")
    void processScheduledPayments_MultipleUsers() {
        // Given
        AppUser user2 = new AppUser("testuser2", "test2@example.com", "password", UserRole.ROLE_ADMIN,
                                   "refresh_token", null, "customer_test_456");
        user2.updateBillingKey("billing_test_456");
        user2.setSubscription(testSubscription);

        List<AppUser> users = Arrays.asList(testUser, user2);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123")))
                .thenReturn(successPaymentResponse);
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_456")))
                .thenReturn(failurePaymentResponse);

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, times(2)).requestPayment(any(SubscriptionRequestDTO.class), any());
        verify(subscriptionService, times(1)).processSubscription(eq(testUser), any(SubscriptionRequestDTO.class));
    }

    @Test
    @DisplayName("구독이 취소된 사용자는 정기결제 대상에서 제외")
    void processScheduledPayments_CancelledSubscription_Skipped() {
        // Given
        testSubscription.updateStatus(SubscriptionStatus.CANCELLED);
        List<AppUser> users = Arrays.asList(testUser);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, never()).requestPayment(any(), any());
        verify(subscriptionService, never()).processSubscription(any(), any());
    }

    @Test
    @DisplayName("구독이 비활성화된 사용자는 정기결제 대상에서 제외")
    void processScheduledPayments_InactiveSubscription_Skipped() {
        // Given
        testSubscription.updateStatus(SubscriptionStatus.INACTIVE);
        List<AppUser> users = Arrays.asList(testUser);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        verify(billingService, never()).requestPayment(any(), any());
        verify(subscriptionService, never()).processSubscription(any(), any());
    }

    @Test
    @DisplayName("구독이 활성 상태인 사용자만 정기결제 처리")
    void processScheduledPayments_OnlyActiveSubscriptions_Processed() {
        // Given
        AppUser cancelledUser = new AppUser("cancelled", "cancelled@example.com", "password", UserRole.ROLE_ADMIN,
                                           "refresh_token", null, "customer_cancelled_123");
        cancelledUser.updateBillingKey("encrypted_billing_key_cancelled");
        
        Subscription cancelledSubscription = Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .amount(10000)
                .status(SubscriptionStatus.CANCELLED)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().minusDays(1))
                .user(cancelledUser)
                .build();
        cancelledUser.setSubscription(cancelledSubscription);

        AppUser inactiveUser = new AppUser("inactive", "inactive@example.com", "password", UserRole.ROLE_ADMIN,
                                          "refresh_token", null, "customer_inactive_123");
        inactiveUser.updateBillingKey("encrypted_billing_key_inactive");
        
        Subscription inactiveSubscription = Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .amount(10000)
                .status(SubscriptionStatus.INACTIVE)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().minusDays(1))
                .user(inactiveUser)
                .build();
        inactiveUser.setSubscription(inactiveSubscription);

        // 실제 스케줄러는 userRepository에서 이미 필터링된 사용자만 반환한다고 가정
        // 하지만 테스트에서는 모든 사용자를 포함시켜 스케줄러의 필터링 로직을 테스트
        List<AppUser> users = Arrays.asList(testUser, cancelledUser, inactiveUser);
        when(userRepository.findUsersRequiringSubscriptionBilling(any(LocalDateTime.class)))
                .thenReturn(users);
        when(billingService.requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123")))
                .thenReturn(successPaymentResponse);

        // When
        subscriptionScheduler.processScheduledPayments();

        // Then
        verify(userRepository, times(1)).findUsersRequiringSubscriptionBilling(any(LocalDateTime.class));
        // ACTIVE 상태인 testUser만 결제 요청되어야 함
        verify(billingService, times(1)).requestPayment(any(SubscriptionRequestDTO.class), eq("billing_test_123"));
        verify(subscriptionService, times(1)).processSubscription(eq(testUser), any(SubscriptionRequestDTO.class));
        
        // 취소되거나 비활성화된 사용자는 결제 요청되지 않아야 함
        verify(billingService, never()).requestPayment(any(SubscriptionRequestDTO.class), eq("encrypted_billing_key_cancelled"));
        verify(billingService, never()).requestPayment(any(SubscriptionRequestDTO.class), eq("encrypted_billing_key_inactive"));
    }
}