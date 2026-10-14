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
 * 급여 계약·장기요양 인정 1건. 인정번호와 메모는 암호화한다.
 * 어르신 1명의 ACTIVE 계약은 하나뿐이다 — 서비스가 먼저 막고, MySQL에서는 가상 열(active_elderly_id)의
 * 유일 키가 동시 저장을 막는다. 가상 열은 DB에만 있어 이 엔티티에는 매핑하지 않는다.
 */
@Entity
@Table(name = "lab_elder_contracts", indexes = {
        @Index(name = "idx_lab_elder_contracts_company_status", columnList = "company_id, status"),
        @Index(name = "idx_lab_elder_contracts_elder_start", columnList = "elderly_id, contract_start")})
@Getter
@NoArgsConstructor
public class LabElderContract {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "contract_start", nullable = false)
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    /** '1'~'5' | 'cognitive' */
    @Column(name = "care_grade", nullable = false, length = 10)
    private String careGrade;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "recognition_no", length = 512)
    private String recognitionNo;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "renewal_count", nullable = false)
    private short renewalCount;

    /** standard | reduced40 | reduced60 | medicalAid */
    @Column(name = "copay_class", nullable = false, length = 20)
    private String copayClass;

    @Column(name = "planned_days_per_month")
    private Short plannedDaysPerMonth;

    @Column(name = "signed_on")
    private LocalDate signedOn;

    @Column(name = "copy_given", nullable = false)
    private boolean copyGiven;

    @Column(name = "contract_notified_on")
    private LocalDate contractNotifiedOn;

    @Column(name = "plan_notified_on")
    private LocalDate planNotifiedOn;

    @Column(name = "rights_guided_on")
    private LocalDate rightsGuidedOn;

    /** ACTIVE | ENDED */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    /** TRANSFER | HOSPITAL | DEATH | SELF | OTHER — ENDED일 때만 */
    @Column(name = "end_reason", length = 20)
    private String endReason;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public LabElderContract(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, LocalDate contractStart, LocalDate contractEnd, String careGrade,
                     String recognitionNo, LocalDate validFrom, LocalDate validTo, short renewalCount,
                     String copayClass, Short plannedDaysPerMonth, LocalDate signedOn, boolean copyGiven,
                     LocalDate contractNotifiedOn, LocalDate planNotifiedOn, LocalDate rightsGuidedOn,
                     String status, String endReason, String note) {
        this.elderlyId = elderlyId;
        this.contractStart = contractStart;
        this.contractEnd = contractEnd;
        this.careGrade = careGrade;
        this.recognitionNo = recognitionNo;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.renewalCount = renewalCount;
        this.copayClass = copayClass;
        this.plannedDaysPerMonth = plannedDaysPerMonth;
        this.signedOn = signedOn;
        this.copyGiven = copyGiven;
        this.contractNotifiedOn = contractNotifiedOn;
        this.planNotifiedOn = planNotifiedOn;
        this.rightsGuidedOn = rightsGuidedOn;
        this.status = status;
        this.endReason = endReason;
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
