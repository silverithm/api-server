package com.silverithm.vehicleplacementsystem.lab.entity;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 제공대장 — 월별 급여제공기록지를 수급자·보호자에게 전달한 내역(어르신 1명 x 월 1건). */
@Entity
@Table(name = "lab_care_deliveries", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_care_deliveries_elder_month", columnNames = {"elderly_id", "record_month"}))
@Getter
@NoArgsConstructor
public class LabCareDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    /** 'YYYY-MM' — 달 단위 값이라 DATE가 아니라 7자 문자열로 둔다 */
    @Column(name = "record_month", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String recordMonth;

    @Column(name = "delivered_on", nullable = false)
    private LocalDate deliveredOn;

    /** VISIT | MAIL | FAX | ELECTRONIC */
    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Column(name = "recipient", length = 50)
    private String recipient;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "note", length = 1024)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LabCareDelivery(Long companyId, Long elderlyId, String recordMonth) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.recordMonth = recordMonth;
    }

    public void fill(LocalDate deliveredOn, String method, String recipient, String note) {
        this.deliveredOn = deliveredOn;
        this.method = method;
        this.recipient = recipient;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
