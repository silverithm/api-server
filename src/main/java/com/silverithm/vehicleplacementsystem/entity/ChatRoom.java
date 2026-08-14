package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String createdBy;

    /**
     * 사람을 가리키는 제대로 된 참조 (V1.66). 문자열 createdBy와 함께 채워둔다 —
     * 조회는 아직 문자열을 쓰고, 이 칼럼들이 FK로 무결성을 지킨다. 규칙은 {@link ChatPersonRef}.
     */
    @Column(name = "creator_member_id")
    private Long creatorMemberId;

    @Column(name = "creator_app_user_id")
    private Long creatorAppUserId;

    @Column(nullable = false)
    private String createdByName;

    @Column
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ChatRoomStatus status = ChatRoomStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastMessageAt;

    /**
     * 방 상단에 고정하는 공지.
     *
     * 내용을 스냅샷으로 들고 있어 원본 메시지가 지워지거나 오래된 메시지로 밀려도 공지는 그대로 보인다.
     * notice_message_id는 원본으로 이동하기 위한 참조일 뿐이라 FK를 걸지 않는다.
     */
    @Column(name = "notice_message_id")
    private Long noticeMessageId;

    @Column(name = "notice_content", length = 1000)
    private String noticeContent;

    @Column(name = "notice_by_name", length = 100)
    private String noticeByName;

    @Column(name = "notice_at")
    private LocalDateTime noticeAt;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // 문자열 식별자와 참조 칼럼이 어긋나지 않게 함께 채운다 (V1.66, ChatPersonRef 참고)
        syncPersonRef();
    }

    public void updateLastMessageAt() {
        this.lastMessageAt = LocalDateTime.now();
    }

    public void archive() {
        this.status = ChatRoomStatus.ARCHIVED;
    }

    public void delete() {
        this.status = ChatRoomStatus.DELETED;
    }

    public enum ChatRoomStatus {
        ACTIVE("활성"),
        ARCHIVED("보관됨"),
        DELETED("삭제됨");

        private final String displayName;

        ChatRoomStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** 문자열 createdBy에서 참조 칼럼을 파생시킨다. 어느 경로로 저장되든 둘이 같은 사람을 가리키게 한다. */
    @PreUpdate
    void syncPersonRef() {
        ChatPersonRef ref = ChatPersonRef.of(this.createdBy);
        this.creatorMemberId = ref.memberId();
        this.creatorAppUserId = ref.appUserId();
    }
}
