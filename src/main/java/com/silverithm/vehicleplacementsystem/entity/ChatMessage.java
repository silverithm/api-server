package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(nullable = false)
    private String senderId;

    /**
     * 사람을 가리키는 제대로 된 참조 (V1.66). 문자열 senderId와 함께 채워둔다 —
     * 조회는 아직 문자열을 쓰고, 이 칼럼들이 FK로 무결성을 지킨다. 규칙은 {@link ChatPersonRef}.
     */
    @Column(name = "sender_member_id")
    private Long senderMemberId;

    @Column(name = "sender_app_user_id")
    private Long senderAppUserId;

    @Column(nullable = false)
    private String senderName;

    @Column
    private String senderPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @Column(length = 5000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    // 답글 관련 필드
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private ChatMessage replyTo;

    // 파일 관련 필드
    @Column
    private String fileUrl;

    @Column
    private String fileName;

    @Column
    private Long fileSize;

    @Column
    private String mimeType;

    /** 이미지 메시지의 축소 썸네일(긴 변 640px) S3 URL. 생성에 실패했거나 이미지가 아니면 null. */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMessageRead> readers = new ArrayList<>();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMessageReaction> reactions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isDeleted == null) {
            isDeleted = false;
        }
        // 문자열 식별자와 참조 칼럼이 어긋나지 않게 함께 채운다 (V1.66, ChatPersonRef 참고).
        // 이 호출이 위 if 안에 들어가면 안 된다 — isDeleted는 @Builder.Default로 항상 값이 있어
        // 그 블록이 돌지 않고, 발신자 참조가 NULL로 저장돼 내 메시지가 남의 것처럼 보인다(실제 사고).
        syncPersonRef();
    }

    public void delete() {
        this.isDeleted = true;
        this.content = null;
        this.fileUrl = null;
        this.fileName = null;
    }

    public String getDisplayContent() {
        if (isDeleted) {
            return "삭제된 메시지입니다";
        }
        if (type == MessageType.SYSTEM) {
            return content;
        }
        if (type == MessageType.IMAGE) {
            return "사진";
        }
        if (type == MessageType.FILE) {
            return fileName != null ? fileName : "파일";
        }
        return content;
    }

    public enum MessageType {
        TEXT("텍스트"),
        IMAGE("이미지"),
        FILE("파일"),
        SYSTEM("시스템");

        private final String displayName;

        MessageType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** 문자열 senderId에서 참조 칼럼을 파생시킨다. 어느 경로로 저장되든 둘이 같은 사람을 가리키게 한다. */
    @PreUpdate
    void syncPersonRef() {
        ChatPersonRef ref = ChatPersonRef.of(this.senderId);
        this.senderMemberId = ref.memberId();
        this.senderAppUserId = ref.appUserId();
    }
}
