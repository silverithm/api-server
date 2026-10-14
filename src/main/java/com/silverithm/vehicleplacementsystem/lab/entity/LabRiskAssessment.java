package com.silverithm.vehicleplacementsystem.lab.entity;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 위험도 평가(욕창 BRADEN · 낙상 MORSE) 한 번의 결과.
 *
 * <p>평가는 덮어쓰지 않고 쌓는다 — 반기마다 했다는 이력 자체가 기관평가 근거다.
 * total·riskLevel은 서버가 scores에서 계산해 넣는다.
 */
@Entity
@Table(name = "lab_risk_assessments", indexes = @Index(
        name = "idx_lab_risk_assessments_elder_kind_date", columnList = "elderly_id, kind, assessed_on"))
@Getter
@NoArgsConstructor
public class LabRiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    /** BRADEN | MORSE */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** 항목별 점수 JSON 객체. 점수만 담겨 있어 암호화하지 않는다 */
    @Column(name = "scores", nullable = false, columnDefinition = "TEXT")
    private String scores;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "assessed_on", nullable = false)
    private LocalDate assessedOn;

    @Column(name = "assessor_name", length = 50)
    private String assessorName;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LabRiskAssessment(Long companyId, Long elderlyId, String kind, String scoresJson, int total,
                             String riskLevel, LocalDate assessedOn, String assessorName, String note,
                             boolean aiAssisted) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.kind = kind;
        this.scores = scoresJson;
        this.total = total;
        this.riskLevel = riskLevel;
        this.assessedOn = assessedOn;
        this.assessorName = assessorName;
        this.note = note;
        this.aiAssisted = aiAssisted;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
