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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 위생·안전·시설 점검 — 기관 x 종류 x 날짜 1건. 개인정보가 없어 암호화하지 않는다. */
@Entity
@Table(name = "lab_safety_checks",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_safety_checks_company_kind_date",
                columnNames = {"company_id", "kind", "check_date"}),
        indexes = @Index(name = "idx_lab_safety_checks_company_date", columnList = "company_id, check_date"))
@Getter
@NoArgsConstructor
public class LabSafetyCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    /** KITCHEN | HYGIENE | FIRE | FACILITY | VEHICLE | INFECTION | DRILL */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    /** 점검 항목 JSON 배열 [{code, ok, note}] */
    @Column(name = "items", nullable = false, columnDefinition = "TEXT")
    private String items;

    @Column(name = "actions", columnDefinition = "TEXT")
    private String actions;

    @Column(name = "inspector_name", length = 50)
    private String inspectorName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabSafetyCheck(Long companyId, String kind, LocalDate checkDate) {
        this.companyId = companyId;
        this.kind = kind;
        this.checkDate = checkDate;
    }

    public void fill(String items, String actions, String inspectorName, boolean aiAssisted) {
        this.items = items;
        this.actions = actions;
        this.inspectorName = inspectorName;
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
