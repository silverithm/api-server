package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("빌링 서비스 테스트")
class BillingServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SlackService slackService;

    @InjectMocks
    private BillingService billingService;

    private SubscriptionRequestDTO requestDto;
    private PaymentResponse successResponse;
    private PaymentResponse failureResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(billingService, "secretKey", "test_sk_dummy_key");

        requestDto = SubscriptionRequestDTO.of(
                SubscriptionType.BASIC,
                SubscriptionBillingType.MONTHLY,
                10000,
                "customer_test_123",
                "billing_test_123",
                "BASIC_MONTHLY",
                "test@example.com",
                "testuser",
                0
        );

        successResponse = new PaymentResponse(
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

        failureResponse = new PaymentResponse(
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
    @DisplayName("정상 결제 요청 성공")
    void requestPayment_Success() {
        // Given
        when(restTemplate.exchange(
                any(String.class),
                any(org.springframework.http.HttpMethod.class),
                any(),
                eq(PaymentResponse.class)
        )).thenReturn(new ResponseEntity<>(successResponse, HttpStatus.OK));

        // When
        PaymentResponse result = billingService.requestPayment(requestDto, "billing_test_123");

        // Then
        assertNotNull(result);
        assertEquals("DONE", result.status());
        assertEquals("test_payment_123", result.paymentKey());
        verify(slackService, times(1)).sendPaymentSuccessNotification(any(), eq("testuser"), eq(10000.0));
    }

    @Test
    @DisplayName("빌링키가 null인 경우 예외 발생")
    void requestPayment_NullBillingKey() {
        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            billingService.requestPayment(requestDto, null);
        });

        assertTrue(exception.getMessage().contains("서버 내부 오류"));
        verify(restTemplate, never()).exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), any(Class.class));
    }

    @Test
    @DisplayName("빈 빌링키인 경우 예외 발생")
    void requestPayment_EmptyBillingKey() {
        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            billingService.requestPayment(requestDto, "");
        });

        assertTrue(exception.getMessage().contains("서버 내부 오류"));
        verify(restTemplate, never()).exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), any(Class.class));
    }

    @Test
    @DisplayName("4xx 클라이언트 에러 시 재시도 없이 즉시 실패")
    void requestPayment_ClientError() {
        // Given
        when(restTemplate.exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid request".getBytes(), null));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            billingService.requestPayment(requestDto, "billing_test_123");
        });

        assertTrue(exception.getMessage().startsWith("결제 실패:"));
        verify(restTemplate, times(1)).exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class));
        verify(slackService, times(1)).sendApiFailureNotification(eq("결제 실패 (클라이언트 오류)"), any(), any(), any());
    }

    @Test
    @DisplayName("5xx 서버 에러 시 최대 3회 재시도")
    void requestPayment_ServerError_Retry() {
        // Given
        when(restTemplate.exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Server error".getBytes(), null));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            billingService.requestPayment(requestDto, "billing_test_123");
        });

        assertTrue(exception.getMessage().contains("토스 서버 오류가 발생했습니다"));
        verify(restTemplate, times(3)).exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class));
        verify(slackService, times(1)).sendApiFailureNotification(eq("결제 실패 (서버 오류)"), any(), any(), any());
    }

    @Test
    @DisplayName("일반 예외 발생 시 최대 3회 재시도")
    void requestPayment_GeneralException_Retry() {
        // Given
        when(restTemplate.exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class)))
                .thenThrow(new RuntimeException("Network timeout"));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            billingService.requestPayment(requestDto, "billing_test_123");
        });

        assertTrue(exception.getMessage().contains("서버 내부 오류"));
        verify(restTemplate, times(3)).exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class));
        verify(slackService, times(1)).sendApiFailureNotification(eq("결제 실패 (시스템 오류)"), any(), any(), any());
    }

    @Test
    @DisplayName("첫 번째 시도 실패 후 두 번째 시도 성공")
    void requestPayment_RetrySuccess() {
        // Given
        when(restTemplate.exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", "Error".getBytes(), null))
                .thenReturn(new ResponseEntity<>(successResponse, HttpStatus.OK));

        // When
        PaymentResponse result = billingService.requestPayment(requestDto, "billing_test_123");

        // Then
        assertNotNull(result);
        assertEquals("DONE", result.status());
        verify(restTemplate, times(2)).exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class));
        verify(slackService, times(1)).sendPaymentSuccessNotification(any(), eq("testuser"), eq(10000.0));
    }

    @Test
    @DisplayName("결제 응답이 DONE이 아닌 경우")
    void requestPayment_NotDoneStatus() {
        // Given
        when(restTemplate.exchange(any(String.class), any(org.springframework.http.HttpMethod.class), any(), eq(PaymentResponse.class)))
                .thenReturn(new ResponseEntity<>(failureResponse, HttpStatus.OK));

        // When
        PaymentResponse result = billingService.requestPayment(requestDto, "billing_test_123");

        // Then
        assertNotNull(result);
        assertEquals("FAILED", result.status());
        verify(slackService, never()).sendPaymentSuccessNotification(any(String.class), any(String.class), any(Double.class));
    }
}