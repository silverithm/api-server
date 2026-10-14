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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 직원 인사 기준 — 기관 x 직원 1건. 경력직 비율(평가 4)·보수교육 대상(평가 5)·건강검진 주기(평가 15)를
 * 웹이 계산하는 근거라 서버는 값이 말이 되는지만 지킨다. birth_year는 SMALLINT라 Short로 받는다.
 */
@Entity
@Table(name = "lab_staff_profiles", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_staff_profiles_company_member", columnNames = {"company_id", "member_id"}))
@Getter
@NoArgsConstructor
public class LabStaffProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "job_type", nullable = false, length = 20)
    private String jobType;

    @Column(name = "birth_year")
    private Short birthYear;

    @Column(name = "license_passed_on")
    private LocalDate licensePassedOn;

    @Column(name = "office_worker", nullable = false)
    private boolean officeWorker;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabStaffProfile(Long companyId, Long memberId) {
        this.companyId = companyId;
        this.memberId = memberId;
    }

    /** 같은 직원을 다시 저장하면 마지막 저장이 기준이 되도록 통째로 덮어쓴다 */
    public void fill(LocalDate hireDate, String jobType, Short birthYear, LocalDate licensePassedOn,
                     boolean officeWorker, String note) {
        this.hireDate = hireDate;
        this.jobType = jobType;
        this.birthYear = birthYear;
        this.licensePassedOn = licensePassedOn;
        this.officeWorker = officeWorker;
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
