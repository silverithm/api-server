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

    @Column(nullable = false)
    private String senderName;

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

    // 파일 관련 필드
    @Column
    private String fileUrl;

    @Column
    private String fileName;

    @Column
    private Long fileSize;

    @Column
    private String mimeType;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMessageRead> readers = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isDeleted == null) {
            isDeleted = false;
        }
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
}
