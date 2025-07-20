package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.FreeSubscriptionHistory;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.FreeSubscriptionHistoryRepository;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("무료 구독 테스트")
class FreeSubscriptionTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private FreeSubscriptionHistoryRepository freeSubscriptionHistoryRepository;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private AppUser testUser;
    private AppUser userWithSubscription;
    private Company testCompany;
    private Subscription existingSubscription;

    @BeforeEach
    void setUp() {
        testCompany = new Company("Test Company", "서울시 강남구", null);
        
        // 구독이 없는 사용자
        testUser = new AppUser("testUser", "test@example.com", "encodedPassword", 
                              UserRole.ROLE_CLIENT, "refreshToken", testCompany, "customerKey");
        
        // 이미 구독이 있는 사용자
        userWithSubscription = new AppUser("userWithSub", "existing@example.com", "encodedPassword", 
                                          UserRole.ROLE_CLIENT, "refreshToken", testCompany, "customerKey2");
        
        existingSubscription = Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1))
                .status(SubscriptionStatus.ACTIVE)
                .amount(10000)
                .user(userWithSubscription)
                .build();
        
        userWithSubscription.setSubscription(existingSubscription);
    }

    @Test
    @DisplayName("처음 무료 구독 생성 성공")
    void createFreeSubscription_FirstTime_Success() {
        // given
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(freeSubscriptionHistoryRepository.existsByUserId(testUser.getId())).thenReturn(false);
        
        Subscription savedSubscription = Subscription.builder()
                .planName(SubscriptionType.FREE)
                .billingType(SubscriptionBillingType.FREE)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE)
                .amount(0)
                .user(testUser)
                .build();
        
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(savedSubscription);
        when(freeSubscriptionHistoryRepository.save(any(FreeSubscriptionHistory.class)))
                .thenReturn(FreeSubscriptionHistory.builder().user(testUser).subscriptionId(1L).build());

        // when
        SubscriptionResponseDTO result = subscriptionService.createFreeSubscription(userDetails);

        // then
        assertNotNull(result);
        assertEquals(SubscriptionType.FREE, result.getPlanName());
        assertEquals(SubscriptionBillingType.FREE, result.getBillingType());
        assertEquals(0, result.getAmount());
        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        
        verify(userRepository).findByEmail("test@example.com");
        verify(freeSubscriptionHistoryRepository).existsByUserId(testUser.getId());
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(freeSubscriptionHistoryRepository).save(any(FreeSubscriptionHistory.class));
    }

    @Test
    @DisplayName("이미 구독이 있는 경우 무료 구독 생성 실패")
    void createFreeSubscription_AlreadyHasSubscription_ThrowsException() {
        // given
        when(userDetails.getUsername()).thenReturn("existing@example.com");
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(userWithSubscription));

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                subscriptionService.createFreeSubscription(userDetails)
        );

        assertEquals("User already has a subscription", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        
        verify(userRepository).findByEmail("existing@example.com");
        verify(freeSubscriptionHistoryRepository, never()).existsByUserId(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("무료 구독 이력이 있는 경우 재생성 실패")
    void createFreeSubscription_HasFreeSubscriptionHistory_ThrowsException() {
        // given
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(freeSubscriptionHistoryRepository.existsByUserId(testUser.getId())).thenReturn(true);

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                subscriptionService.createFreeSubscription(userDetails)
        );

        assertEquals("User has already used free subscription before", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        
        verify(userRepository).findByEmail("test@example.com");
        verify(freeSubscriptionHistoryRepository).existsByUserId(testUser.getId());
        verify(subscriptionRepository, never()).save(any());
        verify(freeSubscriptionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 무료 구독 생성 시도")
    void createFreeSubscription_UserNotFound_ThrowsException() {
        // given
        when(userDetails.getUsername()).thenReturn("notfound@example.com");
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                subscriptionService.createFreeSubscription(userDetails)
        );

        assertEquals("User not found with email: notfound@example.com", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
        
        verify(userRepository).findByEmail("notfound@example.com");
        verify(freeSubscriptionHistoryRepository, never()).existsByUserId(any());
    }

    @Test
    @DisplayName("내 구독 조회 시 무료 구독 이력 포함")
    void getMySubscription_IncludesFreeSubscriptionHistory() {
        // given
        testUser.setSubscription(Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1))
                .status(SubscriptionStatus.ACTIVE)
                .amount(10000)
                .user(testUser)
                .build());
        
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(freeSubscriptionHistoryRepository.existsByUserId(testUser.getId())).thenReturn(true);

        // when
        SubscriptionResponseDTO result = subscriptionService.getMySubscription(userDetails);

        // then
        assertNotNull(result);
        assertTrue(result.getHasUsedFreeSubscription());
        assertEquals(SubscriptionType.BASIC, result.getPlanName());
        
        verify(userRepository).findByEmail("test@example.com");
        verify(freeSubscriptionHistoryRepository).existsByUserId(testUser.getId());
    }

    @Test
    @DisplayName("내 구독 조회 시 무료 구독 이력 없음")
    void getMySubscription_NoFreeSubscriptionHistory() {
        // given
        testUser.setSubscription(Subscription.builder()
                .planName(SubscriptionType.BASIC)
                .billingType(SubscriptionBillingType.MONTHLY)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1))
                .status(SubscriptionStatus.ACTIVE)
                .amount(10000)
                .user(testUser)
                .build());
        
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(freeSubscriptionHistoryRepository.existsByUserId(testUser.getId())).thenReturn(false);

        // when
        SubscriptionResponseDTO result = subscriptionService.getMySubscription(userDetails);

        // then
        assertNotNull(result);
        assertFalse(result.getHasUsedFreeSubscription());
        assertEquals(SubscriptionType.BASIC, result.getPlanName());
        
        verify(userRepository).findByEmail("test@example.com");
        verify(freeSubscriptionHistoryRepository).existsByUserId(testUser.getId());
    }
}