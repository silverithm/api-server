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

/** 월간 프로그램 계획표 — 기관 x 달 1건. 항목은 JSON 문자열이고 개인정보가 없어 암호화하지 않는다. */
@Entity
@Table(name = "lab_program_schedules", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_program_schedules_company_month", columnNames = {"company_id", "schedule_month"}))
@Getter
@NoArgsConstructor
public class LabProgramSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "schedule_month", nullable = false, columnDefinition = "CHAR(7)")
    private String scheduleMonth;

    @Column(name = "items", nullable = false, columnDefinition = "TEXT")
    private String items;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "shared_on")
    private LocalDate sharedOn;

    /** PRINT | SMS | KAKAO | BOARD | OTHER — sharedOn과 함께 있거나 함께 없다 */
    @Column(name = "shared_method", length = 12)
    private String sharedMethod;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabProgramSchedule(Long companyId, String scheduleMonth) {
        this.companyId = companyId;
        this.scheduleMonth = scheduleMonth;
    }

    public void fill(String items, String notes, LocalDate sharedOn, String sharedMethod, boolean aiAssisted) {
        this.items = items;
        this.notes = notes;
        this.sharedOn = sharedOn;
        this.sharedMethod = sharedMethod;
        this.aiAssisted = aiAssisted;
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
