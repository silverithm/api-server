package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScheduleCategory category = ScheduleCategory.OTHER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_id")
    private ScheduleLabel label;

    /**
     * 일정 자체의 색상 ("#RRGGBB"). null이면 카테고리 기본색으로 폴백한다.
     * label과 별개 필드다 — label은 구버전 클라이언트 호환을 위해 남아 있고,
     * 색은 이제 이 필드가 진실 소스다 (ScheduleDTO의 label shim이 이 값을 label.color에도 실어준다).
     */
    private String color;

    private String location;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalTime startTime;

    @Column(nullable = false)
    private LocalDate endDate;

    private LocalTime endTime;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isAllDay = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean sendNotification = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    private LocalDateTime completedAt;

    private String completedById;

    private String completedByName;

    // 목록 조회는 일정마다 참석자·할 일을 DTO로 옮기므로 지연 로딩이면 건수만큼 쿼리가 나간다
    // (연간일정처럼 수백 건을 한 번에 부를 때 그대로 수백 배가 된다).
    // BatchSize로 묶어 IN 절 몇 번으로 끝낸다.
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 200)
    @Builder.Default
    private List<ScheduleParticipant> participants = new ArrayList<>();

    /** 이 일정에서 수행해야 하는 할 일(담당자별 업무) */
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 200)
    @Builder.Default
    private List<ScheduleTask> tasks = new ArrayList<>();

    /** 담당자 (참석자와 구분되는 단일 지정, 미지정 가능) */
    private Long managerMemberId;

    private String managerName;

    /**
     * 담당자 id가 가리키는 테이블. MEMBER(members) | ADMIN(app_user).
     * members.id와 app_user.id는 서로 다른 시퀀스라 값이 겹칠 수 있어 종류를 함께 저장해야
     * managerMemberId만으로 엉뚱한 사람을 가리키는 사고(V1.88 이전)가 재발하지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "manager_type", nullable = false, length = 20)
    @Builder.Default
    private ManagerType managerType = ManagerType.MEMBER;

    public enum ManagerType {
        MEMBER, ADMIN
    }

    @Column(nullable = false)
    private String authorId;

    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isAllDay == null) {
            isAllDay = false;
        }
        if (category == null) {
            category = ScheduleCategory.OTHER;
        }
        if (sendNotification == null) {
            sendNotification = false;
        }
        if (isCompleted == null) {
            isCompleted = false;
        }
        if (managerType == null) {
            managerType = ManagerType.MEMBER;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void update(String title, String content, ScheduleCategory category,
                      ScheduleLabel label, String location, LocalDate startDate,
                      LocalTime startTime, LocalDate endDate, LocalTime endTime,
                      Boolean isAllDay, Boolean sendNotification, String color) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (category != null) {
            this.category = category;
        }
        this.label = label; // can be null to remove label
        // label과 다른 시맨틱: 필드 자체가 없으면(null) 기존 색을 유지한다.
        // 색을 안 보내는 클라이언트가 저장할 때마다 색을 지워버리는 사고를 막기 위함이다.
        // 빈 문자열이면 명시적으로 지우는 것("" → 카테고리 기본색 폴백)이다.
        if (color != null) {
            this.color = color.isBlank() ? null : color;
        }
        if (location != null) {
            this.location = location;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        this.startTime = startTime;
        if (endDate != null) {
            this.endDate = endDate;
        }
        this.endTime = endTime;
        if (isAllDay != null) {
            this.isAllDay = isAllDay;
        }
        if (sendNotification != null) {
            this.sendNotification = sendNotification;
        }
    }

    /**
     * 수행완료 상태 변경. 완료 해제 시 처리자/시각 정보를 비운다.
     */
    public void updateCompletion(boolean completed, String userId, String userName) {
        this.isCompleted = completed;
        if (completed) {
            this.completedAt = LocalDateTime.now();
            this.completedById = userId;
            this.completedByName = userName;
        } else {
            this.completedAt = null;
            this.completedById = null;
            this.completedByName = null;
        }
    }
}