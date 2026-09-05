package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.ChatMediaType;
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

    /** 마지막으로 고친 시각. 한 번도 안 고쳤으면 null — "수정됨" 표시와 감사 기록을 함께 겸한다. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

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

    /** 텍스트 내용을 고친다. 새 내용이 기존과 같으면 무동작(editedAt을 찍지 않는다) — 호출자가 판단해 부른다. */
    public void edit(String newContent) {
        this.content = newContent;
        this.editedAt = LocalDateTime.now();
    }

    /**
     * 대화 밖에서 이 메시지를 한 줄로 가리키는 말 — 방 목록 미리보기, 푸시 알림, 알림함이 쓴다.
     *
     * <p>동영상은 저장 타입이 FILE이라 예전에는 파일 이름이 그대로 나왔다. 앱이 압축하면서 붙인
     * {@code compressed_1757….mp4} 같은 임시 이름이 잠금화면과 방 목록에 뜨는 이유였다.
     * 저장 타입 대신 화면용 종류(ChatMediaType)로 판단해 "동영상"이라고 말한다.
     */
    public String getDisplayContent() {
        if (isDeleted) {
            return "삭제된 메시지입니다";
        }
        if (type == MessageType.SYSTEM) {
            return content;
        }

        String media = ChatMediaType.resolve(type.name(), mimeType, fileName);
        if (ChatMediaType.IMAGE.equals(media)) {
            return "사진";
        }
        if (ChatMediaType.VIDEO.equals(media)) {
            return "동영상";
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
