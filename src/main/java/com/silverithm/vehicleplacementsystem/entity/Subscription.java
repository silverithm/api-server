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
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionType planName;  // Basic, Enterprise 등

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionBillingType billingType;  // monthly, yearly

    @Column(nullable = false)
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Column(nullable = false)
    private SubscriptionStatus status;  // ACTIVE, CANCELLED 등

    @Column(nullable = false)
    private Integer amount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    public void updateEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public void updateStatus(SubscriptionStatus status) {
        this.status = status;
    }

    @Builder
    public Subscription(SubscriptionType planName, SubscriptionBillingType billingType, LocalDateTime startDate,
                        LocalDateTime endDate, SubscriptionStatus status, Integer amount, AppUser user) {
        this.planName = planName;
        this.billingType = billingType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.amount = amount;
        this.user = user;
    }

    public void update(SubscriptionType planName, SubscriptionBillingType billingType, Integer amount,
                       LocalDateTime endDate, SubscriptionStatus status) {
        this.planName = planName;
        this.billingType = billingType;
        this.amount = amount;
        this.startDate = LocalDateTime.now();
        updateEndDate(endDate);
        updateStatus(status);
    }

    public Boolean isActivated() {
        return this.status.equals(SubscriptionStatus.ACTIVE);
    }

    public boolean isFreeUser() {
        return this.planName.equals(SubscriptionType.FREE);
    }
}