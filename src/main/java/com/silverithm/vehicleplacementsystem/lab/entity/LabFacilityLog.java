package com.silverithm.vehicleplacementsystem.lab.entity;

import jakarta.persistence.Column;
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

/** 시설 점검·청소·검사·보험 실시 기록 1건. 다음 기한과 보관 만료는 웹이 항목별 주기로 계산한다. */
@Entity
@Table(name = "lab_facility_logs", indexes = @Index(
        name = "idx_lab_facility_logs_company_item_done", columnList = "company_id, item_code, done_on"))
@Getter
@NoArgsConstructor
public class LabFacilityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "item_code", nullable = false, length = 30)
    private String itemCode;

    @Column(name = "done_on", nullable = false)
    private LocalDate doneOn;

    /** SELF | VENDOR | AGENCY */
    @Column(name = "done_by", nullable = false, length = 8)
    private String doneBy;

    @Column(name = "vendor_name", length = 100)
    private String vendorName;

    /** PASS | FIX_NEEDED | FAIL */
    @Column(name = "result", nullable = false, length = 10)
    private String result;

    @Column(name = "action_note", length = 500)
    private String actionNote;

    /** 소방 자체점검 결과 보고일 — 소방 두 항목만 */
    @Column(name = "reported_on")
    private LocalDate reportedOn;

    /** 보험 만기 — 보험 두 항목만 */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "doc_location", length = 200)
    private String docLocation;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabFacilityLog(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String itemCode, LocalDate doneOn, String doneBy, String vendorName, String result,
                     String actionNote, LocalDate reportedOn, LocalDate validUntil, String docLocation,
                     String writerName) {
        this.itemCode = itemCode;
        this.doneOn = doneOn;
        this.doneBy = doneBy;
        this.vendorName = vendorName;
        this.result = result;
        this.actionNote = actionNote;
        this.reportedOn = reportedOn;
        this.validUntil = validUntil;
        this.docLocation = docLocation;
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
