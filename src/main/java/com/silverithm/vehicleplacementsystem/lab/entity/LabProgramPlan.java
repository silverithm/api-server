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

/** 프로그램 연간계획 — 기관 x 연도 x 분야 1건. 개인정보가 없어 암호화하지 않는다. */
@Entity
@Table(name = "lab_program_plans", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_program_plans_company_year_category", columnNames = {"company_id", "plan_year", "category"}))
@Getter
@NoArgsConstructor
public class LabProgramPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "plan_year", nullable = false)
    private short planYear;

    /** PHYSICAL | COGNITIVE | SOCIAL */
    @Column(name = "category", nullable = false, length = 12)
    private String category;

    @Column(name = "goal", nullable = false, columnDefinition = "TEXT")
    private String goal;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "frequency", nullable = false, length = 40)
    private String frequency;

    @Column(name = "target", length = 100)
    private String target;

    @Column(name = "budget")
    private Long budget;

    @Column(name = "written_on", nullable = false)
    private LocalDate writtenOn;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabProgramPlan(Long companyId, short planYear, String category) {
        this.companyId = companyId;
        this.planYear = planYear;
        this.category = category;
    }

    public void fill(String goal, String content, String frequency, String target, Long budget, LocalDate writtenOn,
                     String writerName, boolean aiAssisted) {
        this.goal = goal;
        this.content = content;
        this.frequency = frequency;
        this.target = target;
        this.budget = budget;
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
