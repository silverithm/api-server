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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 규정·지침 대장의 문서 1건 — 원문이 아니라 항목 점검 결과(checks)와 개정일·비치 장소·교육일만 담는다. */
@Entity
@Table(name = "lab_policy_docs", indexes = @Index(
        name = "idx_lab_policy_docs_company_kind", columnList = "company_id, kind, guide_no"))
@Getter
@NoArgsConstructor
public class LabPolicyDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** OPERATING_RULES | CARE_GUIDE | PRIVACY_POLICY | INFECTION_PLAN | TRANSPORT_RULES | GRIEVANCE_GUIDE | OTHER */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    /** CARE_GUIDE일 때만 1~12 */
    @Column(name = "guide_no")
    private Byte guideNo;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "revised_on")
    private LocalDate revisedOn;

    @Column(name = "location", length = 100)
    private String location;

    /** {항목 키: boolean} JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checks", nullable = false)
    private String checks;

    @Column(name = "trained_on")
    private LocalDate trainedOn;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabPolicyDoc(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String kind, Byte guideNo, String title, LocalDate revisedOn, String location, String checks,
                     LocalDate trainedOn, String note) {
        this.kind = kind;
        this.guideNo = guideNo;
        this.title = title;
        this.revisedOn = revisedOn;
        this.location = location;
        this.checks = checks;
        this.trainedOn = trainedOn;
        this.note = note;
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
