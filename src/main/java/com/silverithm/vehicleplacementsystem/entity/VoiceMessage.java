package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 고충·신고 + 건의함 (VoiceBox).
 * 직원이 기관에 남기는 목소리. 고충·신고는 익명 제출이 가능하며,
 * 열람은 기관 관리자만 할 수 있다. 익명 글의 작성자 정보는 DB에는 저장하되
 * (본인 내역 조회·답변 확인용) 관리자 응답에서는 가린다.
 */
@Entity
@Table(name = "voice_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private VoiceType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, columnDefinition = "varchar(10)")
    private ApprovalStep.ApproverType authorType;

    @Column(name = "author_ref_id", nullable = false)
    private Long authorRefId;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private VoiceStatus status;

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = VoiceStatus.RECEIVED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum VoiceType {
        GRIEVANCE,   // 고충·신고
        SUGGESTION   // 건의
    }

    public enum VoiceStatus {
        RECEIVED,    // 접수
        IN_REVIEW,   // 확인중
        RESOLVED,    // 조치완료(고충) / 반영됨(건의)
        ON_HOLD      // 보류
    }
}
