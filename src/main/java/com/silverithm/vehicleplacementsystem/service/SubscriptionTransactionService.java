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
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionTransactionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public SubscriptionResponseDTO processSubscription(AppUser user, SubscriptionRequestDTO requestDto) {
        if (user.getSubscription() != null) {
            log.info("Subscription exists for user: {}", PrivacyMask.name(user.getUsername()));
            return updateSubscription(user.getSubscription(), requestDto);
        }
        log.info("Subscription does not exist for user: {}", PrivacyMask.name(user.getUsername()));
        return createSubscription(requestDto, user);
    }

    private SubscriptionResponseDTO updateSubscription(Subscription subscription, SubscriptionRequestDTO requestDto) {
        // 연장 기준: 아직 만료 전이면 남은 기간을 보존해 endDate에서, 만료 후 재결제면 지금부터.
        // (과거엔 startDate 기준으로 계산해 정기결제가 성공해도 endDate가 늘지 않고
        //  다음날 또 결제 대상이 되는 이중 청구 버그가 있었다)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = subscription.getEndDate() != null && subscription.getEndDate().isAfter(now)
                ? subscription.getEndDate()
                : now;
        // 앵커 데이(최초 가입일의 '일')를 보존해 결제일 드리프트를 막는다
        int anchorDay = subscription.getStartDate() != null
                ? subscription.getStartDate().getDayOfMonth()
                : base.getDayOfMonth();
        LocalDateTime extendedEndDate = SubscriptionBillingType.extendEndDate(
                requestDto.getBillingType(), base, anchorDay);
        log.info("Extending subscription for user: {}, current endDate: {}, new endDate: {}", 
                subscription.getUser().getUsername(), subscription.getEndDate(), extendedEndDate);
        subscription.update(requestDto.getPlanName(), requestDto.getBillingType(), requestDto.getAmount(), extendedEndDate,
                SubscriptionStatus.ACTIVE);
        
        // 명시적으로 save 호출하여 변경사항 저장
        Subscription savedSubscription = subscriptionRepository.save(subscription);
        log.info("Subscription saved successfully for user: {}, endDate: {}", 
                savedSubscription.getUser().getUsername(), savedSubscription.getEndDate());
        
        return new SubscriptionResponseDTO(savedSubscription);
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