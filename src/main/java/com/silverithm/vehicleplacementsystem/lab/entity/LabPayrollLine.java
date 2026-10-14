package com.silverithm.vehicleplacementsystem.lab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 월 급여대장 한 행 — 기관 x 직원 x 월 1건. 금액은 원 단위 INT다.
 *
 * <p>값 검증(합계 일치·범위)은 서비스가 하고 이 엔티티는 담기만 한다. 상태 전이는 {@link #confirm}·{@link #reopen}
 * 으로만 한다 — 확정된 행의 수정·삭제 차단은 서비스가 이 상태를 보고 막는다.
 * 기타 수당·공제는 개인정보가 아닌 금액 항목이라 평문 JSON 문자열이다.
 */
@Entity
@Table(name = "lab_payroll_lines",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_payroll_lines_company_member_month",
                columnNames = {"company_id", "member_id", "pay_month"}),
        indexes = @Index(name = "idx_lab_payroll_lines_company_month", columnList = "company_id, pay_month"))
@Getter
@Setter
@NoArgsConstructor
public class LabPayrollLine {

    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long companyId;

    @Column(name = "member_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long memberId;

    @Column(name = "pay_month", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    @Setter(AccessLevel.NONE)
    private String payMonth;

    @Column(name = "regular_hours", nullable = false, precision = 5, scale = 1)
    private BigDecimal regularHours = BigDecimal.ZERO;

    @Column(name = "overtime_hours", nullable = false, precision = 5, scale = 1)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "night_hours", nullable = false, precision = 5, scale = 1)
    private BigDecimal nightHours = BigDecimal.ZERO;

    @Column(name = "holiday_hours", nullable = false, precision = 5, scale = 1)
    private BigDecimal holidayHours = BigDecimal.ZERO;

    @Column(name = "holiday_over8_hours", nullable = false, precision = 5, scale = 1)
    private BigDecimal holidayOver8Hours = BigDecimal.ZERO;

    @Column(name = "base_pay", nullable = false)
    private int basePay;

    @Column(name = "overtime_pay", nullable = false)
    private int overtimePay;

    @Column(name = "night_pay", nullable = false)
    private int nightPay;

    @Column(name = "holiday_pay", nullable = false)
    private int holidayPay;

    @Column(name = "long_service_award", nullable = false)
    private int longServiceAward;

    @Column(name = "senior_allowance", nullable = false)
    private int seniorAllowance;

    /** 기타 수당 JSON [{label, amount, taxable}] */
    @Column(name = "other_allowances", columnDefinition = "TEXT")
    private String otherAllowances;

    @Column(name = "meal_allowance", nullable = false)
    private int mealAllowance;

    @Column(name = "gross_pay", nullable = false)
    private int grossPay;

    @Column(name = "taxable_pay", nullable = false)
    private int taxablePay;

    @Column(name = "nps", nullable = false)
    private int nps;

    @Column(name = "health", nullable = false)
    private int health;

    @Column(name = "ltc", nullable = false)
    private int ltc;

    @Column(name = "employment", nullable = false)
    private int employment;

    @Column(name = "income_tax", nullable = false)
    private int incomeTax;

    @Column(name = "local_tax", nullable = false)
    private int localTax;

    /** 기타 공제 JSON [{label, amount}] */
    @Column(name = "other_deductions", columnDefinition = "TEXT")
    private String otherDeductions;

    @Column(name = "net_pay", nullable = false)
    private int netPay;

    @Column(name = "employer_nps", nullable = false)
    private int employerNps;

    @Column(name = "employer_health", nullable = false)
    private int employerHealth;

    @Column(name = "employer_ltc", nullable = false)
    private int employerLtc;

    @Column(name = "employer_employment", nullable = false)
    private int employerEmployment;

    /** DRAFT | CONFIRMED */
    @Column(name = "status", nullable = false, length = 10)
    @Setter(AccessLevel.NONE)
    private String status = DRAFT;

    @Column(name = "confirmed_at")
    @Setter(AccessLevel.NONE)
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    public LabPayrollLine(Long companyId, Long memberId, String payMonth) {
        this.companyId = companyId;
        this.memberId = memberId;
        this.payMonth = payMonth;
    }

    public boolean isConfirmed() {
        return CONFIRMED.equals(status);
    }

    public void confirm() {
        this.status = CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void reopen() {
        this.status = DRAFT;
        this.confirmedAt = null;
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
