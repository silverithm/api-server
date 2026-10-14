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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 퇴소 연계기록지 1건(어르신 x 퇴소일). 심신기능·제공 급여·퇴소 후 계획·상담·수령자는 건강·개인 정보라 암호화한다.
 * 계약 id는 일반 열이다 — 계약을 지우면 DB가 비운다(ON DELETE SET NULL).
 */
@Entity
@Table(name = "lab_discharge_referrals",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_discharge_referrals_elder_discharge",
                columnNames = {"elderly_id", "discharge_on"}),
        indexes = {
                @Index(name = "idx_lab_discharge_referrals_company_discharge", columnList = "company_id, discharge_on"),
                @Index(name = "idx_lab_discharge_referrals_contract", columnList = "contract_id")})
@Getter
@NoArgsConstructor
public class LabDischargeReferral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "discharge_on", nullable = false)
    private LocalDate dischargeOn;

    /** TRANSFER | HOSPITAL | SELF | OTHER */
    @Column(name = "reason", nullable = false, length = 20)
    private String reason;

    @Column(name = "written_on", nullable = false)
    private LocalDate writtenOn;

    @Column(name = "care_grade", nullable = false, length = 10)
    private String careGrade;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "functional_status", nullable = false, columnDefinition = "TEXT")
    private String functionalStatus;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "services_provided", nullable = false, columnDefinition = "TEXT")
    private String servicesProvided;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "after_plan", nullable = false, columnDefinition = "TEXT")
    private String afterPlan;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "counsel_note", columnDefinition = "TEXT")
    private String counselNote;

    @Column(name = "provided_on")
    private LocalDate providedOn;

    /** VISIT | MAIL | FAX | ELECTRONIC | OTHER */
    @Column(name = "provided_method", length = 12)
    private String providedMethod;

    @Column(name = "provider_name", length = 50)
    private String providerName;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "recipient_name", length = 512)
    private String recipientName;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabDischargeReferral(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, Long contractId, LocalDate dischargeOn, String reason, LocalDate writtenOn,
                     String careGrade, String functionalStatus, String servicesProvided, String afterPlan,
                     String counselNote, LocalDate providedOn, String providedMethod, String providerName,
                     String recipientName, String writerName, boolean aiAssisted) {
        this.elderlyId = elderlyId;
        this.contractId = contractId;
        this.dischargeOn = dischargeOn;
        this.reason = reason;
        this.writtenOn = writtenOn;
        this.careGrade = careGrade;
        this.functionalStatus = functionalStatus;
        this.servicesProvided = servicesProvided;
        this.afterPlan = afterPlan;
        this.counselNote = counselNote;
        this.providedOn = providedOn;
        this.providedMethod = providedMethod;
        this.providerName = providerName;
        this.recipientName = recipientName;
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
