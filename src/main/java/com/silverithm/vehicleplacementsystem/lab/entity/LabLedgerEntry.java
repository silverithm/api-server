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

/**
 * 현금출납부 수입·지출 1건. donation_id는 같은 기관 후원금만 가리킨다(서비스가 확인) —
 * 표에는 FK(ON DELETE SET NULL)가 있어 후원금이 지워지면 연결만 풀린다.
 */
@Entity
@Table(name = "lab_ledger_entries", indexes = @Index(
        name = "idx_lab_ledger_entries_company_date", columnList = "company_id, entry_date"))
@Getter
@NoArgsConstructor
public class LabLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** INCOME | EXPENSE */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "account_code", nullable = false, length = 4, columnDefinition = "VARCHAR(4)")
    private String accountCode;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "counterparty", length = 100)
    private String counterparty;

    /** RECEIPT | CARD | TAX_INVOICE | TRANSFER | OTHER */
    @Column(name = "evidence_type", length = 20)
    private String evidenceType;

    @Column(name = "donation_id")
    private Long donationId;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabLedgerEntry(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate entryDate, String direction, String accountCode, String description, long amount,
                     String counterparty, String evidenceType, Long donationId, boolean aiAssisted) {
        this.entryDate = entryDate;
        this.direction = direction;
        this.accountCode = accountCode;
        this.description = description;
        this.amount = amount;
        this.counterparty = counterparty;
        this.evidenceType = evidenceType;
        this.donationId = donationId;
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
