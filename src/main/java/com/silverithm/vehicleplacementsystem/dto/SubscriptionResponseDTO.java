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

    public SubscriptionResponseDTO(Subscription subscription) {
        this.id = subscription.getId();
        this.planName = subscription.getPlanName();
        this.billingType = subscription.getBillingType();
        this.startDate = subscription.getStartDate();
        this.endDate = subscription.getEndDate();
        this.status = subscription.getStatus();
        this.amount = subscription.getAmount();
    }
}