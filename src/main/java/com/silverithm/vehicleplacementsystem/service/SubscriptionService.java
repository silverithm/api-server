package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public SubscriptionResponseDTO createOrUpdateSubscription(UserDetails userDetails,
                                                              SubscriptionRequestDTO requestDto) {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(
                        () -> new IllegalArgumentException("User not found with email: " + userDetails.getUsername()));

        return Optional.ofNullable(user.getSubscription())
                .map(subscription -> updateSubscription(subscription, requestDto))
                .orElseGet(() -> createNewSubscription(requestDto, user));
    }

    private SubscriptionResponseDTO updateSubscription(Subscription subscription, SubscriptionRequestDTO requestDto) {
        LocalDateTime endDate = calculateEndDate(requestDto.getBillingType());
        subscription.update(requestDto.getPlanName(), requestDto.getBillingType(),
                requestDto.getAmount(), endDate, SubscriptionStatus.ACTIVE);
        return new SubscriptionResponseDTO(subscription);
    }

    private SubscriptionResponseDTO createNewSubscription(SubscriptionRequestDTO requestDto, AppUser user) {
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(requestDto.getBillingType());

        Subscription subscription = Subscription.builder()
                .planName(requestDto.getPlanName())
                .billingType(requestDto.getBillingType())
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .amount(requestDto.getAmount())
                .user(user)
                .build();

        return new SubscriptionResponseDTO(subscriptionRepository.save(subscription));
    }

    private LocalDateTime calculateEndDate(SubscriptionBillingType billingType) {
        return billingType == SubscriptionBillingType.MONTHLY ?
                LocalDateTime.now().plusMonths(1) : LocalDateTime.now().plusYears(1);
    }

    public SubscriptionResponseDTO getSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));
        return new SubscriptionResponseDTO(subscription);
    }

    @Transactional
    public SubscriptionResponseDTO cancelSubscription(UserDetails userDetails, Long id) {

        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));

        if (!subscription.getUser().getEmail().equals(userDetails.getUsername())) {
            throw new IllegalArgumentException(
                    "User " + userDetails.getUsername() + " is not authorized to access subscription: " + id);
        }

        subscription.updateStatus(SubscriptionStatus.CANCELLED);
        subscription.updateEndDate(LocalDateTime.now());

        return new SubscriptionResponseDTO(subscription);
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptions() {
        return subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE).stream()
                .map(SubscriptionResponseDTO::new)
                .collect(Collectors.toList());
    }
}