package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 실패 로그 저장 통합 테스트")
class PaymentFailureIntegrationTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SlackService slackService;

    @Mock
    private PaymentFailureService paymentFailureService;

    @InjectMocks
    private BillingService billingService;

    private AppUser testUser;
    private SubscriptionRequestDTO requestDto;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(billingService, "secretKey", "test_sk_dummy_key");
        
        testCompany = new Company("Test Company", "서울시 강남구", null);
        testUser = new AppUser("testUser", "test@example.com", "encodedPassword", 
                              UserRole.ROLE_CLIENT, "refreshToken", testCompany, "customerKey");
        
        requestDto = SubscriptionRequestDTO.of(
                SubscriptionType.BASIC,
                SubscriptionBillingType.MONTHLY,
                10000,
                "customerKey",
                "authKey",
                "Basic Plan",
                "test@example.com",
                "Test User",
                0
        );
    }

    @Test
    @DisplayName("4xx 클라이언트 오류 시 결제 실패 로그 저장")
    void requestPayment_ClientError_SavesFailureLog() {
        // given
        String errorResponse = "{\"code\":\"EXCEED_MAX_CARD_LIMIT\",\"message\":\"카드 한도를 초과했습니다\"}";
        HttpClientErrorException clientError = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request", errorResponse.getBytes(), null);
        
        when(restTemplate.exchange(anyString(), any(), any(), eq(com.silverithm.vehicleplacementsystem.dto.PaymentResponse.class)))
                .thenThrow(clientError);

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                billingService.requestPayment(requestDto, "test_billing_key")
        );

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq("test@example.com"), 
                isNull(), 
                eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED),
                eq(errorResponse), 
                eq(10000),
                eq(SubscriptionType.BASIC), 
                eq(SubscriptionBillingType.MONTHLY),
                eq(errorResponse)
        );
        
        verify(slackService).sendApiFailureNotification(
                eq("결제 실패 (클라이언트 오류)"), 
                eq("test@example.com"), 
                eq(errorResponse), 
                anyString()
        );
        
        assertTrue(exception.getMessage().contains("결제 실패"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("5xx 서버 오류 시 결제 실패 로그 저장")
    void requestPayment_ServerError_SavesFailureLog() {
        // given
        String errorResponse = "{\"code\":\"PROVIDER_ERROR\",\"message\":\"결제 서비스 일시 장애\"}";
        HttpClientErrorException serverError = new HttpClientErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", errorResponse.getBytes(), null);
        
        when(restTemplate.exchange(anyString(), any(), any(), eq(com.silverithm.vehicleplacementsystem.dto.PaymentResponse.class)))
                .thenThrow(serverError);

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                billingService.requestPayment(requestDto, "test_billing_key")
        );

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq("test@example.com"), 
                isNull(), 
                eq(PaymentFailureReason.PAYMENT_GATEWAY_ERROR),
                eq(errorResponse), 
                eq(10000),
                eq(SubscriptionType.BASIC), 
                eq(SubscriptionBillingType.MONTHLY),
                eq(errorResponse)
        );
        
        verify(slackService).sendApiFailureNotification(
                eq("결제 실패 (서버 오류)"), 
                eq("test@example.com"), 
                eq(errorResponse), 
                anyString()
        );
    }

    @Test
    @DisplayName("일반 예외 발생 시 결제 실패 로그 저장")
    void requestPayment_GeneralException_SavesFailureLog() {
        // given
        RuntimeException generalException = new RuntimeException("네트워크 연결 실패");
        
        when(restTemplate.exchange(anyString(), any(), any(), eq(com.silverithm.vehicleplacementsystem.dto.PaymentResponse.class)))
                .thenThrow(generalException);

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                billingService.requestPayment(requestDto, "test_billing_key")
        );

        // then
        verify(paymentFailureService).savePaymentFailure(
                eq("test@example.com"), 
                isNull(), 
                eq(PaymentFailureReason.OTHER),
                eq("네트워크 연결 실패"), 
                eq(10000),
                eq(SubscriptionType.BASIC), 
                eq(SubscriptionBillingType.MONTHLY),
                eq("시스템 오류: 네트워크 연결 실패")
        );
        
        verify(slackService).sendApiFailureNotification(
                eq("결제 실패 (시스템 오류)"), 
                eq("test@example.com"), 
                eq("네트워크 연결 실패"), 
                anyString()
        );
    }

    @Test
    @DisplayName("실패 원인 분석 테스트 - 카드 한도 초과")
    void determineFailureReason_CardLimitExceeded() {
        // given
        String limitErrorResponse = "{\"code\":\"EXCEED_MAX_CARD_LIMIT\",\"message\":\"카드 한도를 초과했습니다\"}";
        HttpClientErrorException limitError = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request", limitErrorResponse.getBytes(), null);
        
        when(restTemplate.exchange(anyString(), any(), any(), eq(com.silverithm.vehicleplacementsystem.dto.PaymentResponse.class)))
                .thenThrow(limitError);

        // when & then
        assertThrows(CustomException.class, () ->
                billingService.requestPayment(requestDto, "test_billing_key")
        );

        // then
        verify(paymentFailureService).savePaymentFailure(
                anyString(), any(), eq(PaymentFailureReason.CARD_LIMIT_EXCEEDED), anyString(), 
                anyInt(), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("실패 원인 분석 테스트 - 유효하지 않은 카드")
    void determineFailureReason_InvalidCard() {
        // given
        String invalidCardResponse = "{\"code\":\"INVALID_CARD\",\"message\":\"유효하지 않은 카드입니다\"}";
        HttpClientErrorException invalidCardError = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request", invalidCardResponse.getBytes(), null);
        
        when(restTemplate.exchange(anyString(), any(), any(), eq(com.silverithm.vehicleplacementsystem.dto.PaymentResponse.class)))
                .thenThrow(invalidCardError);

        // when & then
        assertThrows(CustomException.class, () ->
                billingService.requestPayment(requestDto, "test_billing_key")
        );

        // then
        verify(paymentFailureService).savePaymentFailure(
                anyString(), any(), eq(PaymentFailureReason.INVALID_CARD), anyString(), 
                anyInt(), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("실패 원인 분석 테스트 - 잔액 부족")
    void determineFailureReason_InsufficientBalance() {
        // given
        String insufficientResponse = "{\"message\":\"잔액이 부족합니다\"}";
        HttpClientErrorException insufficientError = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request", insufficientResponse.getBytes(), null);
        
        when(restTemplate.exchange(anyString(), any(), any(), eq(com.silverithm.vehicleplacementsystem.dto.PaymentResponse.class)))
                .thenThrow(insufficientError);

        // when & then
        assertThrows(CustomException.class, () ->
                billingService.requestPayment(requestDto, "test_billing_key")
        );

        // then
        verify(paymentFailureService).savePaymentFailure(
                anyString(), any(), eq(PaymentFailureReason.INSUFFICIENT_BALANCE), anyString(), 
                anyInt(), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("재시도 실패 후 최종 실패 로그 저장")
    void requestPayment_AllRetriesFailed_SavesFinalFailureLog() {
        // given
        RuntimeException networkError = new RuntimeException("Connection timeout");
        
        when(restTemplate.exchange(anyString(), any(), any(), eq(com.silverithm.vehicleplacementsystem.dto.PaymentResponse.class)))
                .thenThrow(networkError);

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                billingService.requestPayment(requestDto, "test_billing_key")
        );

        // then
        // 재시도 로직에서 일반 예외는 재시도하지만 마지막에만 실패 로그 저장
        verify(paymentFailureService, times(1)).savePaymentFailure(
                anyString(), any(), any(), anyString(), anyInt(), any(), any(), anyString()
        );
        
        // 최종 실패 로그 확인
        verify(paymentFailureService).savePaymentFailure(
                eq("test@example.com"), 
                isNull(), 
                eq(PaymentFailureReason.OTHER),
                eq("Connection timeout"), 
                eq(10000),
                eq(SubscriptionType.BASIC), 
                eq(SubscriptionBillingType.MONTHLY),
                eq("시스템 오류: Connection timeout")
        );
    }
}