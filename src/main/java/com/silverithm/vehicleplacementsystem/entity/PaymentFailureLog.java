package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_failure_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentFailureLog extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    
    @Column(name = "subscription_id")
    private Long subscriptionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", nullable = false)
    private PaymentFailureReason failureReason;
    
    @Column(name = "failure_message", length = 1000)
    private String failureMessage;
    
    @Column(name = "attempted_amount")
    private Integer attemptedAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type")
    private SubscriptionType subscriptionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type")
    private SubscriptionBillingType billingType;
    
    @Column(name = "payment_gateway_response", length = 2000)
    private String paymentGatewayResponse;
    
    @Column(name = "is_scheduled_payment")
    private Boolean isScheduledPayment = false;
    
    @Builder
    public PaymentFailureLog(AppUser user, Long subscriptionId, PaymentFailureReason failureReason, 
                           String failureMessage, Integer attemptedAmount, SubscriptionType subscriptionType,
                           SubscriptionBillingType billingType, String paymentGatewayResponse, Boolean isScheduledPayment) {
        this.user = user;
        this.subscriptionId = subscriptionId;
        this.failureReason = failureReason;
        this.failureMessage = failureMessage;
        this.attemptedAmount = attemptedAmount;
        this.subscriptionType = subscriptionType;
        this.billingType = billingType;
        this.paymentGatewayResponse = paymentGatewayResponse;
        this.isScheduledPayment = isScheduledPayment != null ? isScheduledPayment : false;
    }
}