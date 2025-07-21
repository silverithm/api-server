package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionTransactionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public SubscriptionResponseDTO processSubscription(AppUser user, SubscriptionRequestDTO requestDto) {
        if (user.getSubscription() != null) {
            log.info("Subscription exists for user: {}", user.getUsername());
            return updateSubscription(user.getSubscription(), requestDto);
        }
        log.info("Subscription does not exist for user: {}", user.getUsername());
        return createSubscription(requestDto, user);
    }

    private SubscriptionResponseDTO updateSubscription(Subscription subscription, SubscriptionRequestDTO requestDto) {
        LocalDateTime extendedEndDate = SubscriptionBillingType.extendEndDate(requestDto.getBillingType(), 
                subscription.getEndDate());
        log.info("Extending subscription for user: {}, current endDate: {}, new endDate: {}", 
                subscription.getUser().getUsername(), subscription.getEndDate(), extendedEndDate);
        subscription.update(requestDto.getPlanName(), requestDto.getBillingType(), requestDto.getAmount(), extendedEndDate,
                SubscriptionStatus.ACTIVE);
        return new SubscriptionResponseDTO(subscription);
    }

    private SubscriptionResponseDTO createSubscription(SubscriptionRequestDTO requestDto, AppUser user) {
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = SubscriptionBillingType.calculateEndDate(requestDto.getBillingType());
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

}