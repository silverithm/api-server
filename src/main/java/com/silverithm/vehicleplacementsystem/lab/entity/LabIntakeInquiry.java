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

/** 이용 문의·입소 상담·대기 1건. 신청 어르신·문의자 이름, 연락처, 욕구는 암호화한다. */
@Entity
@Table(name = "lab_intake_inquiries", indexes = {
        @Index(name = "idx_lab_intake_company_status", columnList = "company_id, status"),
        @Index(name = "idx_lab_intake_elder", columnList = "elderly_id")})
@Getter
@NoArgsConstructor
public class LabIntakeInquiry {

    public static final String CONTRACTED = "CONTRACTED";
    public static final String DECLINED = "DECLINED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "inquiry_date", nullable = false)
    private LocalDate inquiryDate;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "applicant_name", nullable = false, length = 512)
    private String applicantName;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "contact_name", length = 512)
    private String contactName;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "contact_phone", length = 512)
    private String contactPhone;

    @Column(name = "relation", length = 20)
    private String relation;

    @Column(name = "care_grade", length = 10)
    private String careGrade;

    @Column(name = "desired_days", length = 60)
    private String desiredDays;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "needs", columnDefinition = "TEXT")
    private String needs;

    /** PHONE | VISIT | REFERRAL | WEB | OTHER */
    @Column(name = "source", nullable = false, length = 12)
    private String source;

    /** NEW | CONSULTED | WAITING | CONTRACTED | DECLINED | WITHDRAWN */
    @Column(name = "status", nullable = false, length = 12)
    private String status;

    /** CAPACITY | OUT_OF_AREA | SERVICE_MISMATCH | OTHER — DECLINED일 때만 */
    @Column(name = "decline_reason", length = 20)
    private String declineReason;

    @Column(name = "decline_note", length = 500)
    private String declineNote;

    @Column(name = "consent_on")
    private LocalDate consentOn;

    @Column(name = "next_contact_on")
    private LocalDate nextContactOn;

    @Column(name = "elderly_id")
    private Long elderlyId;

    @Column(name = "counselor_name", length = 50)
    private String counselorName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabIntakeInquiry(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate inquiryDate, String applicantName, String contactName, String contactPhone,
                     String relation, String careGrade, String desiredDays, String needs, String source,
                     String status, String declineReason, String declineNote, LocalDate consentOn,
                     LocalDate nextContactOn, Long elderlyId, String counselorName, boolean aiAssisted) {
        this.inquiryDate = inquiryDate;
        this.applicantName = applicantName;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.relation = relation;
        this.careGrade = careGrade;
        this.desiredDays = desiredDays;
        this.needs = needs;
        this.source = source;
        this.status = status;
        this.declineReason = declineReason;
        this.declineNote = declineNote;
        this.consentOn = consentOn;
        this.nextContactOn = nextContactOn;
        this.elderlyId = elderlyId;
        this.counselorName = counselorName;
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
