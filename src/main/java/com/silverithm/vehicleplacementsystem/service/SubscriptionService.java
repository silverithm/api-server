package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.BillingResponse;
import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.FreeSubscriptionHistory;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.FreeSubscriptionHistoryRepository;
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
@Slf4j
public class SubscriptionService {

    private final BillingService billingService;
    private final SubscriptionTransactionService subscriptionTransactionService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final FreeSubscriptionHistoryRepository freeSubscriptionHistoryRepository;
    private final BillingKeyEncryptionService billingKeyEncryptionService;

    public SubscriptionResponseDTO createOrUpdateSubscription(UserDetails userDetails,
                                                              SubscriptionRequestDTO requestDto) {
        AppUser user = findUserByEmail(userDetails.getUsername());
        billingService.ensureBillingKey(user, requestDto);
        billingService.processPayment(requestDto, user.getBillingKey());
        return subscriptionTransactionService.processSubscription(user, requestDto);
    }

    @Transactional
    public SubscriptionResponseDTO processSubscription(AppUser user, SubscriptionRequestDTO requestDto) {
        return subscriptionTransactionService.processSubscription(user, requestDto);
    }


    private AppUser findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("해당 이메일의 사용자를 찾을 수 없습니다: " + email, HttpStatus.NOT_FOUND));
    }


    @Transactional
    public SubscriptionResponseDTO createSubscriptionToUser(SubscriptionRequestDTO requestDto, Long userId) {

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자 ID를 찾을 수 없습니다: " + userId));

        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = SubscriptionBillingType.calculateEndDate(requestDto.getBillingType());

        Subscription subscription = Subscription.builder().planName(requestDto.getPlanName())
                .billingType(requestDto.getBillingType()).startDate(startDate).endDate(endDate)
                .status(SubscriptionStatus.ACTIVE).amount(requestDto.getAmount()).user(user).build();

        return new SubscriptionResponseDTO(subscriptionRepository.save(subscription));
    }


    public SubscriptionResponseDTO getSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 구독 ID를 찾을 수 없습니다: " + id));
        return new SubscriptionResponseDTO(subscription);
    }

    @Transactional
    public SubscriptionResponseDTO cancelSubscription(UserDetails userDetails) {

        AppUser user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new CustomException("해당 사용자 이메일을 찾을 수 없습니다: " + userDetails.getUsername(),
                        HttpStatus.NOT_FOUND));

        Subscription subscription = user.getSubscription();

        if (subscription == null) {
            throw new CustomException("해당 사용자 ID의 구독 정보를 찾을 수 없습니다: " + user.getId(), HttpStatus.NOT_FOUND);
        }

        subscription.updateStatus(SubscriptionStatus.CANCELLED);

        return new SubscriptionResponseDTO(subscription);
    }

    @Transactional
    public SubscriptionResponseDTO activateSubscription(UserDetails userDetails) {

        AppUser user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new CustomException("해당 사용자 이메일을 찾을 수 없습니다: " + userDetails.getUsername(),
                        HttpStatus.NOT_FOUND));

        Subscription subscription = user.getSubscription();

        if (subscription == null) {
            throw new CustomException("해당 사용자 ID의 구독 정보를 찾을 수 없습니다: " + user.getId(), HttpStatus.NOT_FOUND);
        }

        subscription.updateStatus(SubscriptionStatus.ACTIVE);

        boolean hasUsedFreeSubscription = freeSubscriptionHistoryRepository.existsByUserId(user.getId());
        return new SubscriptionResponseDTO(subscription, hasUsedFreeSubscription);
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptions() {
        return subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE).stream().map(SubscriptionResponseDTO::new)
                .collect(Collectors.toList());
    }

    public SubscriptionResponseDTO getMySubscription(UserDetails userDetails) {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("User not found with email: " + userDetails.getUsername(),
                        HttpStatus.NOT_FOUND));

        boolean hasUsedFreeSubscription = freeSubscriptionHistoryRepository.existsByUserId(user.getId());

        // 현재 구독이 있는 경우
        if (user.getSubscription() != null) {
            return new SubscriptionResponseDTO(user.getSubscription(), hasUsedFreeSubscription);
        }

        // 현재 구독이 없지만 free subscription history가 있는 경우
        if (hasUsedFreeSubscription) {
            return new SubscriptionResponseDTO(true); // INACTIVE, FREE, hasUsedFreeSubscription=true
        }

        // 구독이 없고 free subscription history도 없는 경우
        throw new CustomException("구독 정보가 없습니다", HttpStatus.NOT_FOUND);
    }

    public SubscriptionResponseDTO createFreeSubscription(UserDetails userDetails) {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("User not found with email: " + userDetails.getUsername(),
                        HttpStatus.NOT_FOUND));

        // 이미 구독이 있는 경우 예외 처리
        if (user.getSubscription() != null) {
            throw new CustomException("이미 구독 중인 사용자입니다", HttpStatus.BAD_REQUEST);
        }

        // 무료 요금제를 한번이라도 사용한 이력이 있는지 확인
        if (freeSubscriptionHistoryRepository.existsByUserId(user.getId())) {
            throw new CustomException("이미 무료 구독을 사용한 적이 있습니다", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = startDate.plusDays(30); // 무료 구독은 30일로 설정

        Subscription freeSubscription = Subscription.builder()
                .planName(SubscriptionType.FREE)
                .billingType(SubscriptionBillingType.FREE)
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .amount(0)
                .user(user)
                .build();

        Subscription savedSubscription = subscriptionRepository.save(freeSubscription);

        // 무료 구독 이력 저장
        FreeSubscriptionHistory history = FreeSubscriptionHistory.builder()
                .user(user)
                .subscriptionId(savedSubscription.getId())
                .build();
        freeSubscriptionHistoryRepository.save(history);

        return new SubscriptionResponseDTO(savedSubscription);
    }

    @Transactional
    public void deactivateSubscriptionDueToPaymentFailures(AppUser user, String reason) {
        if (user.getSubscription() != null && user.getSubscription().isActivated()) {
            user.getSubscription().updateStatus(SubscriptionStatus.INACTIVE);
            log.warn("구독 비활성화: 사용자={}, 원인={}", user.getEmail(), reason);
        }
    }
}