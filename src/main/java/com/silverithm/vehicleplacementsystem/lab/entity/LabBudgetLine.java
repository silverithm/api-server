package com.silverithm.vehicleplacementsystem.lab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예산 줄 — 예산 하나의 (방향, 계정 코드)별 금액. 수정은 줄을 통째로 지우고 다시 넣는 방식이라 수정 메서드가 없다.
 * 예산 FK는 DB의 ON DELETE CASCADE가 맡고 엔티티는 id만 든다.
 */
@Entity
@Table(name = "lab_budget_lines", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_budget_lines_budget_direction_code", columnNames = {"budget_id", "direction", "account_code"}))
@Getter
@NoArgsConstructor
public class LabBudgetLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "budget_id", nullable = false)
    private Long budgetId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** INCOME | EXPENSE */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "account_code", nullable = false, length = 4)
    private String accountCode;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "note", length = 200)
    private String note;

    public LabBudgetLine(Long budgetId, Long companyId, String direction, String accountCode, long amount,
                         String note) {
        this.budgetId = budgetId;
        this.companyId = companyId;
        this.direction = direction;
        this.accountCode = accountCode;
        this.amount = amount;
        this.note = note;
    }
}
