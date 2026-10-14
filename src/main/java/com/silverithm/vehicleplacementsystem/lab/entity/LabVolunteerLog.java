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

/**
 * 봉사자 활동 일지 — 봉사자 1명의 하루 활동 1건. 봉사 시간(minutes)은 서버가 시작·끝 시각으로 계산해 담는다.
 *
 * <p>봉사자 이름은 개인정보라 암호화(평문 50자 → VARCHAR(512))하고, 활동 내용도 암호화한다.
 */
@Entity
@Table(name = "lab_volunteer_logs", indexes = @Index(
        name = "idx_lab_volunteer_logs_company_date", columnList = "company_id, activity_date"))
@Getter
@NoArgsConstructor
public class LabVolunteerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "volunteer_name", nullable = false, length = 512)
    private String volunteerName;

    @Column(name = "organization", length = 100)
    private String organization;

    /** 'HH:MM' */
    @Column(name = "start_time", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String startTime;

    /** 'HH:MM' */
    @Column(name = "end_time", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String endTime;

    @Column(name = "minutes", nullable = false)
    private short minutes;

    /** MEAL | PROGRAM | COMPANION | CLEANING | TALENT | OTHER */
    @Column(name = "activity_type", nullable = false, length = 20)
    private String activityType;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "vms_registered", nullable = false)
    private boolean vmsRegistered;

    @Column(name = "confirmer_name", length = 50)
    private String confirmerName;

    public LabVolunteerLog(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate activityDate, String volunteerName, String organization, String startTime,
                     String endTime, short minutes, String activityType, String content, boolean vmsRegistered,
                     String confirmerName) {
        this.activityDate = activityDate;
        this.volunteerName = volunteerName;
        this.organization = organization;
        this.startTime = startTime;
        this.endTime = endTime;
        this.minutes = minutes;
        this.activityType = activityType;
        this.content = content;
        this.vmsRegistered = vmsRegistered;
        this.confirmerName = confirmerName;
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
