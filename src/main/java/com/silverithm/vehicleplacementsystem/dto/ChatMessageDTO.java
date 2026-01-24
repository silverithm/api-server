package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {

    private Long id;
    private Long chatRoomId;
    private String senderId;
    private String senderName;
    private String type;
    private String content;
    private LocalDateTime createdAt;
    private Boolean isDeleted;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private int readCount;
    private String displayContent;

    public static ChatMessageDTO fromEntity(ChatMessage message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoom() != null ? message.getChatRoom().getId() : null)
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .type(message.getType().name())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isDeleted(message.getIsDeleted())
                .fileUrl(message.getFileUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .mimeType(message.getMimeType())
                .readCount(message.getReaders() != null ? message.getReaders().size() : 0)
                .displayContent(message.getDisplayContent())
                .build();
    }

    public static ChatMessageDTO fromEntityWithReadCount(ChatMessage message, int readCount) {
        ChatMessageDTO dto = fromEntity(message);
        dto.setReadCount(readCount);
        return dto;
    }
}
