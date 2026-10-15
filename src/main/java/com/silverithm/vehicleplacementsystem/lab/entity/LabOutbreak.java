package com.silverithm.vehicleplacementsystem.lab.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;

/** 감염병 유행·발생·국가 위기 경보 1건. 조치는 JSON 배열, 메모만 암호화한다. */
@Entity
@Table(name = "lab_outbreaks", indexes = @Index(
        name = "idx_lab_outbreaks_company_started", columnList = "company_id, started_on"))
@Getter
@NoArgsConstructor
public class LabOutbreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** OUTBREAK | CASE | NATIONAL */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "disease", nullable = false, length = 50)
    private String disease;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "elder_cases", nullable = false)
    private short elderCases;

    @Column(name = "staff_cases", nullable = false)
    private short staffCases;

    @Column(name = "guardian_notified_on")
    private LocalDate guardianNotifiedOn;

    @Column(name = "health_center_reported_on")
    private LocalDate healthCenterReportedOn;

    @Column(name = "authority_reported_on")
    private LocalDate authorityReportedOn;

    @Column(name = "alert_level", length = 8)
    private String alertLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", nullable = false)
    private String actions;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabOutbreak(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String kind, String disease, LocalDate startedOn, short elderCases, short staffCases,
                     LocalDate guardianNotifiedOn, LocalDate healthCenterReportedOn, LocalDate authorityReportedOn,
                     String alertLevel, String actions, LocalDate endedOn, String note, String writerName) {
        this.kind = kind;
        this.disease = disease;
        this.startedOn = startedOn;
        this.elderCases = elderCases;
        this.staffCases = staffCases;
        this.guardianNotifiedOn = guardianNotifiedOn;
        this.healthCenterReportedOn = healthCenterReportedOn;
        this.authorityReportedOn = authorityReportedOn;
        this.alertLevel = alertLevel;
        this.actions = actions;
        this.endedOn = endedOn;
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
