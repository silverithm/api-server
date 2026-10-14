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
 * 투약 기록 — 의뢰서 x 날짜 x 시간대 1건. 투약 후 상태는 건강 정보라 암호화한다.
 * 의뢰서 id는 일반 열이고, 의뢰서를 지우면 DB가 기록도 함께 지운다(서비스는 기록이 있으면 먼저 막는다).
 */
@Entity
@Table(name = "lab_medication_doses",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_medication_doses_request_date_slot",
                columnNames = {"request_id", "given_on", "slot"}),
        indexes = {
                @Index(name = "idx_lab_medication_doses_company_date", columnList = "company_id, given_on"),
                @Index(name = "idx_lab_medication_doses_elder_date", columnList = "elderly_id, given_on")})
@Getter
@NoArgsConstructor
public class LabMedicationDose {

    public static final String GIVEN = "GIVEN";
    public static final String SKIPPED = "SKIPPED";
    public static final String REFUSED = "REFUSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "given_on", nullable = false)
    private LocalDate givenOn;

    /** MORNING | LUNCH | EVENING | BEDTIME */
    @Column(name = "slot", nullable = false, length = 10)
    private String slot;

    /** GIVEN | SKIPPED | REFUSED */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    /** 'HH:MM' — GIVEN일 때만 */
    @Column(name = "given_at", length = 5, columnDefinition = "CHAR(5)")
    private String givenAt;

    @Column(name = "giver_name", nullable = false, length = 50)
    private String giverName;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "observation", columnDefinition = "TEXT")
    private String observation;

    public LabMedicationDose(Long companyId, Long elderlyId, Long requestId, LocalDate givenOn, String slot) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.requestId = requestId;
        this.givenOn = givenOn;
        this.slot = slot;
    }

    public void fill(String status, String givenAt, String giverName, String observation) {
        this.status = status;
        this.givenAt = givenAt;
        this.giverName = giverName;
        this.observation = observation;
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
