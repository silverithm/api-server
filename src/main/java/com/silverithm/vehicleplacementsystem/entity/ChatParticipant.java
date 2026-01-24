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
}
