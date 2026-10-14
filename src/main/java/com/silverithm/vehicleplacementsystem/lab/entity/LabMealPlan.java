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

/** 주간 식단 — 기관 x 주(월요일 시작) 1건. 어르신과 무관하고 개인정보가 없어 암호화하지 않는다. */
@Entity
@Table(name = "lab_meal_plans", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_meal_plans_company_week", columnNames = {"company_id", "week_start"}))
@Getter
@NoArgsConstructor
public class LabMealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    /** 월~토 6일 JSON 배열 */
    @Column(name = "days", nullable = false, columnDefinition = "TEXT")
    private String days;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabMealPlan(Long companyId, LocalDate weekStart) {
        this.companyId = companyId;
        this.weekStart = weekStart;
    }

    public void fill(String days, String notes, String writerName, boolean aiAssisted) {
        this.days = days;
        this.notes = notes;
        this.writerName = writerName;
        this.aiAssisted = aiAssisted;
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
