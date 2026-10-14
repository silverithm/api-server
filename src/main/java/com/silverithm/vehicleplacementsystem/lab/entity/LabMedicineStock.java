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

/** 약품 관리대장(일반·비상 의약품) 1건. 개인정보가 없어 암호화하지 않는다. 폐기한 약은 disposedOn이 채워진다. */
@Entity
@Table(name = "lab_medicine_stock",
        indexes = @Index(name = "idx_lab_medicine_stock_company_expires", columnList = "company_id, expires_on"))
@Getter
@NoArgsConstructor
public class LabMedicineStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "efficacy", nullable = false, length = 100)
    private String efficacy;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit", nullable = false, length = 10)
    private String unit;

    /** ROOM | FRIDGE */
    @Column(name = "storage", nullable = false, length = 10)
    private String storage;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "disposed_on")
    private LocalDate disposedOn;

    @Column(name = "note", length = 200)
    private String note;

    public LabMedicineStock(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String name, String efficacy, int quantity, String unit, String storage, LocalDate expiresOn,
                     LocalDate openedOn, LocalDate disposedOn, String note) {
        this.name = name;
        this.efficacy = efficacy;
        this.quantity = quantity;
        this.unit = unit;
        this.storage = storage;
        this.expiresOn = expiresOn;
        this.openedOn = openedOn;
        this.disposedOn = disposedOn;
        this.note = note;
    }

    /** 점검에서 폐기로 표시된 약 — 이미 폐기일이 있으면 그대로 둔다 */
    public void disposeOn(LocalDate day) {
        if (this.disposedOn == null) {
            this.disposedOn = day;
        }
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
