package com.silverithm.vehicleplacementsystem.lab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 통장 월말 잔액 — 통장 x 달 1건. 장부 잔액과 맞춰 보는 은행 쪽 숫자다. */
@Entity
@Table(name = "lab_bank_statements", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_bank_statements_account_month", columnNames = {"account_id", "statement_month"}))
@Getter
@NoArgsConstructor
public class LabBankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "statement_month", nullable = false, columnDefinition = "CHAR(7)")
    private String statementMonth;

    @Column(name = "closing_balance", nullable = false)
    private long closingBalance;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabBankStatement(Long companyId, Long accountId, String statementMonth) {
        this.companyId = companyId;
        this.accountId = accountId;
        this.statementMonth = statementMonth;
    }

    public void fill(long closingBalance, String note) {
        this.closingBalance = closingBalance;
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
