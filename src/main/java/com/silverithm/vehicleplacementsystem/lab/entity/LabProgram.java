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
 * 프로그램 일지 — 하루 활동 1건에 어르신 여러 명이 참여한다.
 *
 * <p>참여자는 어르신 id JSON 배열 한 칸에 담는다(최대 100명). 서비스가 모두 같은 기관 어르신인지
 * 확인한 뒤에만 저장하므로 이 열의 id는 신뢰할 수 있지만, 이후 어르신이 지워지면 dangling이 될 수 있어
 * 조회 때 걸러 낸다. 목표·내용·반응은 참여자의 상태가 적히는 서술이라 암호화한다.
 */
@Entity
@Table(name = "lab_programs", indexes = @Index(
        name = "idx_lab_programs_company_date", columnList = "company_id, program_date"))
@Getter
@NoArgsConstructor
public class LabProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "program_date", nullable = false)
    private LocalDate programDate;

    /** 'HH:MM' */
    @Column(name = "start_time", length = 5, columnDefinition = "CHAR(5)")
    private String startTime;

    /** COGNITIVE | PHYSICAL | LEISURE | SOCIAL | OTHER */
    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "reaction", columnDefinition = "TEXT")
    private String reaction;

    @Column(name = "instructor_name", length = 50)
    private String instructorName;

    /** 참여 어르신 id JSON 배열. id만 담겨 있어 암호화하지 않는다 */
    @Column(name = "participant_ids", nullable = false, columnDefinition = "TEXT")
    private String participantIds;

    /** 진행 시간(분) 10~480. 옛 일지는 null */
    @Column(name = "duration_minutes")
    private Short durationMinutes;

    /** 인지활동형 프로그램 여부 — 5등급·인지지원 어르신의 급여비용 산정 조건(고시 제32조⑩) */
    @Column(name = "cognitive_activity", nullable = false)
    private boolean cognitiveActivity;

    /** PROGRAM_MANAGER | DEMENTIA_CAREGIVER | EXTERNAL_INSTRUCTOR | OTHER. 인지활동형이면 필수 */
    @Column(name = "provider_kind", length = 20)
    private String providerKind;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabProgram(Long companyId) {
        this.companyId = companyId;
    }

    /** 등록과 수정이 같은 경로 — 입력으로 내용을 통째로 덮어쓴다 */
    public void fill(LocalDate programDate, String startTime, String category, String title, String goal,
                     String content, String reaction, String instructorName, String participantIds,
                     boolean aiAssisted, Short durationMinutes, boolean cognitiveActivity, String providerKind) {
        this.programDate = programDate;
        this.startTime = startTime;
        this.category = category;
        this.title = title;
        this.goal = goal;
        this.content = content;
        this.reaction = reaction;
        this.instructorName = instructorName;
        this.participantIds = participantIds;
        this.aiAssisted = aiAssisted;
        this.durationMinutes = durationMinutes;
        this.cognitiveActivity = cognitiveActivity;
        this.providerKind = providerKind;
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
