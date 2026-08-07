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
}
