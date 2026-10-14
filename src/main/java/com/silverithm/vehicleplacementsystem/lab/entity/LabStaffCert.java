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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 직원 이수·검진 기록 — 보수교육 시간과 건강검진 날짜의 근거. 한 직원이 여러 번 가질 수 있다. */
@Entity
@Table(name = "lab_staff_certs", indexes = @Index(name = "idx_lab_staff_certs_member_kind",
        columnList = "company_id, member_id, kind"))
@Getter
@NoArgsConstructor
public class LabStaffCert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "done_on", nullable = false)
    private LocalDate doneOn;

    @Column(name = "hours", precision = 4, scale = 1)
    private BigDecimal hours;

    @Column(name = "institution", length = 100)
    private String institution;

    @Column(name = "cert_no", length = 40)
    private String certNo;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabStaffCert(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long memberId, String kind, LocalDate doneOn, BigDecimal hours, String institution,
                     String certNo, String note) {
        this.memberId = memberId;
        this.kind = kind;
        this.doneOn = doneOn;
        this.hours = hours;
        this.institution = institution;
        this.certNo = certNo;
        this.note = note;
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
