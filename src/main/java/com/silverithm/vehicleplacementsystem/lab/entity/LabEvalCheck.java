package com.silverithm.vehicleplacementsystem.lab.entity;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기관평가 준비 점검 — 기관 x 평가지표 1건. 어르신과 무관하다. */
@Entity
@Table(name = "lab_eval_checks", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_eval_checks_company_code", columnNames = {"company_id", "indicator_code"}))
@Getter
@NoArgsConstructor
public class LabEvalCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "indicator_code", nullable = false, length = 40)
    private String indicatorCode;

    /** READY | PARTIAL | MISSING | NOT_APPLICABLE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "ai_comment", columnDefinition = "TEXT")
    private String aiComment;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabEvalCheck(Long companyId, String indicatorCode) {
        this.companyId = companyId;
        this.indicatorCode = indicatorCode;
    }

    public void fill(String status, String evidence, String aiComment, String updatedBy) {
        this.status = status;
        this.evidence = evidence;
        this.aiComment = aiComment;
        this.updatedBy = updatedBy;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
