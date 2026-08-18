package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(nullable = false)
    private String userId;

    /**
     * 사람을 가리키는 제대로 된 참조 (V1.66). 문자열 userId와 함께 채워둔다 —
     * 조회는 아직 문자열을 쓰고, 이 칼럼들이 FK로 무결성을 지킨다. 규칙은 {@link ChatPersonRef}.
     */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "app_user_id")
    private Long appUserId;

    @Column(nullable = false)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column
    private LocalDateTime lastReadAt;

    @Column
    private Long lastReadMessageId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column
    private LocalDateTime leftAt;

    @Enumerated(EnumType.STRING)
    @Column
    private LeaveReason leaveReason;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        // 문자열 식별자와 참조 칼럼이 어긋나지 않게 함께 채운다 (V1.66, ChatPersonRef 참고).
        // 이 호출이 위 if 안에 들어가면 안 된다 — isActive는 @Builder.Default로 항상 값이 있어
        // 그 블록이 돌지 않고, 참조 칼럼이 전부 NULL로 저장돼 조회에서 사라진다(실제 사고).
        syncPersonRef();
    }

    public void leave(LeaveReason reason) {
        this.isActive = false;
        this.leftAt = LocalDateTime.now();
        this.leaveReason = reason;
    }

    public void updateLastRead(Long messageId) {
        this.lastReadAt = LocalDateTime.now();
        this.lastReadMessageId = messageId;
    }

    public enum ParticipantRole {
        ADMIN("방장"),
        MEMBER("참가자");

        private final String displayName;

        ParticipantRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum LeaveReason {
        SELF_LEFT("자발적 퇴장"),
        KICKED("강제 퇴장"),
        ACCOUNT_DELETED("계정 삭제");

        private final String displayName;

        LeaveReason(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** 문자열 userId에서 참조 칼럼을 파생시킨다. 어느 경로로 저장되든 둘이 같은 사람을 가리키게 한다. */
    @PreUpdate
    void syncPersonRef() {
        ChatPersonRef ref = ChatPersonRef.of(this.userId);
        this.memberId = ref.memberId();
        this.appUserId = ref.appUserId();
    }
}
