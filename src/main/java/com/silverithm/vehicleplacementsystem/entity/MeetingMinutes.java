package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 회의록. 작성하고, 참석자에게 알리고, 서명을 병렬로 모은다.
 *
 * <p>결재선(ApprovalStep)은 순차 진행 모델이라 참석자 서명에 맞지 않아 별도 도메인으로 두고,
 * 완료되면 이관 문서(V1.79)와 같은 방식으로 결재함에 완결 문서로 들어간다.
 */
@Entity
@Table(name = "meeting_minutes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** 주제 */
    @Column(nullable = false)
    private String title;

    @Column
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, columnDefinition = "varchar(10)")
    private ApprovalStep.ApproverType authorType;

    @Column(name = "author_ref_id", nullable = false)
    private Long authorRefId;

    /** 작성 시점 이름 스냅샷 */
    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "meeting_start_at", nullable = false)
    private LocalDateTime meetingStartAt;

    @Column(name = "meeting_end_at")
    private LocalDateTime meetingEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Status status;

    /** [{"key","label","content"}] — 작성 시점 양식 스냅샷 + 정리된 내용 */
    @Column(name = "sections_json", columnDefinition = "JSON")
    private String sectionsJson;

    /** 타이핑 원문 — AI 정리 후에도 지우지 않는다 (잘못 정리되면 되돌릴 원본) */
    @Column(name = "raw_notes", columnDefinition = "LONGTEXT")
    private String rawNotes;

    /** 실시간 전사문 누적 — 브라우저가 죽어도 그 시점까지는 남게 주기 저장된다 */
    @Column(columnDefinition = "LONGTEXT")
    private String transcript;

    /** 완료 시 만들어지는 결재함 문서 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id")
    private ApprovalRequest approvalRequest;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "meetingMinutes", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<MeetingMinutesAttendee> attendees = new ArrayList<>();

    @OneToMany(mappedBy = "meetingMinutes", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    @Builder.Default
    private List<MeetingMinutesAudioChunk> audioChunks = new ArrayList<>();

    @OneToMany(mappedBy = "meetingMinutes", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<MeetingMinutesAttachment> attachments = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = Status.IN_PROGRESS;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        /** 회의 진행/작성 중 — 녹음·전사가 붙는다. 참석자에게는 아직 알리지 않았다 */
        IN_PROGRESS,
        /** 등록됨 — 참석자에게 알림이 나갔고 서명을 모으는 중 */
        REGISTERED,
        /** 완료 — 결재함에 완결 문서로 등록됨 */
        COMPLETED
    }
}
