package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 일정에 딸린 할 일. 담당자가 자기 항목을 수행완료로 체크한다.
 * 참석자(ScheduleParticipant)와는 별개 개념이다.
 */
@Entity
@Table(name = "schedule_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(nullable = false, length = 500)
    private String content;

    private Long assigneeMemberId;

    private String assigneeName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    private LocalDateTime completedAt;

    private String completedById;

    private String completedByName;

    private String createdById;

    private String createdByName;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isCompleted == null) {
            isCompleted = false;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateContent(String content, Long assigneeMemberId, String assigneeName) {
        if (content != null && !content.isBlank()) {
            this.content = content;
        }
        // 담당자는 null로 비우는 것도 허용한다 (미지정으로 되돌리기)
        this.assigneeMemberId = assigneeMemberId;
        this.assigneeName = assigneeName;
    }

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
