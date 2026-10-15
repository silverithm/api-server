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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 예방접종 기록 — 어르신 x 연도 x 백신 1건. 평가 접종률은 웹이 이 기록으로 계산한다. */
@Entity
@Table(name = "lab_vaccinations",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_vaccinations_elder_season_vaccine",
                columnNames = {"company_id", "elderly_id", "season_year", "vaccine"}),
        indexes = @Index(name = "idx_lab_vaccinations_company_season", columnList = "company_id, season_year"))
@Getter
@NoArgsConstructor
public class LabVaccination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "season_year", nullable = false)
    private Short seasonYear;

    /** INFLUENZA | PNEUMOCOCCAL | COVID19 */
    @Column(name = "vaccine", nullable = false, length = 16)
    private String vaccine;

    /** DONE | REFUSED | EXCLUDED | UNKNOWN */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "given_on")
    private LocalDate givenOn;

    @Column(name = "institution", length = 100)
    private String institution;

    /** CERT | GUARDIAN | NIP | OTHER */
    @Column(name = "verified_by", length = 12)
    private String verifiedBy;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabVaccination(Long companyId, Long elderlyId, Short seasonYear, String vaccine) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.seasonYear = seasonYear;
        this.vaccine = vaccine;
    }

    public void fill(String status, LocalDate givenOn, String institution, String verifiedBy, String reason,
                     String note, String writerName) {
        this.status = status;
        this.givenOn = givenOn;
        this.institution = institution;
        this.verifiedBy = verifiedBy;
        this.reason = reason;
        this.note = note;
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
