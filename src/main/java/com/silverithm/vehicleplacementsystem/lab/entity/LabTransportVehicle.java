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

/** 송영 차량과 보험 — 기관 x 차량번호 1건. 종합보험 여부와 만기만 담고 증권 원본은 받지 않는다. */
@Entity
@Table(name = "lab_transport_vehicles", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_transport_vehicles_no", columnNames = {"company_id", "vehicle_no"}))
@Getter
@NoArgsConstructor
public class LabTransportVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "vehicle_no", nullable = false, length = 20)
    private String vehicleNo;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "insurer", length = 50)
    private String insurer;

    @Column(name = "policy_no", length = 40)
    private String policyNo;

    @Column(name = "insurance_from")
    private LocalDate insuranceFrom;

    @Column(name = "insurance_to")
    private LocalDate insuranceTo;

    @Column(name = "comprehensive", nullable = false)
    private boolean comprehensive;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabTransportVehicle(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String vehicleNo, String label, String insurer, String policyNo, LocalDate insuranceFrom,
                     LocalDate insuranceTo, boolean comprehensive, boolean active, String note) {
        this.vehicleNo = vehicleNo;
        this.label = label;
        this.insurer = insurer;
        this.policyNo = policyNo;
        this.insuranceFrom = insuranceFrom;
        this.insuranceTo = insuranceTo;
        this.comprehensive = comprehensive;
        this.active = active;
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
