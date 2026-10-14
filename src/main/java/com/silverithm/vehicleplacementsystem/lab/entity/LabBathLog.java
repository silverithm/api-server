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
 * 목욕 제공 일지 1건. 분(minutes)은 서버가 시작·끝 시각으로 계산해 넣는다.
 * 목욕 전 상태·피부 관찰·메모는 건강 정보라 암호화한다.
 */
@Entity
@Table(name = "lab_bath_logs", indexes = {
        @Index(name = "idx_lab_bath_logs_company_date", columnList = "company_id, bath_date"),
        @Index(name = "idx_lab_bath_logs_elder_date", columnList = "elderly_id, bath_date")})
@Getter
@NoArgsConstructor
public class LabBathLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "bath_date", nullable = false)
    private LocalDate bathDate;

    @Column(name = "start_time", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String endTime;

    @Column(name = "minutes", nullable = false)
    private short minutes;

    /** FULL_BATH | BATH_CHAIR | BED */
    @Column(name = "method", nullable = false, length = 12)
    private String method;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "pre_check", length = 512)
    private String preCheck;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "skin_note", columnDefinition = "TEXT")
    private String skinNote;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public LabBathLog(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, LocalDate bathDate, String startTime, String endTime, short minutes,
                     String method, String providerName, String preCheck, String skinNote, String note) {
        this.elderlyId = elderlyId;
        this.bathDate = bathDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.minutes = minutes;
        this.method = method;
        this.providerName = providerName;
        this.preCheck = preCheck;
        this.skinNote = skinNote;
        this.note = note;
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
