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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사례관리 회의 1건. 참석자(직원 id)·의견·대상(어르신 id와 반영·평가)은 JSON 열이다.
 * 회의록만 사례가 오갈 수 있어 암호화한다.
 */
@Entity
@Table(name = "lab_case_meetings", indexes = @Index(
        name = "idx_lab_case_meetings_company_held", columnList = "company_id, held_on"))
@Getter
@NoArgsConstructor
public class LabCaseMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attendee_ids", nullable = false)
    private String attendeeIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opinions", nullable = false)
    private String opinions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "targets", nullable = false)
    private String targets;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "minutes", columnDefinition = "TEXT")
    private String minutes;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabCaseMeeting(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate heldOn, String attendeeIds, String opinions, String targets, String minutes,
                     String writerName, boolean aiAssisted) {
        this.heldOn = heldOn;
        this.attendeeIds = attendeeIds;
        this.opinions = opinions;
        this.targets = targets;
        this.minutes = minutes;
        this.writerName = writerName;
        this.aiAssisted = aiAssisted;
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
