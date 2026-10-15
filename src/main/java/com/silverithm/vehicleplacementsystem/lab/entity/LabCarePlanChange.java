package com.silverithm.vehicleplacementsystem.lab.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;

/** 급여제공계획 변경 이력 1건. 변경 내용만 사례가 적힐 수 있어 암호화하고, 변경 항목은 JSON 배열이다. */
@Entity
@Table(name = "lab_care_plan_changes", indexes = @Index(
        name = "idx_lab_care_plan_changes_elder_changed", columnList = "company_id, elderly_id, changed_on"))
@Getter
@NoArgsConstructor
public class LabCarePlanChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "changed_on", nullable = false)
    private LocalDate changedOn;

    @Column(name = "reason", nullable = false, length = 16)
    private String reason;

    @Column(name = "reason_note", nullable = false, length = 300)
    private String reasonNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_items", nullable = false)
    private String changeItems;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "change_detail", nullable = false, columnDefinition = "TEXT")
    private String changeDetail;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "guardian_explained_on")
    private LocalDate guardianExplainedOn;

    @Column(name = "guardian_signed_on")
    private LocalDate guardianSignedOn;

    @Column(name = "notified_on")
    private LocalDate notifiedOn;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabCarePlanChange(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, LocalDate changedOn, String reason, String reasonNote, String changeItems,
                     String changeDetail, Long eventId, LocalDate guardianExplainedOn, LocalDate guardianSignedOn,
                     LocalDate notifiedOn, String writerName) {
        this.elderlyId = elderlyId;
        this.changedOn = changedOn;
        this.reason = reason;
        this.reasonNote = reasonNote;
        this.changeItems = changeItems;
        this.changeDetail = changeDetail;
        this.eventId = eventId;
        this.guardianExplainedOn = guardianExplainedOn;
        this.guardianSignedOn = guardianSignedOn;
        this.notifiedOn = notifiedOn;
        this.writerName = writerName;
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
