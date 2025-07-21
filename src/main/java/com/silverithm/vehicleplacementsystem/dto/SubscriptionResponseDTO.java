package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class SubscriptionResponseDTO {
    private Long id;
    private SubscriptionType planName;
    private SubscriptionBillingType billingType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private SubscriptionStatus status;
    private Integer amount;
    private Boolean hasUsedFreeSubscription;

    public SubscriptionResponseDTO(Subscription subscription) {
        this.id = subscription.getId();
        this.planName = subscription.getPlanName();
        this.billingType = subscription.getBillingType();
        this.startDate = subscription.getStartDate();
        this.endDate = subscription.getEndDate();
        this.status = subscription.getStatus();
        this.amount = subscription.getAmount();
    }
    
    public SubscriptionResponseDTO(Subscription subscription, Boolean hasUsedFreeSubscription) {
        this.id = subscription.getId();
        this.planName = subscription.getPlanName();
        this.billingType = subscription.getBillingType();
        this.startDate = subscription.getStartDate();
        this.endDate = subscription.getEndDate();
        this.status = subscription.getStatus();
        this.amount = subscription.getAmount();
        this.hasUsedFreeSubscription = hasUsedFreeSubscription;
    }

    public SubscriptionResponseDTO() {
        this.planName = SubscriptionType.FREE;
        this.billingType = SubscriptionBillingType.FREE;
        this.status = SubscriptionStatus.INACTIVE;
    }
    
    // free subscription history가 있을 때 사용할 생성자
    public SubscriptionResponseDTO(Boolean hasUsedFreeSubscription) {
        this.id = null;
        this.planName = SubscriptionType.FREE;
        this.billingType = SubscriptionBillingType.FREE;
        this.startDate = null;
        this.endDate = null;
        this.status = SubscriptionStatus.INACTIVE;
        this.amount = 0;
        this.hasUsedFreeSubscription = hasUsedFreeSubscription;
    }


}