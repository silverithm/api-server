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

/** 직원 복지(포상) 제공대장 1건. member_id·evidence_ledger_id는 서비스가 같은 기관인지 확인한다. */
@Entity
@Table(name = "lab_staff_welfare", indexes = @Index(
        name = "idx_lab_staff_welfare_company_given", columnList = "company_id, given_on"))
@Getter
@NoArgsConstructor
public class LabStaffWelfare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "given_on", nullable = false)
    private LocalDate givenOn;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "item", nullable = false, length = 100)
    private String item;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "signed", nullable = false)
    private boolean signed;

    @Column(name = "recipient_is_owner", nullable = false)
    private boolean recipientIsOwner;

    @Column(name = "evidence_ledger_id")
    private Long evidenceLedgerId;

    @Column(name = "evidence_note", length = 200)
    private String evidenceNote;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabStaffWelfare(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate givenOn, Long memberId, String kind, String item, long amount, boolean signed,
                     boolean recipientIsOwner, Long evidenceLedgerId, String evidenceNote, String note) {
        this.givenOn = givenOn;
        this.memberId = memberId;
        this.kind = kind;
        this.item = item;
        this.amount = amount;
        this.signed = signed;
        this.recipientIsOwner = recipientIsOwner;
        this.evidenceLedgerId = evidenceLedgerId;
        this.evidenceNote = evidenceNote;
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
