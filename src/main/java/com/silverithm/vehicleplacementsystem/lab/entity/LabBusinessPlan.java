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

/** 연간 사업계획서 — 기관 x 연도 1건. 분야별 항목은 JSON 문자열이고 개인정보가 없어 암호화하지 않는다. */
@Entity
@Table(name = "lab_business_plans", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_business_plans_company_year", columnNames = {"company_id", "plan_year"}))
@Getter
@NoArgsConstructor
public class LabBusinessPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "plan_year", nullable = false)
    private short planYear;

    @Column(name = "sections", nullable = false, columnDefinition = "TEXT")
    private String sections;

    @Column(name = "total_income")
    private Long totalIncome;

    @Column(name = "total_expense")
    private Long totalExpense;

    @Column(name = "written_on")
    private LocalDate writtenOn;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabBusinessPlan(Long companyId, short planYear) {
        this.companyId = companyId;
        this.planYear = planYear;
    }

    public void fill(String sections, Long totalIncome, Long totalExpense, LocalDate writtenOn, String writerName,
                     boolean aiAssisted) {
        this.sections = sections;
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
        this.writtenOn = writtenOn;
        this.writerName = writerName;
        this.aiAssisted = aiAssisted;
    }

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
