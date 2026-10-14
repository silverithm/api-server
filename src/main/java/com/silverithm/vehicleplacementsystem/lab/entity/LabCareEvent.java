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
 * 상태변화·사고·응급 기록 — 어르신 1명에게 일어난 일 1건.
 *
 * <p>발생·보호자 연락 시각은 서울 시각 그대로 담는 DATETIME이다. 설명·조치·보호자 반응·후속 조치·예방은
 * 건강 정보라 암호화한다. 닫기 규칙(후속 조치·보호자 연락)은 서비스가 검증하고, 이 엔티티는 담기만 한다.
 */
@Entity
@Table(name = "lab_care_events", indexes = {
        @Index(name = "idx_lab_care_events_company_time", columnList = "company_id, occurred_at"),
        @Index(name = "idx_lab_care_events_elder_time", columnList = "elderly_id, occurred_at")})
@Getter
@NoArgsConstructor
public class LabCareEvent {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    @Column(name = "place", length = 100)
    private String place;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "immediate_action", columnDefinition = "TEXT")
    private String immediateAction;

    @Column(name = "guardian_notified_at")
    private LocalDateTime guardianNotifiedAt;

    @Column(name = "guardian_method", length = 10)
    private String guardianMethod;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "guardian_response", columnDefinition = "TEXT")
    private String guardianResponse;

    @Column(name = "hospital_name", length = 100)
    private String hospitalName;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "follow_up", columnDefinition = "TEXT")
    private String followUp;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "prevention", columnDefinition = "TEXT")
    private String prevention;

    /** OPEN | CLOSED */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "reporter_name", length = 50)
    private String reporterName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabCareEvent(Long companyId) {
        this.companyId = companyId;
    }

    /** 등록과 수정이 같은 경로 — 입력으로 내용을 통째로 덮어쓴다 */
    public void fill(Long elderlyId, LocalDateTime occurredAt, String kind, String severity, String place,
                     String description, String immediateAction, LocalDateTime guardianNotifiedAt,
                     String guardianMethod, String guardianResponse, String hospitalName, String followUp,
                     String prevention, String status, LocalDate closedOn, String reporterName,
                     boolean aiAssisted) {
        this.elderlyId = elderlyId;
        this.occurredAt = occurredAt;
        this.kind = kind;
        this.severity = severity;
        this.place = place;
        this.description = description;
        this.immediateAction = immediateAction;
        this.guardianNotifiedAt = guardianNotifiedAt;
        this.guardianMethod = guardianMethod;
        this.guardianResponse = guardianResponse;
        this.hospitalName = hospitalName;
        this.followUp = followUp;
        this.prevention = prevention;
        this.status = status;
        this.closedOn = closedOn;
        this.reporterName = reporterName;
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
