package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.PaymentFailureLog;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentFailureResponseDTO {
    
    private final Long id;
    private final Long subscriptionId;
    private final PaymentFailureReason failureReason;
    private final String failureReasonDescription;
    private final String failureMessage;
    private final Integer attemptedAmount;
    private final SubscriptionType subscriptionType;
    private final SubscriptionBillingType billingType;
    private final LocalDateTime failedAt;
    
    public PaymentFailureResponseDTO(PaymentFailureLog paymentFailureLog) {
        this.id = paymentFailureLog.getId();
        this.subscriptionId = paymentFailureLog.getSubscriptionId();
        this.failureReason = paymentFailureLog.getFailureReason();
        this.failureReasonDescription = paymentFailureLog.getFailureReason().getDescription();
        this.failureMessage = paymentFailureLog.getFailureMessage();
        this.attemptedAmount = paymentFailureLog.getAttemptedAmount();
        this.subscriptionType = paymentFailureLog.getSubscriptionType();
        this.billingType = paymentFailureLog.getBillingType();
        this.failedAt = paymentFailureLog.getCreatedAt();
    }
}