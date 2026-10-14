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
 * 예산 — 기관 x 회계연도 x 차수(0=본예산, 1~9=추경). 줄은 {@link LabBudgetLine}이 따로 들고 있다.
 *
 * <p>확정(CONFIRMED)된 예산의 잠금은 서비스가 지키고, 상태 전이는 {@link #confirm}·{@link #reopen}으로만 한다.
 * 줄만 바뀌어도 수정 시각이 갱신되도록 {@link #touch}를 둔다.
 */
@Entity
@Table(name = "lab_budgets",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_budgets_company_year_revision",
                columnNames = {"company_id", "fiscal_year", "revision"}))
@Getter
@NoArgsConstructor
public class LabBudget {

    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "fiscal_year", nullable = false)
    private short fiscalYear;

    @Column(name = "revision", nullable = false)
    private short revision;

    @Column(name = "decided_on")
    private LocalDate decidedOn;

    /** DRAFT | CONFIRMED */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "confirmed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime confirmedAt;

    public LabBudget(Long companyId, short fiscalYear, short revision) {
        this.companyId = companyId;
        this.fiscalYear = fiscalYear;
        this.revision = revision;
        this.status = DRAFT;
    }

    public boolean isConfirmed() {
        return CONFIRMED.equals(status);
    }

    public void fill(LocalDate decidedOn, String note) {
        this.decidedOn = decidedOn;
        this.note = note;
        touch();
    }

    public void confirm() {
        this.status = CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void reopen() {
        this.status = DRAFT;
        this.confirmedAt = null;
    }

    /** 줄만 바뀌어도 수정 시각을 갱신한다 */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
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
