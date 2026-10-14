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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 급여 만족도 응답 1건. 익명이면 elderlyId가 null이고, 의견은 암호화한다. */
@Entity
@Table(name = "lab_satisfaction_responses", indexes = {
        @Index(name = "idx_lab_satisfaction_company_kind_date", columnList = "company_id, kind, survey_date"),
        @Index(name = "idx_lab_satisfaction_elder", columnList = "elderly_id")})
@Getter
@NoArgsConstructor
public class LabSatisfactionResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "survey_date", nullable = false)
    private LocalDate surveyDate;

    /** MEAL | PROGRAM | TRANSPORT | GENERAL */
    @Column(name = "kind", nullable = false, length = 12)
    private String kind;

    @Column(name = "elderly_id")
    private Long elderlyId;

    /** SELF | GUARDIAN */
    @Column(name = "respondent", nullable = false, length = 10)
    private String respondent;

    /** JSON {문항코드: 1~5} */
    @Column(name = "scores", nullable = false, columnDefinition = "TEXT")
    private String scores;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "interviewer_name", length = 50)
    private String interviewerName;

    public LabSatisfactionResponse(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate surveyDate, String kind, Long elderlyId, String respondent, String scores,
                     String comment, String interviewerName) {
        this.surveyDate = surveyDate;
        this.kind = kind;
        this.elderlyId = elderlyId;
        this.respondent = respondent;
        this.scores = scores;
        this.comment = comment;
        this.interviewerName = interviewerName;
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
