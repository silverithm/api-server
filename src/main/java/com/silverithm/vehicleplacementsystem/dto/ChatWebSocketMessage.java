package com.silverithm.vehicleplacementsystem.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatWebSocketMessage {

    private String type; // MESSAGE, TYPING, READ, JOIN, LEAVE, KICK
    private Long roomId;
    private String senderId;
    private String senderName;
    private ChatMessageDTO message;
    private Boolean isTyping;
    private Long lastReadMessageId;
    private LocalDateTime timestamp;
    private Map<String, Object> data;

    public static ChatWebSocketMessage messageEvent(Long roomId, ChatMessageDTO message) {
        return ChatWebSocketMessage.builder()
                .type("MESSAGE")
                .roomId(roomId)
                .message(message)
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWebSocketMessage typingEvent(Long roomId, String userId, String userName, boolean isTyping) {
        return ChatWebSocketMessage.builder()
                .type("TYPING")
                .roomId(roomId)
                .senderId(userId)
                .senderName(userName)
                .isTyping(isTyping)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWebSocketMessage readEvent(Long roomId, String userId, String userName, Long lastReadMessageId) {
        return ChatWebSocketMessage.builder()
                .type("READ")
                .roomId(roomId)
                .senderId(userId)
                .senderName(userName)
                .lastReadMessageId(lastReadMessageId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWebSocketMessage joinEvent(Long roomId, String userId, String userName) {
        return ChatWebSocketMessage.builder()
                .type("JOIN")
                .roomId(roomId)
                .senderId(userId)
                .senderName(userName)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWebSocketMessage leaveEvent(Long roomId, String userId, String userName) {
        return ChatWebSocketMessage.builder()
                .type("LEAVE")
                .roomId(roomId)
                .senderId(userId)
                .senderName(userName)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
