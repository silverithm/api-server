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

    private String type; // MESSAGE, TYPING, READ, JOIN, LEAVE, KICK, DELETE
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

    /**
     * 메시지 삭제 알림.
     *
     * 지운 메시지를 그대로 실어 보낸다 — 받는 쪽은 같은 id의 메시지를 이걸로 갈아끼우면
     * 끝이고, 삭제 표시("삭제된 메시지입니다")도 displayContent에 이미 들어 있다.
     * id만 보내면 클라이언트마다 삭제 표현을 따로 만들어야 해서 서로 달라진다.
     */
    public static ChatWebSocketMessage deleteEvent(Long roomId, ChatMessageDTO message) {
        return ChatWebSocketMessage.builder()
                .type("DELETE")
                .roomId(roomId)
                .message(message)
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 메시지 수정 알림.
     *
     * 고친 메시지를 통째로 실어 보낸다 — 삭제 알림과 같은 방식으로, 받는 쪽은 같은 id의
     * 메시지를 이걸로 갈아끼우면 끝이다. "수정됨" 표시는 message.editedAt으로 판단한다.
     */
    public static ChatWebSocketMessage editEvent(Long roomId, ChatMessageDTO message) {
        return ChatWebSocketMessage.builder()
                .type("EDIT")
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
