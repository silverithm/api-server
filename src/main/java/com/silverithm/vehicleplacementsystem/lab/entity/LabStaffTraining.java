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

/**
 * 직원 교육·회의 1건 — 법정 교육 이수와 사례회의의 근거 기록.
 *
 * <p>참석자는 직원(members.id) JSON 배열이다(최대 200명). 내용은 사례회의에서 어르신 상황이 오갈 수 있어
 * 암호화한다. 주제(topic)는 목록·평가 자료에 그대로 보이는 제목이라 평문이다.
 */
@Entity
@Table(name = "lab_staff_trainings", indexes = @Index(
        name = "idx_lab_staff_trainings_company_date", columnList = "company_id, held_on"))
@Getter
@NoArgsConstructor
public class LabStaffTraining {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** TRAINING | MEETING */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "instructor", length = 50)
    private String instructor;

    /** 참석 직원 id JSON 배열 */
    @Column(name = "attendee_ids", nullable = false, columnDefinition = "TEXT")
    private String attendeeIds;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabStaffTraining(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String kind, LocalDate heldOn, String category, String topic, String instructor,
                     String attendeeIds, String content, boolean aiAssisted) {
        this.kind = kind;
        this.heldOn = heldOn;
        this.category = category;
        this.topic = topic;
        this.instructor = instructor;
        this.attendeeIds = attendeeIds;
        this.content = content;
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
