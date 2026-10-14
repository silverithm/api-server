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
 * 외부기관·지역자원 연계 기록. elderly_id가 비어 있으면 어르신 한 명이 아니라 기관 전체의 연계다.
 *
 * <p>담당자 이름·연계 내용·결과는 암호화한다. referral_given은 연계 결과를 보호자에게 알렸는지 여부다(2026 평가의 퇴소 연계기록지와는 별개).
 */
@Entity
@Table(name = "lab_resource_links", indexes = {
        @Index(name = "idx_lab_resource_links_company_date", columnList = "company_id, linked_on"),
        @Index(name = "idx_lab_resource_links_elder", columnList = "elderly_id")})
@Getter
@NoArgsConstructor
public class LabResourceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 기관 전체 연계면 null */
    @Column(name = "elderly_id")
    private Long elderlyId;

    @Column(name = "linked_on", nullable = false)
    private LocalDate linkedOn;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "institution", nullable = false, length = 100)
    private String institution;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "contact_person", length = 512)
    private String contactPerson;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "outcome", columnDefinition = "TEXT")
    private String outcome;

    @Column(name = "follow_up_on")
    private LocalDate followUpOn;

    @Column(name = "referral_given", nullable = false)
    private boolean referralGiven;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabResourceLink(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, LocalDate linkedOn, String resourceType, String institution,
                     String contactPerson, String content, String outcome, LocalDate followUpOn,
                     boolean referralGiven, String writerName, boolean aiAssisted) {
        this.elderlyId = elderlyId;
        this.linkedOn = linkedOn;
        this.resourceType = resourceType;
        this.institution = institution;
        this.contactPerson = contactPerson;
        this.content = content;
        this.outcome = outcome;
        this.followUpOn = followUpOn;
        this.referralGiven = referralGiven;
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
