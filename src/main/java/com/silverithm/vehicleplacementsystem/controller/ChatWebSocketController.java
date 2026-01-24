package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ChatMessageCreateRequest;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageDTO;
import com.silverithm.vehicleplacementsystem.dto.ChatWebSocketMessage;
import com.silverithm.vehicleplacementsystem.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 메시지 전송
     * 클라이언트: /app/chat/{roomId}/send
     * 브로드캐스트: /topic/chat/{roomId}
     */
    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload ChatMessageCreateRequest request) {

        try {
            log.info("[Chat WebSocket] 메시지 전송: roomId={}, senderId={}", roomId, request.getSenderId());

            // ChatService를 통해 메시지 저장 및 브로드캐스트 (이미 내부에서 처리)
            chatService.sendMessage(roomId, request);

        } catch (Exception e) {
            log.error("[Chat WebSocket] 메시지 전송 오류: roomId={}, error={}", roomId, e.getMessage());
        }
    }

    /**
     * 타이핑 상태 전송
     * 클라이언트: /app/chat/{roomId}/typing
     * 브로드캐스트: /topic/chat/{roomId}/typing
     */
    @MessageMapping("/chat/{roomId}/typing")
    public void sendTypingStatus(
            @DestinationVariable Long roomId,
            @Payload Map<String, Object> payload) {

        try {
            String userId = (String) payload.get("userId");
            String userName = (String) payload.get("userName");
            Boolean isTyping = (Boolean) payload.get("isTyping");

            log.debug("[Chat WebSocket] 타이핑 상태: roomId={}, userId={}, isTyping={}",
                    roomId, userId, isTyping);

            ChatWebSocketMessage typingEvent = ChatWebSocketMessage.typingEvent(
                    roomId, userId, userName, isTyping != null && isTyping);

            messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/typing", typingEvent);

        } catch (Exception e) {
            log.error("[Chat WebSocket] 타이핑 상태 전송 오류: roomId={}, error={}", roomId, e.getMessage());
        }
    }

    /**
     * 읽음 처리
     * 클라이언트: /app/chat/{roomId}/read
     * 브로드캐스트: /topic/chat/{roomId}/read
     */
    @MessageMapping("/chat/{roomId}/read")
    public void markAsRead(
            @DestinationVariable Long roomId,
            @Payload Map<String, Object> payload) {

        try {
            String userId = (String) payload.get("userId");
            String userName = (String) payload.get("userName");
            Long lastMessageId = Long.valueOf(payload.get("lastMessageId").toString());

            log.info("[Chat WebSocket] 읽음 처리: roomId={}, userId={}, lastMessageId={}",
                    roomId, userId, lastMessageId);

            // ChatService를 통해 읽음 처리 및 브로드캐스트 (이미 내부에서 처리)
            chatService.markAsRead(roomId, userId, userName, lastMessageId);

        } catch (Exception e) {
            log.error("[Chat WebSocket] 읽음 처리 오류: roomId={}, error={}", roomId, e.getMessage());
        }
    }
}
