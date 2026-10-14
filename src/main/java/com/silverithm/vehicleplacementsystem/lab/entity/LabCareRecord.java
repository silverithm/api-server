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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 급여제공기록 — 어르신 1명 x 하루 1건.
 *
 * <p>company_id·elderly_id는 연관관계 대신 평범한 Long으로 둔다. 실험실 엔티티가 Elderly·Company의
 * 복잡한 매핑에 끌려 들어가지 않고 단독으로 스키마 검증이 되게 하려는 것이다(DB에는 FK가 있다).
 *
 * <p>돌봄 문장은 건강 정보라 모두 컬럼 암호화한다. 암호문이 평문의 약 4.2배라 TEXT/VARCHAR(1024)로 잡았다.
 */
@Entity
@Table(name = "lab_care_records", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_care_records_elder_date", columnNames = {"elderly_id", "record_date"}))
@Getter
@NoArgsConstructor
public class LabCareRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "physical", columnDefinition = "TEXT")
    private String physical;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "cognitive", columnDefinition = "TEXT")
    private String cognitive;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "nursing", columnDefinition = "TEXT")
    private String nursing;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "training", columnDefinition = "TEXT")
    private String training;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "special_note", columnDefinition = "TEXT")
    private String specialNote;

    /** AI에 넣기 전 요양보호사가 적은 원문 — 구조화 결과가 이상할 때 되짚어 보려고 남긴다 */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "raw_note", columnDefinition = "TEXT")
    private String rawNote;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "meal", length = 1024)
    private String meal;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "toilet", length = 1024)
    private String toilet;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "bath", length = 1024)
    private String bath;

    @Column(name = "provider_name", length = 50)
    private String providerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabCareRecord(Long companyId, Long elderlyId, LocalDate recordDate) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.recordDate = recordDate;
    }

    /** 입력으로 내용을 통째로 덮어쓴다 — 같은 날 다시 저장하면 마지막 저장이 그날의 기록이 된다 */
    public void fill(String physical, String cognitive, String nursing, String training, String specialNote,
                     String rawNote, String meal, String toilet, String bath, String providerName,
                     boolean aiAssisted) {
        this.physical = physical;
        this.cognitive = cognitive;
        this.nursing = nursing;
        this.training = training;
        this.specialNote = specialNote;
        this.rawNote = rawNote;
        this.meal = meal;
        this.toilet = toilet;
        this.bath = bath;
        this.providerName = providerName;
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
