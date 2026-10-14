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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 간호일지 — 어르신 1명 x 하루 1건.
 *
 * <p>바이탈은 안 잰 값이 흔해서 모두 null을 허용한다(범위는 서비스가 검증). 혈압·맥박·혈당은 SMALLINT 열이라
 * 엔티티도 Short로 받아 Hibernate 스키마 검증이 INTEGER/SMALLINT 불일치로 실패하지 않게 한다.
 * 처치와 메모는 건강 정보라 암호화한다.
 */
@Entity
@Table(name = "lab_nursing_logs", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_nursing_logs_elder_date", columnNames = {"elderly_id", "log_date"}))
@Getter
@NoArgsConstructor
public class LabNursingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "systolic")
    private Short systolic;

    @Column(name = "diastolic")
    private Short diastolic;

    @Column(name = "pulse")
    private Short pulse;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "blood_sugar")
    private Short bloodSugar;

    @Column(name = "weight", precision = 5, scale = 1)
    private BigDecimal weight;

    /** GIVEN | SKIPPED | NA */
    @Column(name = "med_morning", length = 10)
    private String medMorning;

    @Column(name = "med_lunch", length = 10)
    private String medLunch;

    @Column(name = "med_evening", length = 10)
    private String medEvening;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "treatment", columnDefinition = "TEXT")
    private String treatment;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "recorder_name", length = 50)
    private String recorderName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabNursingLog(Long companyId, Long elderlyId, LocalDate logDate) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.logDate = logDate;
    }

    /** 같은 날 다시 저장하면 마지막 저장이 그날의 일지가 되도록 내용을 통째로 덮어쓴다 */
    public void fill(Short systolic, Short diastolic, Short pulse, BigDecimal temperature, Short bloodSugar,
                     BigDecimal weight, String medMorning, String medLunch, String medEvening, String treatment,
                     String note, String recorderName, boolean aiAssisted) {
        this.systolic = systolic;
        this.diastolic = diastolic;
        this.pulse = pulse;
        this.temperature = temperature;
        this.bloodSugar = bloodSugar;
        this.weight = weight;
        this.medMorning = medMorning;
        this.medLunch = medLunch;
        this.medEvening = medEvening;
        this.treatment = treatment;
        this.note = note;
        this.recorderName = recorderName;
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
