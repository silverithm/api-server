package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ChatMessageCreateRequest;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageDTO;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatMessageRead;
import com.silverithm.vehicleplacementsystem.entity.ChatPersonRef;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageReadRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 메시지 한 건을 저장하는 트랜잭션. 방송과 알림은 여기 없다 — 커밋된 뒤 {@link ChatService}가 한다.
 *
 * <p><b>왜 따로 뗐나.</b> 전에는 저장·방송·알림이 한 트랜잭션 안에 있었다. 그러면
 * ① 커밋되기 전에 방송이 나가 되돌아간 메시지가 상대 화면에 뜰 수 있고,
 * ② 교착으로 실패했을 때 트랜잭션째 다시 시도할 자리가 없다.
 * 저장만 떼어 두면 {@link ChatService#sendMessage}가 실패한 저장을 새 트랜잭션으로 다시 부를 수 있다.
 *
 * <p><b>같은 메시지가 두 번 오면.</b> 보내는 쪽이 붙인 {@code clientMessageId}로 먼저 찾아본다.
 * 있으면 저장하지 않고 그것을 돌려준다({@link Stored#duplicate()}). 조회와 저장 사이에 다른
 * 요청이 끼어들면 유니크 제약이 막고, 그 예외는 호출자가 받아 다시 찾는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageWriter {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageReadRepository chatMessageReadRepository;

    /** 저장 결과. {@code duplicate}가 true면 이번 요청으로 새로 저장된 것이 아니다. */
    public record Stored(ChatMessageDTO dto, boolean duplicate) {
        public Long messageId() { return dto.getId(); }
    }

    @Transactional
    public Stored persist(Long roomId, ChatMessageCreateRequest request, String senderPosition) {
        String senderId = request.getSenderId();

        Optional<ChatMessage> already = findExisting(roomId, senderId, request.getClientMessageId());
        if (already.isPresent()) {
            log.info("[Chat Writer] 재전송 — 기존 메시지 반환: roomId={}, clientMessageId={}, id={}",
                    roomId, request.getClientMessageId(), already.get().getId());
            return new Stored(ChatMessageDTO.fromEntityWithReadCount(already.get(), 1), true);
        }

        // 방 행의 배타 잠금을 **먼저** 잡는다. 순서가 바뀌면 교착이 난다 — 이유는 touchLastMessageAt 주석.
        if (chatRoomRepository.touchLastMessageAt(roomId, LocalDateTime.now()) == 0) {
            throw new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId);
        }
        ChatRoom room = chatRoomRepository.getReferenceById(roomId);

        ChatPersonRef sender = ChatPersonRef.of(senderId);
        chatParticipantRepository.findActiveByRoomAndPerson(roomId, sender.memberId(), sender.appUserId())
                .orElseThrow(() -> new RuntimeException("채팅방 참가자가 아닙니다"));

        ChatMessage.MessageType messageType = ChatMessage.MessageType.TEXT;
        if (request.getType() != null) {
            try {
                messageType = ChatMessage.MessageType.valueOf(request.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[Chat Writer] 알 수 없는 메시지 타입: {}", request.getType());
            }
        }

        ChatMessage replyTo = null;
        if (request.getReplyToId() != null) {
            replyTo = chatMessageRepository.findById(request.getReplyToId()).orElse(null);
        }

        ChatMessage message = ChatMessage.builder()
                .chatRoom(room)
                .senderId(senderId)
                .senderName(request.getSenderName())
                .senderPosition(senderPosition)
                .clientMessageId(blankToNull(request.getClientMessageId()))
                .type(messageType)
                .content(request.getContent())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .mimeType(request.getMimeType())
                .thumbnailUrl(request.getThumbnailUrl())
                .replyTo(replyTo)
                .isDeleted(false)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        // 유니크 제약 위반은 여기서 터져야 호출자가 '이미 있는 메시지'로 처리할 수 있다.
        chatMessageRepository.flush();

        // 보낸 사람은 자기 메시지를 읽은 것으로 — 새 메시지라 겹칠 수 없다
        ChatPersonRef reader = ChatPersonRef.of(senderId);
        if (!chatMessageReadRepository.existsByMessageAndPerson(saved.getId(), reader.memberId(), reader.appUserId())) {
            chatMessageReadRepository.save(ChatMessageRead.builder()
                    .message(saved)
                    .userId(senderId)
                    .userName(request.getSenderName())
                    .build());
        }

        log.info("[Chat Writer] 메시지 저장 완료: id={}, clientMessageId={}", saved.getId(), saved.getClientMessageId());
        return new Stored(ChatMessageDTO.fromEntityWithReadCount(saved, 1), false);
    }

    /** 같은 사람이 같은 식별자로 이미 보낸 메시지. 식별자가 없으면(구버전) 항상 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<ChatMessage> findExisting(Long roomId, String senderId, String clientMessageId) {
        String key = blankToNull(clientMessageId);
        if (key == null) {
            return Optional.empty();
        }
        return chatMessageRepository.findByChatRoomIdAndSenderIdAndClientMessageId(roomId, senderId, key);
    }

    /** 이미 저장된 메시지를 DTO로. 유니크 제약에 막힌 뒤 되찾을 때 쓴다. */
    @Transactional(readOnly = true)
    public Optional<ChatMessageDTO> findExistingDto(Long roomId, String senderId, String clientMessageId) {
        return findExisting(roomId, senderId, clientMessageId)
                .map(m -> ChatMessageDTO.fromEntityWithReadCount(m, 1));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
