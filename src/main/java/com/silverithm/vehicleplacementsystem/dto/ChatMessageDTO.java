package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private String senderPosition;
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

    @Builder.Default
    private List<ChatReactionDTO.ReactionSummary> reactions = new ArrayList<>();

    // 답글 관련 필드
    private Long replyToId;
    private String replyToSenderName;
    private String replyToContent;
    private String replyToType;

    public static ChatMessageDTO fromEntity(ChatMessage message) {
        ChatMessageDTO.ChatMessageDTOBuilder builder = ChatMessageDTO.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoom() != null ? message.getChatRoom().getId() : null)
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderPosition(message.getSenderPosition())
                .type(message.getType().name())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isDeleted(message.getIsDeleted())
                .fileUrl(message.getFileUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .mimeType(message.getMimeType())
                .readCount(message.getReaders() != null ? message.getReaders().size() : 0)
                .displayContent(message.getDisplayContent());

        // 답글 정보 포함
        if (message.getReplyTo() != null) {
            ChatMessage replyTo = message.getReplyTo();
            builder.replyToId(replyTo.getId())
                    .replyToSenderName(replyTo.getSenderName())
                    .replyToContent(replyTo.getIsDeleted() ? "삭제된 메시지입니다" : replyTo.getDisplayContent())
                    .replyToType(replyTo.getType().name());
        }

        return builder.build();
    }

    public static ChatMessageDTO fromEntityWithReadCount(ChatMessage message, int readCount) {
        ChatMessageDTO dto = fromEntity(message);
        dto.setReadCount(readCount);
        return dto;
    }
}
