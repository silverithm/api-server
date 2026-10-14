package com.silverithm.vehicleplacementsystem.lab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기관 통장 — 계좌번호는 끝 4자리만 갖는다. 전체 번호를 받을 열도, 받을 입력도 없다. */
@Entity
@Table(name = "lab_bank_accounts")
@Getter
@NoArgsConstructor
public class LabBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "purpose", nullable = false, length = 12)
    private String purpose;

    @Column(name = "bank_name", nullable = false, length = 30)
    private String bankName;

    @Column(name = "account_tail", columnDefinition = "CHAR(4)")
    private String accountTail;

    @Column(name = "opening_balance", nullable = false)
    private long openingBalance;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabBankAccount(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String name, String purpose, String bankName, String accountTail, long openingBalance,
                     LocalDate openingDate, boolean active) {
        this.name = name;
        this.purpose = purpose;
        this.bankName = bankName;
        this.accountTail = accountTail;
        this.openingBalance = openingBalance;
        this.openingDate = openingDate;
        this.active = active;
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
