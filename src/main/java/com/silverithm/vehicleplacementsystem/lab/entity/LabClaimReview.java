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

/** 공단 청구 대조 결과 1건(어르신 x 청구월). 개인정보가 없어 암호화하지 않는다. status는 서비스가 정한다. */
@Entity
@Table(name = "lab_claim_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_claim_reviews_elder_month",
                columnNames = {"elderly_id", "claim_month"}),
        indexes = @Index(name = "idx_lab_claim_reviews_company_month", columnList = "company_id, claim_month"))
@Getter
@NoArgsConstructor
public class LabClaimReview {

    public static final String MATCH = "MATCH";
    public static final String MISMATCH = "MISMATCH";
    public static final String RESOLVED = "RESOLVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    /** 'YYYY-MM' */
    @Column(name = "claim_month", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String claimMonth;

    @Column(name = "claimed_days", nullable = false)
    private short claimedDays;

    @Column(name = "attended_days", nullable = false)
    private short attendedDays;

    @Column(name = "billed_days")
    private Short billedDays;

    /** h3to6 | h6to8 | h8to10 | h10to13 | over13 | under3 */
    @Column(name = "claimed_band", length = 10)
    private String claimedBand;

    /** MATCH | MISMATCH | RESOLVED */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    public LabClaimReview(Long companyId, Long elderlyId, String claimMonth) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.claimMonth = claimMonth;
    }

    public void fill(short claimedDays, short attendedDays, Short billedDays, String claimedBand, String status,
                     String note, String reviewerName) {
        this.claimedDays = claimedDays;
        this.attendedDays = attendedDays;
        this.billedDays = billedDays;
        this.claimedBand = claimedBand;
        this.status = status;
        this.note = note;
        this.reviewerName = reviewerName;
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
