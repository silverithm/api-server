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

/** 평가 가산금 수령·사용 1건. owner_use는 대표자 겸 시설장·직원에게 쓴 금액(사용 비율에서 뺀다). */
@Entity
@Table(name = "lab_bonus_funds", indexes = @Index(
        name = "idx_lab_bonus_funds_company_occurred", columnList = "company_id, occurred_on"))
@Getter
@NoArgsConstructor
public class LabBonusFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** RECEIVED | SPENT */
    @Column(name = "kind", nullable = false, length = 8)
    private String kind;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "purpose", nullable = false, length = 100)
    private String purpose;

    @Column(name = "owner_use", nullable = false)
    private boolean ownerUse;

    @Column(name = "evidence_ledger_id")
    private Long evidenceLedgerId;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabBonusFund(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String kind, LocalDate occurredOn, long amount, Long memberId, String purpose, boolean ownerUse,
                     Long evidenceLedgerId, String note) {
        this.kind = kind;
        this.occurredOn = occurredOn;
        this.amount = amount;
        this.memberId = memberId;
        this.purpose = purpose;
        this.ownerUse = ownerUse;
        this.evidenceLedgerId = evidenceLedgerId;
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
