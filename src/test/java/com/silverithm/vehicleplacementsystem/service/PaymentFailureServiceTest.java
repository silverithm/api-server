package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentFailureResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureLog;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.PaymentFailureLogRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 실패 서비스 테스트")
class PaymentFailureServiceTest {

    @Mock
    private PaymentFailureLogRepository paymentFailureLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private PaymentFailureService paymentFailureService;

    private AppUser testUser;
    private PaymentFailureLog testFailureLog;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        testCompany = new Company("Test Company", "서울시 강남구", null);
        testUser = new AppUser("testUser", "test@example.com", "encodedPassword", 
                              UserRole.ROLE_ADMIN, "refreshToken", testCompany, "customerKey");
        
        testFailureLog = PaymentFailureLog.builder()
                .user(testUser)
                .subscriptionId(1L)
                .failureReason(PaymentFailureReason.CARD_LIMIT_EXCEEDED)
                .failureMessage("카드 한도 초과")
                .attemptedAmount(10000)
                .subscriptionType(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .paymentGatewayResponse("토스 응답")
                .build();
    }

    @Test
    @DisplayName("결제 실패 로그 저장 - AppUser 객체 전달")
    void savePaymentFailure_WithUserObject_Success() {
        // given
        when(paymentFailureLogRepository.save(any(PaymentFailureLog.class))).thenReturn(testFailureLog);

        // when
        paymentFailureService.savePaymentFailure(
                testUser, 1L, PaymentFailureReason.CARD_LIMIT_EXCEEDED,
                "카드 한도 초과", 10000, SubscriptionType.BASIC,
                SubscriptionBillingType.MONTHLY, "토스 응답"
        );

        // then
        verify(paymentFailureLogRepository).save(any(PaymentFailureLog.class));
    }

    @Test
    @DisplayName("결제 실패 로그 저장 - 이메일로 사용자 조회")
    void savePaymentFailure_WithEmail_Success() {
        // given
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(paymentFailureLogRepository.save(any(PaymentFailureLog.class))).thenReturn(testFailureLog);

        // when
        paymentFailureService.savePaymentFailure(
                "test@example.com", 1L, PaymentFailureReason.CARD_LIMIT_EXCEEDED,
                "카드 한도 초과", 10000, SubscriptionType.BASIC,
                SubscriptionBillingType.MONTHLY, "토스 응답"
        );

        // then
        verify(userRepository).findByEmail("test@example.com");
        verify(paymentFailureLogRepository).save(any(PaymentFailureLog.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 결제 실패 로그 저장 시 예외 발생")
    void savePaymentFailure_UserNotFound_ThrowsException() {
        // given
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                paymentFailureService.savePaymentFailure(
                        "notfound@example.com", 1L, PaymentFailureReason.CARD_LIMIT_EXCEEDED,
                        "카드 한도 초과", 10000, SubscriptionType.BASIC,
                        SubscriptionBillingType.MONTHLY, "토스 응답"
                )
        );

        assertEquals("해당 이메일의 사용자를 찾을 수 없습니다: notfound@example.com", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    }

    @Test
    @DisplayName("내 결제 실패 내역 조회")
    void getMyPaymentFailures_Success() {
        // given
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentFailureLog> failurePage = new PageImpl<>(List.of(testFailureLog));
        when(paymentFailureLogRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId(), pageable))
                .thenReturn(failurePage);

        // when
        Page<PaymentFailureResponseDTO> result = paymentFailureService.getMyPaymentFailures(userDetails, pageable);

        // then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(PaymentFailureReason.CARD_LIMIT_EXCEEDED, result.getContent().get(0).getFailureReason());
        verify(userRepository).findByEmail("test@example.com");
        verify(paymentFailureLogRepository).findByUserIdOrderByCreatedAtDesc(testUser.getId(), pageable);
    }

    @Test
    @DisplayName("실패 원인별 결제 실패 내역 조회")
    void getMyPaymentFailuresByReason_Success() {
        // given
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentFailureLog> failurePage = new PageImpl<>(List.of(testFailureLog));
        when(paymentFailureLogRepository.findByUserIdAndFailureReasonOrderByCreatedAtDesc(
                testUser.getId(), PaymentFailureReason.CARD_LIMIT_EXCEEDED, pageable))
                .thenReturn(failurePage);

        // when
        Page<PaymentFailureResponseDTO> result = paymentFailureService.getMyPaymentFailuresByReason(
                userDetails, PaymentFailureReason.CARD_LIMIT_EXCEEDED, pageable);

        // then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(PaymentFailureReason.CARD_LIMIT_EXCEEDED, result.getContent().get(0).getFailureReason());
        verify(paymentFailureLogRepository).findByUserIdAndFailureReasonOrderByCreatedAtDesc(
                testUser.getId(), PaymentFailureReason.CARD_LIMIT_EXCEEDED, pageable);
    }

    @Test
    @DisplayName("기간별 결제 실패 내역 조회")
    void getMyPaymentFailuresByDateRange_Success() {
        // given
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentFailureLog> failurePage = new PageImpl<>(List.of(testFailureLog));
        
        when(paymentFailureLogRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(testUser.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable)))
                .thenReturn(failurePage);

        // when
        Page<PaymentFailureResponseDTO> result = paymentFailureService.getMyPaymentFailuresByDateRange(
                userDetails, startDate, endDate, pageable);

        // then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(paymentFailureLogRepository).findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(testUser.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable));
    }
}