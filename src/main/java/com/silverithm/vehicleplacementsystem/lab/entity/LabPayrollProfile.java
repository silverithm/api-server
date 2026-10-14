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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 직원 1명의 급여 기준 — 기관 x 직원 1건. member_id는 같은 기관 직원이어야 하며 서비스가 확인한다.
 * SMALLINT 열은 Short로 받아 Hibernate 스키마 검증이 INTEGER/SMALLINT 불일치로 실패하지 않게 한다.
 */
@Entity
@Table(name = "lab_payroll_profiles", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_payroll_profiles_company_member", columnNames = {"company_id", "member_id"}))
@Getter
@NoArgsConstructor
public class LabPayrollProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "job_type", nullable = false, length = 20)
    private String jobType;

    @Column(name = "pay_type", nullable = false, length = 10)
    private String payType;

    @Column(name = "base_amount", nullable = false)
    private int baseAmount;

    @Column(name = "weekly_hours", nullable = false, precision = 4, scale = 1)
    private BigDecimal weeklyHours;

    @Column(name = "dependents", nullable = false)
    private Short dependents;

    @Column(name = "children", nullable = false)
    private Short children;

    @Column(name = "withholding_rate", nullable = false)
    private Short withholdingRate;

    @Column(name = "meal_allowance", nullable = false)
    private int mealAllowance;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "senior_caregiver", nullable = false)
    private boolean seniorCaregiver;

    @Column(name = "nps_enrolled", nullable = false)
    private boolean npsEnrolled;

    @Column(name = "health_enrolled", nullable = false)
    private boolean healthEnrolled;

    @Column(name = "employment_enrolled", nullable = false)
    private boolean employmentEnrolled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabPayrollProfile(Long companyId, Long memberId) {
        this.companyId = companyId;
        this.memberId = memberId;
    }

    public void fill(String jobType, String payType, int baseAmount, BigDecimal weeklyHours, short dependents,
                     short children, short withholdingRate, int mealAllowance, LocalDate hireDate,
                     boolean seniorCaregiver, boolean npsEnrolled, boolean healthEnrolled,
                     boolean employmentEnrolled) {
        this.jobType = jobType;
        this.payType = payType;
        this.baseAmount = baseAmount;
        this.weeklyHours = weeklyHours;
        this.dependents = dependents;
        this.children = children;
        this.withholdingRate = withholdingRate;
        this.mealAllowance = mealAllowance;
        this.hireDate = hireDate;
        this.seniorCaregiver = seniorCaregiver;
        this.npsEnrolled = npsEnrolled;
        this.healthEnrolled = healthEnrolled;
        this.employmentEnrolled = employmentEnrolled;
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
