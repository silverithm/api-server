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

/**
 * 진료 동행 기록 1건(평가 30 기준 1) — 급여제공시간 중 직원이 어르신과 병원에 동행한 내용.
 *
 * <p>진료 사유·내용·처방·보호자 메모는 건강 정보라 암호화한다. 병원 이름·진료과·동행 직원은 목록에
 * 그대로 보이는 평문이다. 보호자 메모 열은 암호문이 길어지므로 VARCHAR(512)다(평문 100자 이하).
 */
@Entity
@Table(name = "lab_medical_visits", indexes = {
        @Index(name = "idx_lab_medical_visits_company_date", columnList = "company_id, visit_date"),
        @Index(name = "idx_lab_medical_visits_elder_date", columnList = "elderly_id, visit_date")})
@Getter
@NoArgsConstructor
public class LabMedicalVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "start_time", columnDefinition = "CHAR(5)")
    private String startTime;

    @Column(name = "end_time", columnDefinition = "CHAR(5)")
    private String endTime;

    @Column(name = "hospital", nullable = false, length = 100)
    private String hospital;

    @Column(name = "department", length = 40)
    private String department;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "result", nullable = false, columnDefinition = "TEXT")
    private String result;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "prescription", columnDefinition = "TEXT")
    private String prescription;

    @Column(name = "escort_name", nullable = false, length = 50)
    private String escortName;

    @Column(name = "guardian_notified", nullable = false)
    private boolean guardianNotified;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "guardian_note", length = 512)
    private String guardianNote;

    @Column(name = "follow_up_on")
    private LocalDate followUpOn;

    @Column(name = "follow_up_done", nullable = false)
    private boolean followUpDone;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabMedicalVisit(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, LocalDate visitDate, String startTime, String endTime, String hospital,
                     String department, String reason, String result, String prescription, String escortName,
                     boolean guardianNotified, String guardianNote, LocalDate followUpOn, boolean followUpDone,
                     boolean aiAssisted) {
        this.elderlyId = elderlyId;
        this.visitDate = visitDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.hospital = hospital;
        this.department = department;
        this.reason = reason;
        this.result = result;
        this.prescription = prescription;
        this.escortName = escortName;
        this.guardianNotified = guardianNotified;
        this.guardianNote = guardianNote;
        this.followUpOn = followUpOn;
        this.followUpDone = followUpDone;
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
