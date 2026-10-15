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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 송영 수칙·운행표를 어르신(보호자)에게 드린 기록 1건. */
@Entity
@Table(name = "lab_guide_deliveries", indexes = {
        @Index(name = "idx_lab_guide_deliveries_company_kind", columnList = "company_id, kind, delivered_on"),
        @Index(name = "idx_lab_guide_deliveries_elderly", columnList = "elderly_id")})
@Getter
@NoArgsConstructor
public class LabGuideDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    /** TRANSPORT_RULES | TIMETABLE */
    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "delivered_on", nullable = false)
    private LocalDate deliveredOn;

    /** FACE | PAPER | SMS | SNS | OTHER */
    @Column(name = "method", nullable = false, length = 8)
    private String method;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabGuideDelivery(Long companyId, Long elderlyId, String kind, LocalDate deliveredOn, String method,
                            String note) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.kind = kind;
        this.deliveredOn = deliveredOn;
        this.method = method;
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
