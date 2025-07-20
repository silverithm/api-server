package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.BillingResponse;
import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
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

    public SubscriptionResponseDTO createOrUpdateSubscription(UserDetails userDetails,
                                                              SubscriptionRequestDTO requestDto) {
        AppUser user = findUserByEmail(userDetails.getUsername());
        billingService.ensureBillingKey(user, requestDto);
        billingService.processPayment(requestDto, user.getBillingKey());
        return subscriptionTransactionService.processSubscription(user, requestDto);
    }

    public SubscriptionResponseDTO processSubscription(AppUser user, SubscriptionRequestDTO requestDto) {
        return subscriptionTransactionService.processSubscription(user, requestDto);
    }


    private AppUser findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found with email: " + email, HttpStatus.NOT_FOUND));
    }


    @Transactional
    public SubscriptionResponseDTO createSubscriptionToUser(SubscriptionRequestDTO requestDto, Long userId) {

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with userId: " + userId));

        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = SubscriptionBillingType.calculateEndDate(requestDto.getBillingType());

        Subscription subscription = Subscription.builder().planName(requestDto.getPlanName())
                .billingType(requestDto.getBillingType()).startDate(startDate).endDate(endDate)
                .status(SubscriptionStatus.ACTIVE).amount(requestDto.getAmount()).user(user).build();

        return new SubscriptionResponseDTO(subscriptionRepository.save(subscription));
    }


    public SubscriptionResponseDTO getSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));
        return new SubscriptionResponseDTO(subscription);
    }

    @Transactional
    public SubscriptionResponseDTO cancelSubscription(UserDetails userDetails) {

        AppUser user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new CustomException("User not found with userEmail: " + userDetails.getUsername(),
                        HttpStatus.NOT_FOUND));

        Subscription subscription = user.getSubscription();

        if (subscription == null) {
            throw new CustomException("Subscription not found with userId: " + user.getId(), HttpStatus.NOT_FOUND);
        }

        subscription.updateStatus(SubscriptionStatus.CANCELLED);
        subscription.updateEndDate(LocalDateTime.now());

        return new SubscriptionResponseDTO(subscription);
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptions() {
        return subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE).stream().map(SubscriptionResponseDTO::new)
                .collect(Collectors.toList());
    }

    public SubscriptionResponseDTO getMySubscription(UserDetails userDetails) {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("User not found with email: " + userDetails.getUsername(),
                        HttpStatus.NOT_FOUND));




        return Optional.ofNullable(user.getSubscription())
                .map(SubscriptionResponseDTO::new)
                .orElseThrow(() -> new CustomException("No subscription found", HttpStatus.NOT_FOUND));
    }

    public SubscriptionResponseDTO createFreeSubscription(UserDetails userDetails) {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("User not found with email: " + userDetails.getUsername(),
                        HttpStatus.NOT_FOUND));

        // 이미 구독이 있는 경우 예외 처리
        if (user.getSubscription() != null) {
            throw new CustomException("User already has a subscription", HttpStatus.BAD_REQUEST);
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

        return new SubscriptionResponseDTO(subscriptionRepository.save(freeSubscription));
    }
}