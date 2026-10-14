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
 * 투약의뢰서 — 보호자가 맡긴 약과 먹이는 방법. 의뢰자 이름·약 목록·메모는 건강·개인 정보라 암호화한다.
 * 약 목록은 JSON 문자열이고, 모양과 개수 검증은 서비스가 한다.
 */
@Entity
@Table(name = "lab_medication_requests", indexes = {
        @Index(name = "idx_lab_medication_requests_company_status", columnList = "company_id, status"),
        @Index(name = "idx_lab_medication_requests_elder_start", columnList = "elderly_id, start_date")})
@Getter
@NoArgsConstructor
public class LabMedicationRequest {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "requested_on", nullable = false)
    private LocalDate requestedOn;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "requester_name", nullable = false, length = 512)
    private String requesterName;

    /** SELF | SPOUSE | CHILD | OTHER_FAMILY | OTHER */
    @Column(name = "requester_relation", nullable = false, length = 20)
    private String requesterRelation;

    /** JSON [{name, dose, route, slots, timing, note}] */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "medicines", nullable = false, columnDefinition = "TEXT")
    private String medicines;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "prescription_checked", nullable = false)
    private boolean prescriptionChecked;

    /** ACTIVE | ENDED */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabMedicationRequest(Long companyId) {
        this.companyId = companyId;
    }

    /** 등록과 수정이 같은 경로 — 입력으로 내용을 통째로 덮어쓴다 */
    public void fill(Long elderlyId, LocalDate requestedOn, String requesterName, String requesterRelation,
                     String medicines, LocalDate startDate, LocalDate endDate, boolean prescriptionChecked,
                     String status, String note, String writerName, boolean aiAssisted) {
        this.elderlyId = elderlyId;
        this.requestedOn = requestedOn;
        this.requesterName = requesterName;
        this.requesterRelation = requesterRelation;
        this.medicines = medicines;
        this.startDate = startDate;
        this.endDate = endDate;
        this.prescriptionChecked = prescriptionChecked;
        this.status = status;
        this.note = note;
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
