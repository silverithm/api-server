package com.silverithm.vehicleplacementsystem.lab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 본인부담금 청구서 — 어르신 1명 x 월 1건. 금액은 모두 원 단위 정수.
 *
 * <p>nonCoveredAmount·totalDue·status는 서비스가 입력 부품에서 다시 계산해 넣는 파생값이다.
 * 수납액(paidAmount)은 청구 내용을 다시 저장해도 지워지지 않아야 하므로 {@link #fillBill}이 건드리지 않는다.
 */
@Entity
@Table(name = "lab_copay_bills", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_copay_bills_elder_month", columnNames = {"elderly_id", "bill_month"}))
@Getter
@NoArgsConstructor
public class LabCopayBill {

    public static final String UNPAID = "UNPAID";
    public static final String PARTIAL = "PARTIAL";
    public static final String PAID = "PAID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "bill_month", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String billMonth;

    @Column(name = "care_grade", nullable = false, length = 20)
    private String careGrade;

    @Column(name = "copay_class", nullable = false, length = 20)
    private String copayClass;

    @Column(name = "daycare_unit", nullable = false, length = 20)
    private String daycareUnit;

    @Column(name = "hour_band", nullable = false, length = 20)
    private String hourBand;

    @Column(name = "days", nullable = false)
    private int days;

    @Column(name = "daily_fee", nullable = false)
    private int dailyFee;

    @Column(name = "covered_total", nullable = false)
    private int coveredTotal;

    @Column(name = "copay_amount", nullable = false)
    private int copayAmount;

    @Column(name = "over_limit_amount", nullable = false)
    private int overLimitAmount;

    /** 비급여 항목 JSON 배열 — 개인을 알아볼 수 없는 항목명·금액이라 암호화하지 않는다 */
    @Column(name = "non_covered_items", columnDefinition = "TEXT")
    private String nonCoveredItems;

    @Column(name = "non_covered_amount", nullable = false)
    private int nonCoveredAmount;

    @Column(name = "previous_unpaid", nullable = false)
    private int previousUnpaid;

    @Column(name = "total_due", nullable = false)
    private int totalDue;

    @Column(name = "paid_amount", nullable = false)
    private int paidAmount;

    @Column(name = "last_paid_on")
    private LocalDate lastPaidOn;

    @Column(name = "payer_name", length = 50)
    private String payerName;

    @Column(name = "status", nullable = false, length = 20)
    private String status = UNPAID;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabCopayBill(Long companyId, Long elderlyId, String billMonth) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.billMonth = billMonth;
    }

    /** 청구 내용을 덮어쓴다. 수납 내역은 그대로 두고 상태만 새 청구액 기준으로 다시 맞춘다. */
    public void fillBill(String careGrade, String copayClass, String daycareUnit, String hourBand, int days,
                         int dailyFee, int coveredTotal, int copayAmount, int overLimitAmount,
                         String nonCoveredItemsJson, int nonCoveredAmount, int previousUnpaid, int totalDue) {
        this.careGrade = careGrade;
        this.copayClass = copayClass;
        this.daycareUnit = daycareUnit;
        this.hourBand = hourBand;
        this.days = days;
        this.dailyFee = dailyFee;
        this.coveredTotal = coveredTotal;
        this.copayAmount = copayAmount;
        this.overLimitAmount = overLimitAmount;
        this.nonCoveredItems = nonCoveredItemsJson;
        this.nonCoveredAmount = nonCoveredAmount;
        this.previousUnpaid = previousUnpaid;
        this.totalDue = totalDue;
        this.status = statusOf(totalDue, paidAmount);
    }

    /** 수납을 더한다. 여러 번에 나눠 내는 경우가 흔해 덮어쓰지 않고 누적한다. */
    public void addPayment(int amount, LocalDate paidOn, String payer) {
        this.paidAmount = Math.addExact(this.paidAmount, amount);
        if (paidOn != null && (this.lastPaidOn == null || paidOn.isAfter(this.lastPaidOn))) {
            this.lastPaidOn = paidOn;
        }
        if (payer != null) {
            this.payerName = payer;
        }
        this.status = statusOf(totalDue, paidAmount);
    }

    /** 청구액 0원이거나 낸 돈이 청구액 이상이면 완납, 일부라도 냈으면 부분 수납 */
    public static String statusOf(int totalDue, int paidAmount) {
        if (totalDue == 0 || paidAmount >= totalDue) {
            return PAID;
        }
        return paidAmount > 0 ? PARTIAL : UNPAID;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
