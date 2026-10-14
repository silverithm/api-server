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

/** 약품 보관 점검 — 기관 x 점검일 1건. 개인정보가 없어 암호화하지 않는다. items는 약품별 확인·조치 JSON이다. */
@Entity
@Table(name = "lab_medicine_checks",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_medicine_checks_company_date",
                columnNames = {"company_id", "checked_on"}))
@Getter
@NoArgsConstructor
public class LabMedicineCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "checked_on", nullable = false)
    private LocalDate checkedOn;

    @Column(name = "inspector_name", nullable = false, length = 50)
    private String inspectorName;

    @Column(name = "cabinet_locked", nullable = false)
    private boolean cabinetLocked;

    /** 냉장 보관 약이 없으면 null */
    @Column(name = "fridge_locked")
    private Boolean fridgeLocked;

    /** JSON [{stockId, ok, action, note}] */
    @Column(name = "items", nullable = false, columnDefinition = "TEXT")
    private String items;

    @Column(name = "note", length = 500)
    private String note;

    public LabMedicineCheck(Long companyId, LocalDate checkedOn) {
        this.companyId = companyId;
        this.checkedOn = checkedOn;
    }

    public void fill(String inspectorName, boolean cabinetLocked, Boolean fridgeLocked, String items, String note) {
        this.inspectorName = inspectorName;
        this.cabinetLocked = cabinetLocked;
        this.fridgeLocked = fridgeLocked;
        this.items = items;
        this.note = note;
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
