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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 시설 정보 — 기관당 한 줄. 건물·설비 종류로 시설 관리 대장 항목의 적용 여부를 판단하는 근거다.
 * 모르는 칸은 UNKNOWN으로 두고, 적용 여부는 appliesOverride로 직접 지정할 수 있다.
 */
@Entity
@Table(name = "lab_facility_profiles", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_facility_profiles_company", columnNames = "company_id"))
@Getter
@NoArgsConstructor
public class LabFacilityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @Column(name = "gross_area_m2")
    private Integer grossAreaM2;

    /** NONE | SIMPLE | FULL | UNKNOWN */
    @Column(name = "sprinkler", nullable = false, length = 8)
    private String sprinkler;

    /** GENERAL | SELF_USE | UNKNOWN */
    @Column(name = "power_kind", nullable = false, length = 10)
    private String powerKind;

    /** NONE | CITY | LPG | UNKNOWN */
    @Column(name = "gas_kind", nullable = false, length = 8)
    private String gasKind;

    @Column(name = "lpg_capacity_kg")
    private Integer lpgCapacityKg;

    /** NONE | CENTER | BUILDING | UNKNOWN */
    @Column(name = "elevator", nullable = false, length = 10)
    private String elevator;

    /** NONE | LARGE | SMALL | UNKNOWN */
    @Column(name = "water_tank", nullable = false, length = 8)
    private String waterTank;

    /** NONE | YES | UNKNOWN */
    @Column(name = "septic", nullable = false, length = 8)
    private String septic;

    /** {항목 코드: 'YES'|'NO'} JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applies_override", nullable = false)
    private String appliesOverride;

    @Column(name = "fire_manager_name", length = 50)
    private String fireManagerName;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabFacilityProfile(Long companyId) {
        this.companyId = companyId;
    }

    /** 저장은 늘 통째로 덮어쓴다 */
    public void fill(LocalDate approvedOn, Integer grossAreaM2, String sprinkler, String powerKind, String gasKind,
                     Integer lpgCapacityKg, String elevator, String waterTank, String septic,
                     String appliesOverride, String fireManagerName, String note, String writerName) {
        this.approvedOn = approvedOn;
        this.grossAreaM2 = grossAreaM2;
        this.sprinkler = sprinkler;
        this.powerKind = powerKind;
        this.gasKind = gasKind;
        this.lpgCapacityKg = lpgCapacityKg;
        this.elevator = elevator;
        this.waterTank = waterTank;
        this.septic = septic;
        this.appliesOverride = appliesOverride;
        this.fireManagerName = fireManagerName;
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
