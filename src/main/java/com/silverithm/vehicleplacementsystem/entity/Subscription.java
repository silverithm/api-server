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

    public void updateStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
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
        // startDate(최초 가입일)는 갱신 시 보존한다 — 결제일 앵커(가입일의 '일')와 이력의 기준점.
        // 매 갱신마다 now로 리셋하면 앵커가 사라져 결제일이 조금씩 흘러내린다.
        updateEndDate(endDate);
        updateStatus(status);
    }

    public Boolean isActivated() {
        return this.status.equals(SubscriptionStatus.ACTIVE);
    }

    /**
     * endDate 기준 실제 만료 여부 (1일 유예).
     * 유료 정기결제는 endDate 당일 새벽 6시 배치에서 갱신되므로, 자정~결제 사이의
     * 정상 사용자가 만료로 오판되지 않도록 하루의 여유를 둔다.
     */
    public boolean isExpiredByDate(LocalDateTime now) {
        return this.endDate != null && now.isAfter(this.endDate.plusDays(1));
    }

    /** 만료 상태로 전이 — 읽기 시점 lazy expiration에서 사용 */
    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
    }

    public boolean isFreeUser() {
        return this.planName.equals(SubscriptionType.FREE);
    }
}