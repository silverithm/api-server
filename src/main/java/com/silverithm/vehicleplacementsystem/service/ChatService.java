package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.*;
import com.silverithm.vehicleplacementsystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageReadRepository chatMessageReadRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    // ==================== 채팅방 관리 ====================

    /**
     * 채팅방 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRooms(Long companyId, String userId) {
        log.info("[Chat Service] 채팅방 목록 조회: companyId={}, userId={}", companyId, userId);

        List<ChatRoom> rooms = chatRoomRepository.findActiveRoomsByCompanyIdAndUserId(companyId, userId);

        return rooms.stream()
                .map(room -> {
                    ChatRoomDTO dto = ChatRoomDTO.fromEntity(room);

                    // 최신 메시지 설정
                    chatMessageRepository.findFirstByChatRoomIdOrderByCreatedAtDesc(room.getId())
                            .ifPresent(lastMsg -> dto.setLastMessage(ChatMessageDTO.fromEntity(lastMsg)));

                    // 안읽은 메시지 수 계산
                    int unreadCount = calculateUnreadCount(room.getId(), userId);
                    dto.setUnreadCount(unreadCount);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 채팅방 생성
     */
    @Transactional
    public ChatRoomDTO createChatRoom(Long companyId, ChatRoomCreateRequest request) {
        log.info("[Chat Service] 채팅방 생성: companyId={}, name={}", companyId, request.getName());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        // 채팅방 생성
        ChatRoom room = ChatRoom.builder()
                .name(request.getName())
                .description(request.getDescription())
                .company(company)
                .createdBy(request.getCreatedBy())
                .createdByName(request.getCreatedByName())
                .status(ChatRoom.ChatRoomStatus.ACTIVE)
                .build();

        ChatRoom savedRoom = chatRoomRepository.save(room);
        log.info("[Chat Service] 채팅방 저장 완료: id={}", savedRoom.getId());

        // 참가자 추가 (생성자는 ADMIN 역할)
        for (String participantId : request.getParticipantIds()) {
            String participantName = getParticipantName(participantId);
            ChatParticipant.ParticipantRole role =
                    participantId.equals(request.getCreatedBy()) ?
                            ChatParticipant.ParticipantRole.ADMIN :
                            ChatParticipant.ParticipantRole.MEMBER;

            ChatParticipant participant = ChatParticipant.builder()
                    .chatRoom(savedRoom)
                    .userId(participantId)
                    .userName(participantName)
                    .role(role)
                    .isActive(true)
                    .build();

            chatParticipantRepository.save(participant);
        }

        // 시스템 메시지 추가
        createSystemMessage(savedRoom, request.getCreatedByName() + "님이 채팅방을 만들었습니다.");

        return ChatRoomDTO.fromEntityWithParticipants(savedRoom);
    }

    /**
     * 채팅방 상세 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomDTO getChatRoomDetail(Long roomId) {
        log.info("[Chat Service] 채팅방 상세 조회: roomId={}", roomId);

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));

        return ChatRoomDTO.fromEntityWithParticipants(room);
    }

    /**
     * 채팅방 수정
     */
    @Transactional
    public ChatRoomDTO updateChatRoom(Long roomId, ChatRoomUpdateRequest request) {
        log.info("[Chat Service] 채팅방 수정: roomId={}", roomId);

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));

        if (request.getName() != null) {
            room.setName(request.getName());
        }
        if (request.getDescription() != null) {
            room.setDescription(request.getDescription());
        }
        if (request.getThumbnailUrl() != null) {
            room.setThumbnailUrl(request.getThumbnailUrl());
        }

        ChatRoom saved = chatRoomRepository.save(room);
        return ChatRoomDTO.fromEntity(saved);
    }

    /**
     * 채팅방 나가기
     */
    @Transactional
    public void leaveChatRoom(Long roomId, String userId) {
        log.info("[Chat Service] 채팅방 나가기: roomId={}, userId={}", roomId, userId);

        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)
                .orElseThrow(() -> new RuntimeException("참가자 정보를 찾을 수 없습니다"));

        participant.leave(ChatParticipant.LeaveReason.SELF_LEFT);
        chatParticipantRepository.save(participant);

        // 시스템 메시지 전송
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다"));
        createSystemMessage(room, participant.getUserName() + "님이 나갔습니다.");

        // WebSocket으로 퇴장 알림
        ChatWebSocketMessage leaveEvent = ChatWebSocketMessage.leaveEvent(roomId, userId, participant.getUserName());
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, leaveEvent);

        // 남은 참가자가 없으면 채팅방 보관
        long remainingCount = chatParticipantRepository.countByChatRoomIdAndIsActiveTrue(roomId);
        if (remainingCount == 0) {
            room.archive();
            chatRoomRepository.save(room);
        }
    }

    /**
     * 채팅방 삭제 (보관 처리)
     */
    @Transactional
    public void deleteChatRoom(Long roomId) {
        log.info("[Chat Service] 채팅방 삭제: roomId={}", roomId);

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));

        room.delete();
        chatRoomRepository.save(room);
    }

    // ==================== 참가자 관리 ====================

    /**
     * 참가자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatParticipantDTO> getParticipants(Long roomId) {
        log.info("[Chat Service] 참가자 목록 조회: roomId={}", roomId);

        List<ChatParticipant> participants =
                chatParticipantRepository.findByChatRoomIdAndIsActiveTrueOrderByJoinedAtAsc(roomId);

        return participants.stream()
                .map(ChatParticipantDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 참가자 추가
     */
    @Transactional
    public List<ChatParticipantDTO> addParticipants(Long roomId, List<String> userIds) {
        log.info("[Chat Service] 참가자 추가: roomId={}, userIds={}", roomId, userIds);

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));

        List<ChatParticipantDTO> addedParticipants = new ArrayList<>();
        StringBuilder joinMessage = new StringBuilder();

        for (String userId : userIds) {
            // 이미 참가 중인지 확인
            Optional<ChatParticipant> existing = chatParticipantRepository.findByChatRoomIdAndUserId(roomId, userId);

            if (existing.isPresent()) {
                ChatParticipant participant = existing.get();
                if (!participant.getIsActive()) {
                    // 재참가
                    participant.setIsActive(true);
                    participant.setLeftAt(null);
                    participant.setLeaveReason(null);
                    participant.setJoinedAt(LocalDateTime.now());
                    chatParticipantRepository.save(participant);
                    addedParticipants.add(ChatParticipantDTO.fromEntity(participant));

                    if (joinMessage.length() > 0) joinMessage.append(", ");
                    joinMessage.append(participant.getUserName());
                }
            } else {
                // 새 참가자
                String userName = getParticipantName(userId);
                ChatParticipant participant = ChatParticipant.builder()
                        .chatRoom(room)
                        .userId(userId)
                        .userName(userName)
                        .role(ChatParticipant.ParticipantRole.MEMBER)
                        .isActive(true)
                        .build();

                ChatParticipant saved = chatParticipantRepository.save(participant);
                addedParticipants.add(ChatParticipantDTO.fromEntity(saved));

                if (joinMessage.length() > 0) joinMessage.append(", ");
                joinMessage.append(userName);
            }
        }

        // 시스템 메시지
        if (joinMessage.length() > 0) {
            createSystemMessage(room, joinMessage + "님이 참가했습니다.");
        }

        return addedParticipants;
    }

    /**
     * 참가자 제거 (강퇴)
     */
    @Transactional
    public void removeParticipant(Long roomId, String userId, boolean isKicked) {
        log.info("[Chat Service] 참가자 제거: roomId={}, userId={}, isKicked={}", roomId, userId, isKicked);

        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)
                .orElseThrow(() -> new RuntimeException("참가자를 찾을 수 없습니다"));

        ChatParticipant.LeaveReason reason = isKicked ?
                ChatParticipant.LeaveReason.KICKED :
                ChatParticipant.LeaveReason.SELF_LEFT;

        participant.leave(reason);
        chatParticipantRepository.save(participant);

        // 시스템 메시지
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다"));

        String message = isKicked ?
                participant.getUserName() + "님이 퇴장되었습니다." :
                participant.getUserName() + "님이 나갔습니다.";
        createSystemMessage(room, message);

        // WebSocket 알림
        ChatWebSocketMessage event = ChatWebSocketMessage.leaveEvent(roomId, userId, participant.getUserName());
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, event);
    }

    // ==================== 메시지 관리 ====================

    /**
     * 메시지 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMessages(Long roomId, int page, int size) {
        log.info("[Chat Service] 메시지 목록 조회: roomId={}, page={}, size={}", roomId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(roomId, pageable);

        return messages.getContent().stream()
                .map(msg -> {
                    int readCount = (int) chatMessageReadRepository.countByMessageId(msg.getId());
                    return ChatMessageDTO.fromEntityWithReadCount(msg, readCount);
                })
                .collect(Collectors.toList());
    }

    /**
     * 메시지 전송
     */
    @Transactional
    public ChatMessageDTO sendMessage(Long roomId, ChatMessageCreateRequest request) {
        log.info("[Chat Service] 메시지 전송: roomId={}, senderId={}", roomId, request.getSenderId());

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));

        // 참가자 확인
        chatParticipantRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, request.getSenderId())
                .orElseThrow(() -> new RuntimeException("채팅방 참가자가 아닙니다"));

        // 메시지 타입 파싱
        ChatMessage.MessageType messageType = ChatMessage.MessageType.TEXT;
        if (request.getType() != null) {
            try {
                messageType = ChatMessage.MessageType.valueOf(request.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[Chat Service] 알 수 없는 메시지 타입: {}", request.getType());
            }
        }

        // 메시지 저장
        ChatMessage message = ChatMessage.builder()
                .chatRoom(room)
                .senderId(request.getSenderId())
                .senderName(request.getSenderName())
                .type(messageType)
                .content(request.getContent())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .mimeType(request.getMimeType())
                .isDeleted(false)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("[Chat Service] 메시지 저장 완료: id={}", saved.getId());

        // 채팅방 최신 메시지 시간 업데이트
        room.updateLastMessageAt();
        chatRoomRepository.save(room);

        // 발신자 읽음 처리
        markMessageAsRead(saved, request.getSenderId(), request.getSenderName());

        ChatMessageDTO dto = ChatMessageDTO.fromEntity(saved);

        // WebSocket으로 메시지 전송
        ChatWebSocketMessage wsMessage = ChatWebSocketMessage.messageEvent(roomId, dto);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, wsMessage);

        // FCM 푸시 알림 전송 (다른 참가자들에게)
        sendMessageNotification(room, saved);

        return dto;
    }

    /**
     * 메시지 삭제
     */
    @Transactional
    public void deleteMessage(Long roomId, Long messageId) {
        log.info("[Chat Service] 메시지 삭제: roomId={}, messageId={}", roomId, messageId);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("메시지를 찾을 수 없습니다: " + messageId));

        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new RuntimeException("해당 채팅방의 메시지가 아닙니다");
        }

        message.delete();
        chatMessageRepository.save(message);
    }

    // ==================== 읽음 처리 ====================

    /**
     * 읽음 처리
     */
    @Transactional
    public void markAsRead(Long roomId, String userId, String userName, Long lastMessageId) {
        log.info("[Chat Service] 읽음 처리: roomId={}, userId={}, lastMessageId={}", roomId, userId, lastMessageId);

        // 참가자 정보 업데이트
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)
                .orElseThrow(() -> new RuntimeException("참가자를 찾을 수 없습니다"));

        participant.updateLastRead(lastMessageId);
        chatParticipantRepository.save(participant);

        // 안읽은 메시지들에 대해 읽음 기록 추가
        List<Long> unreadMessageIds = chatMessageReadRepository.findUnreadMessageIds(roomId, lastMessageId, userId);
        for (Long msgId : unreadMessageIds) {
            ChatMessage message = chatMessageRepository.findById(msgId).orElse(null);
            if (message != null) {
                markMessageAsRead(message, userId, userName);
            }
        }

        // WebSocket으로 읽음 상태 알림
        ChatWebSocketMessage readEvent = ChatWebSocketMessage.readEvent(roomId, userId, userName, lastMessageId);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/read", readEvent);
    }

    /**
     * 메시지 읽은 사람 목록
     */
    @Transactional(readOnly = true)
    public List<ChatMessageReaderDTO> getMessageReaders(Long roomId, Long messageId) {
        log.info("[Chat Service] 메시지 읽은 사람 조회: roomId={}, messageId={}", roomId, messageId);

        List<ChatMessageRead> readers = chatMessageReadRepository.findByMessageIdOrderByReadAtDesc(messageId);

        return readers.stream()
                .map(ChatMessageReaderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== 미디어 ====================

    /**
     * 공유된 미디어 조회
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getSharedMedia(Long roomId, String type) {
        log.info("[Chat Service] 공유된 미디어 조회: roomId={}, type={}", roomId, type);

        List<ChatMessage> messages;
        if (type != null && !type.isEmpty()) {
            try {
                ChatMessage.MessageType messageType = ChatMessage.MessageType.valueOf(type.toUpperCase());
                messages = chatMessageRepository.findByTypeAndChatRoomId(roomId, messageType);
            } catch (IllegalArgumentException e) {
                messages = chatMessageRepository.findSharedMedia(roomId);
            }
        } else {
            messages = chatMessageRepository.findSharedMedia(roomId);
        }

        return messages.stream()
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== 회원 삭제 시 처리 ====================

    /**
     * 회원 삭제 시 모든 채팅방에서 자동 퇴장
     */
    @Transactional
    public void handleMemberDeleted(String userId, String userName) {
        log.info("[Chat Service] 회원 삭제로 인한 채팅방 퇴장 처리: userId={}", userId);

        List<ChatParticipant> participations = chatParticipantRepository.findByUserIdAndIsActiveTrue(userId);

        for (ChatParticipant participant : participations) {
            participant.leave(ChatParticipant.LeaveReason.ACCOUNT_DELETED);
            chatParticipantRepository.save(participant);

            // 시스템 메시지
            ChatRoom room = participant.getChatRoom();
            createSystemMessage(room, userName + "님이 퇴장되었습니다. (계정 삭제)");

            // WebSocket 알림
            ChatWebSocketMessage event = ChatWebSocketMessage.leaveEvent(room.getId(), userId, userName);
            messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), event);
        }
    }

    // ==================== 헬퍼 메서드 ====================

    private void createSystemMessage(ChatRoom room, String content) {
        ChatMessage systemMessage = ChatMessage.builder()
                .chatRoom(room)
                .senderId("system")
                .senderName("시스템")
                .type(ChatMessage.MessageType.SYSTEM)
                .content(content)
                .isDeleted(false)
                .build();

        chatMessageRepository.save(systemMessage);

        // WebSocket으로 시스템 메시지 전송
        ChatMessageDTO dto = ChatMessageDTO.fromEntity(systemMessage);
        ChatWebSocketMessage wsMessage = ChatWebSocketMessage.messageEvent(room.getId(), dto);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), wsMessage);
    }

    private void markMessageAsRead(ChatMessage message, String userId, String userName) {
        if (!chatMessageReadRepository.existsByMessageIdAndUserId(message.getId(), userId)) {
            ChatMessageRead read = ChatMessageRead.builder()
                    .message(message)
                    .userId(userId)
                    .userName(userName)
                    .build();
            chatMessageReadRepository.save(read);
        }
    }

    private int calculateUnreadCount(Long roomId, String userId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)
                .orElse(null);

        if (participant == null) {
            return 0;
        }

        Long lastReadMessageId = participant.getLastReadMessageId();
        if (lastReadMessageId == null) {
            return (int) chatMessageRepository.countAllUnreadMessages(roomId, userId);
        }

        return (int) chatMessageRepository.countUnreadMessages(roomId, lastReadMessageId, userId);
    }

    private String getParticipantName(String userId) {
        try {
            Long memberIdLong = Long.parseLong(userId);
            return memberRepository.findById(memberIdLong)
                    .map(Member::getName)
                    .orElse("사용자");
        } catch (NumberFormatException e) {
            return "사용자";
        }
    }

    private void sendMessageNotification(ChatRoom room, ChatMessage message) {
        try {
            List<ChatParticipant> participants =
                    chatParticipantRepository.findByChatRoomIdAndIsActiveTrueOrderByJoinedAtAsc(room.getId());

            for (ChatParticipant participant : participants) {
                // 발신자 제외
                if (participant.getUserId().equals(message.getSenderId())) {
                    continue;
                }

                try {
                    Long memberIdLong = Long.parseLong(participant.getUserId());
                    Member member = memberRepository.findById(memberIdLong).orElse(null);

                    if (member != null && member.getFcmToken() != null) {
                        FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                                .recipientToken(member.getFcmToken())
                                .title(room.getName())
                                .message(message.getSenderName() + ": " + message.getDisplayContent())
                                .recipientUserId(participant.getUserId())
                                .recipientUserName(participant.getUserName())
                                .type("CHAT")
                                .relatedEntityId(room.getId())
                                .relatedEntityType("chatRoom")
                                .data(Map.of(
                                        "type", "chat",
                                        "roomId", String.valueOf(room.getId()),
                                        "messageId", String.valueOf(message.getId())
                                ))
                                .build();

                        notificationService.sendAndSaveNotification(request);
                    }
                } catch (Exception e) {
                    log.error("[Chat Service] FCM 전송 실패: userId={}, error={}",
                            participant.getUserId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Chat Service] 메시지 알림 전송 중 오류: {}", e.getMessage());
        }
    }
}
