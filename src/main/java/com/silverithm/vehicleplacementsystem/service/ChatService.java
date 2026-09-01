package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.*;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.*;
import com.silverithm.vehicleplacementsystem.util.AdminDisplay;
import com.silverithm.vehicleplacementsystem.util.PersonDisplay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /**
     * 관리자(AppUser)를 가리키는 채팅 사용자 식별자의 접두사.
     *
     * 관리자와 직원(Member)은 서로 다른 테이블이라 id가 겹친다. 채팅이 원시 숫자 하나로만
     * 사람을 가리키면 관리자 3번과 직원 3번이 같은 사람이 되어, 이름·프로필이 뒤바뀌고
     * (방, user_id) 유니크 제약 때문에 둘이 같은 방에 들어가지도 못하며, 서로의 메시지를
     * 자기 것으로 보게 된다. 그래서 관리자만 접두사를 붙여 갈라놓는다.
     * 결재선이 이미 쓰고 있는 표기(approverIdLegacy = "admin_&lt;id&gt;")와 같은 규약이다.
     */
    public static final String ADMIN_ID_PREFIX = "admin_";

    /** 채팅 식별자가 관리자를 가리키는지 */
    public static boolean isAdminChatUserId(String userId) {
        return userId != null && userId.startsWith(ADMIN_ID_PREFIX);
    }

    /** 관리자 AppUser id를 채팅 식별자로 */
    public static String toAdminChatUserId(Long appUserId) {
        return ADMIN_ID_PREFIX + appUserId;
    }

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageReadRepository chatMessageReadRepository;
    private final ChatMessageReactionRepository chatMessageReactionRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final ResourceScopeGuard resourceScopeGuard;


    /**
     * 채팅 식별자 문자열을 참조 칼럼 짝으로 푼다.
     * 조회는 이제 member_id / app_user_id를 본다 (V1.68) — 문자열은 호환을 위해 계속 함께 저장한다.
     */
    private static ChatPersonRef person(String chatUserId) {
        return ChatPersonRef.of(chatUserId);
    }

    // ==================== 채팅방 관리 ====================

    /**
     * 채팅방 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRooms(Long companyId, String userId) {
        log.info("[Chat Service] 채팅방 목록 조회: companyId={}, userId={}", companyId, userId);

        List<ChatRoom> rooms = chatRoomRepository.findActiveRoomsByCompanyIdAndPerson(companyId, person(userId).memberId(), person(userId).appUserId());

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
        // 같은 사람을 두 번 보내오면 (방, user_id) 유니크 제약에 걸리므로 먼저 걸러낸다
        for (String participantId : new LinkedHashSet<>(request.getParticipantIds())) {
            requireInvitable(participantId, companyId);
            boolean isCreator = participantId.equals(request.getCreatedBy());
            // 만든 사람의 이름은 로그인 세션이 알려준 값이 가장 정확하다
            String participantName = isCreator ? request.getCreatedByName() : getParticipantName(participantId);
            ChatParticipant.ParticipantRole role =
                    isCreator ?
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
        resourceScopeGuard.requireSameCompany(room.getCompany());

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
        resourceScopeGuard.requireSameCompany(room.getCompany());

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
     * 방 공지 설정 / 해제.
     *
     * messageId가 null이면 공지를 내린다. 설정할 때는 내용을 스냅샷으로 복사해두어
     * 원본 메시지가 삭제되어도 공지 문구는 남게 한다.
     */
    @Transactional
    public ChatRoomDTO updateChatRoomNotice(Long roomId, Long messageId, String setByName) {
        return updateChatRoomNotice(roomId, messageId, setByName, null);
    }

    /**
     * @param fileMessageId 공지에 딸린 파일 메시지 (선택). 승인 공문처럼 "요약 텍스트를 고정하되
     *                      파일도 배너에서 바로 열게" 하려고 파일명·URL을 공지에 스냅샷으로 남긴다.
     */
    @Transactional
    public ChatRoomDTO updateChatRoomNotice(Long roomId, Long messageId, String setByName, Long fileMessageId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));
        resourceScopeGuard.requireSameCompany(room.getCompany());

        if (messageId == null) {
            room.setNoticeMessageId(null);
            room.setNoticeContent(null);
            room.setNoticeByName(null);
            room.setNoticeAt(null);
            room.setNoticeFileName(null);
            room.setNoticeFileUrl(null);
            log.info("[Chat Service] 방 공지 해제: roomId={}", roomId);
        } else {
            ChatMessage message = chatMessageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("메시지를 찾을 수 없습니다: " + messageId));
            if (message.getChatRoom() == null || !message.getChatRoom().getId().equals(roomId)) {
                throw new IllegalArgumentException("이 방의 메시지가 아닙니다.");
            }

            // 파일·사진 메시지는 본문이 비어 있을 수 있어 파일명으로 대신 보여준다
            String snapshot = message.getContent();
            if (snapshot == null || snapshot.isBlank()) {
                snapshot = message.getFileName() != null ? message.getFileName() : "";
            }
            if (snapshot.length() > 1000) {
                snapshot = snapshot.substring(0, 1000);
            }

            // 파일 참조: 지정한 파일 메시지가 있으면 그 파일을, 없으면 고정한 메시지 자체의 파일을 쓴다
            ChatMessage fileSource = message;
            if (fileMessageId != null && !fileMessageId.equals(messageId)) {
                ChatMessage fileMessage = chatMessageRepository.findById(fileMessageId)
                        .orElseThrow(() -> new RuntimeException("파일 메시지를 찾을 수 없습니다: " + fileMessageId));
                if (fileMessage.getChatRoom() == null || !fileMessage.getChatRoom().getId().equals(roomId)) {
                    throw new IllegalArgumentException("이 방의 메시지가 아닙니다.");
                }
                fileSource = fileMessage;
            }

            room.setNoticeMessageId(message.getId());
            room.setNoticeContent(snapshot);
            room.setNoticeByName(setByName);
            room.setNoticeAt(LocalDateTime.now());
            room.setNoticeFileName(fileSource.getFileUrl() != null ? fileSource.getFileName() : null);
            room.setNoticeFileUrl(fileSource.getFileUrl());
            log.info("[Chat Service] 방 공지 설정: roomId={}, messageId={}, fileMessageId={}",
                    roomId, messageId, fileMessageId);
        }

        return ChatRoomDTO.fromEntity(chatRoomRepository.save(room));
    }

    /**
     * 채팅방 나가기
     */
    @Transactional
    public void leaveChatRoom(Long roomId, String userId) {
        log.info("[Chat Service] 채팅방 나가기: roomId={}, userId={}", roomId, userId);

        ChatParticipant participant = chatParticipantRepository
                .findActiveByRoomAndPerson(roomId, person(userId).memberId(), person(userId).appUserId())
                .orElseThrow(() -> new RuntimeException("참가자 정보를 찾을 수 없습니다"));

        participant.leave(ChatParticipant.LeaveReason.SELF_LEFT);
        chatParticipantRepository.save(participant);

        // 시스템 메시지 전송
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다"));
        resourceScopeGuard.requireSameCompany(room.getCompany());
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
    public void deleteChatRoom(Long roomId, String requesterIdentifier) {
        log.info("[Chat Service] 채팅방 삭제: roomId={}, requester={}", roomId, requesterIdentifier);

        if (!isAdminRequester(requesterIdentifier)) {
            throw new SecurityException("채팅방 삭제 권한이 없습니다");
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + roomId));
        resourceScopeGuard.requireSameCompany(room.getCompany());

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
                .map(participant -> ChatParticipantDTO.fromEntity(
                        participant,
                        getParticipantPosition(participant.getUserId()),
                        getParticipantMemberRole(participant.getUserId()),
                        getParticipantProfileImageUrl(participant.getUserId())
                ))
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
        resourceScopeGuard.requireSameCompany(room.getCompany());

        List<ChatParticipantDTO> addedParticipants = new ArrayList<>();
        StringBuilder joinMessage = new StringBuilder();

        Long roomCompanyId = room.getCompany() != null ? room.getCompany().getId() : null;

        // 같은 사람이 두 번 담겨 오면 (방, user_id) 유니크 제약에 걸린다
        for (String userId : new LinkedHashSet<>(userIds == null ? List.<String>of() : userIds)) {
            requireInvitable(userId, roomCompanyId);
            // 이미 참가 중인지 확인
            Optional<ChatParticipant> existing = chatParticipantRepository.findByRoomAndPerson(roomId, person(userId).memberId(), person(userId).appUserId());

            if (existing.isPresent()) {
                ChatParticipant participant = existing.get();
                if (!participant.getIsActive()) {
                    // 재참가
                    participant.setIsActive(true);
                    participant.setLeftAt(null);
                    participant.setLeaveReason(null);
                    participant.setJoinedAt(LocalDateTime.now());
                    chatParticipantRepository.save(participant);
                    addedParticipants.add(ChatParticipantDTO.fromEntity(
                            participant,
                            getParticipantPosition(participant.getUserId()),
                            getParticipantMemberRole(participant.getUserId()),
                            getParticipantProfileImageUrl(participant.getUserId())
                    ));

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
                addedParticipants.add(ChatParticipantDTO.fromEntity(
                        saved,
                        getParticipantPosition(saved.getUserId()),
                        getParticipantMemberRole(saved.getUserId()),
                        getParticipantProfileImageUrl(saved.getUserId())
                ));

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
                .findActiveByRoomAndPerson(roomId, person(userId).memberId(), person(userId).appUserId())
                .orElseThrow(() -> new RuntimeException("참가자를 찾을 수 없습니다"));

        ChatParticipant.LeaveReason reason = isKicked ?
                ChatParticipant.LeaveReason.KICKED :
                ChatParticipant.LeaveReason.SELF_LEFT;

        participant.leave(reason);
        chatParticipantRepository.save(participant);

        // 시스템 메시지
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다"));
        resourceScopeGuard.requireSameCompany(room.getCompany());

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
     * 메시지 목록 조회 (리액션 포함)
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMessages(Long roomId, int page, int size) {
        return getMessages(roomId, page, size, null);
    }

    /**
     * 메시지 목록 조회 (리액션 포함, 현재 사용자 ID로 myReaction 판단)
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMessages(Long roomId, int page, int size, String currentUserId) {
        log.info("[Chat Service] 메시지 목록 조회: roomId={}, page={}, size={}", roomId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(roomId, pageable);

        // 메시지 ID 목록 추출
        List<Long> messageIds = messages.getContent().stream()
                .map(ChatMessage::getId)
                .collect(Collectors.toList());

        // 한 번에 모든 리액션 조회
        List<ChatMessageReaction> allReactions = chatMessageReactionRepository.findByMessageIdIn(messageIds);

        // 메시지별로 리액션 그룹화
        Map<Long, List<ChatMessageReaction>> reactionsByMessage = allReactions.stream()
                .collect(Collectors.groupingBy(r -> r.getMessage().getId()));

        return messages.getContent().stream()
                .map(msg -> {
                    int readCount = (int) chatMessageReadRepository.countByMessageId(msg.getId());
                    ChatMessageDTO dto = ChatMessageDTO.fromEntityWithReadCount(msg, readCount);

                    // 리액션 요약 추가
                    List<ChatMessageReaction> msgReactions = reactionsByMessage.getOrDefault(msg.getId(), List.of());
                    dto.setReactions(buildReactionSummaries(msgReactions, currentUserId));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 특정 메시지를 가운데 두고 앞뒤로 불러오는 조회.
     *
     * 검색 결과 등 현재 화면에 없는 과거 메시지로 바로 이동할 때 쓴다 — 기존 목록 조회는
     * page/size 기반 최신순뿐이라 "이 메시지 주변"을 가져올 방법이 없었다.
     * 응답 모양(ChatMessageDTO, 최신순 정렬)은 {@link #getMessages}와 동일하게 맞춰
     * 프론트가 같은 렌더링 경로를 그대로 쓸 수 있게 한다.
     *
     * @param size 앞뒤로 각각 가져올 개수의 총합 기준값. 절반씩(size/2) 나눠 가져온다.
     */
    @Transactional(readOnly = true)
    public ChatMessagesAroundDTO getMessagesAround(Long roomId, Long messageId, int size, String currentUserId) {
        log.info("[Chat Service] 메시지 주변 조회: roomId={}, messageId={}, size={}", roomId, messageId, size);

        // 참가자만 조회 가능 — 다른 목록/전송 API와 같은 방식(findActiveByRoomAndPerson)으로 검사한다.
        chatParticipantRepository
                .findActiveByRoomAndPerson(roomId, person(currentUserId).memberId(), person(currentUserId).appUserId())
                .orElseThrow(() -> new SecurityException("채팅방 참가자가 아닙니다"));

        ChatMessage center = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다: " + messageId));
        if (center.getChatRoom() == null || !center.getChatRoom().getId().equals(roomId)) {
            // 존재는 하지만 다른 방의 메시지 id를 넣은 경우 — 조회 권한이 있는 다른 방이라도
            // 이 방의 참가자라는 사실이 그 메시지를 볼 권한이 되지는 않는다.
            throw new IllegalArgumentException("이 채팅방의 메시지가 아닙니다: " + messageId);
        }

        int half = Math.max(1, size / 2);

        Page<ChatMessage> beforePage = chatMessageRepository.findMessagesBefore(
                roomId, messageId, PageRequest.of(0, half));
        Page<ChatMessage> afterPage = chatMessageRepository.findMessagesAfter(
                roomId, messageId, PageRequest.of(0, half));

        List<ChatMessage> beforeDesc = beforePage.getContent(); // 이미 최신순(중심에 가까운 것부터)
        List<ChatMessage> afterAsc = new ArrayList<>(afterPage.getContent()); // 오름차순(중심에 가까운 것부터)
        Collections.reverse(afterAsc); // 최신순으로 뒤집는다 — 결합 시 맨 위(가장 최신)가 되도록

        List<ChatMessage> combined = new ArrayList<>(afterAsc.size() + 1 + beforeDesc.size());
        combined.addAll(afterAsc);
        combined.add(center);
        combined.addAll(beforeDesc);

        List<Long> messageIds = combined.stream().map(ChatMessage::getId).collect(Collectors.toList());
        List<ChatMessageReaction> allReactions = chatMessageReactionRepository.findByMessageIdIn(messageIds);
        Map<Long, List<ChatMessageReaction>> reactionsByMessage = allReactions.stream()
                .collect(Collectors.groupingBy(r -> r.getMessage().getId()));

        List<ChatMessageDTO> messages = combined.stream()
                .map(msg -> {
                    int readCount = (int) chatMessageReadRepository.countByMessageId(msg.getId());
                    ChatMessageDTO dto = ChatMessageDTO.fromEntityWithReadCount(msg, readCount);
                    List<ChatMessageReaction> msgReactions = reactionsByMessage.getOrDefault(msg.getId(), List.of());
                    dto.setReactions(buildReactionSummaries(msgReactions, currentUserId));
                    return dto;
                })
                .collect(Collectors.toList());

        // 기존 목록 조회의 hasMore와 같은 방식(요청한 만큼 꽉 찼으면 더 있다고 본다)의 근사치다.
        boolean hasBefore = beforeDesc.size() == half;
        boolean hasAfter = afterAsc.size() == half;

        return new ChatMessagesAroundDTO(messages, hasBefore, hasAfter);
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
        chatParticipantRepository.findActiveByRoomAndPerson(roomId, person(request.getSenderId()).memberId(), person(request.getSenderId()).appUserId())
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

        // 답글 대상 메시지 조회
        ChatMessage replyTo = null;
        if (request.getReplyToId() != null) {
            replyTo = chatMessageRepository.findById(request.getReplyToId()).orElse(null);
        }

        // 발신자 직책 조회 (서버에서 직접 조회하여 신뢰성 확보)
        String senderPosition = getParticipantPosition(request.getSenderId());

        // 메시지 저장
        ChatMessage message = ChatMessage.builder()
                .chatRoom(room)
                .senderId(request.getSenderId())
                .senderName(request.getSenderName())
                .senderPosition(senderPosition)
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
        log.info("[Chat Service] 메시지 저장 완료: id={}", saved.getId());

        // 채팅방 최신 메시지 시간 업데이트
        room.updateLastMessageAt();
        chatRoomRepository.save(room);

        // 발신자 읽음 처리
        markMessageAsRead(saved, request.getSenderId(), request.getSenderName());

        // 발신자 읽음이 보장되므로 readCount=1로 설정
        ChatMessageDTO dto = ChatMessageDTO.fromEntityWithReadCount(saved, 1);

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
    public void deleteMessage(Long roomId, Long messageId, String callerChatUserId) {
        log.info("[Chat Service] 메시지 삭제: roomId={}, messageId={}, caller={}", roomId, messageId, callerChatUserId);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("메시지를 찾을 수 없습니다: " + messageId));

        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new RuntimeException("해당 채팅방의 메시지가 아닙니다");
        }

        // 다른 기관의 방은 애초에 건드릴 수 없다 (다른 채팅 기능과 같은 규칙)
        resourceScopeGuard.requireSameCompany(message.getChatRoom().getCompany());

        // 내가 보낸 것만 지운다. 화면이 '삭제'를 내 메시지에만 보여주더라도, 서버가 막지 않으면
        // messageId만 알면 남의 말을 지울 수 있다.
        if (!isSentBy(message, callerChatUserId)) {
            log.warn("[Chat Service] 남의 메시지 삭제 시도 차단: messageId={}, caller={}", messageId, callerChatUserId);
            throw new CustomException("본인이 보낸 메시지만 삭제할 수 있습니다", HttpStatus.FORBIDDEN);
        }

        // 이미 지운 메시지를 또 지워도 결과는 같다 — 알림만 다시 보내지 않는다
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            return;
        }

        message.delete();
        chatMessageRepository.save(message);

        // 지운 사실을 방 전체에 알린다. 없으면 상대 화면은 다시 들어올 때까지 옛 내용을 그대로 보여준다.
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomId,
                ChatWebSocketMessage.deleteEvent(roomId, ChatMessageDTO.fromEntity(message)));
    }

    /**
     * 이 메시지를 보낸 사람이 호출자인가.
     *
     * 참조 칼럼(member_id/app_user_id)을 먼저 본다 — 문자열 senderId는 관리자 접두사 규약이 바뀌기
     * 전에 저장된 값이 섞여 있어 그것만 비교하면 옛 메시지가 남의 것으로 판정된다.
     */
    private boolean isSentBy(ChatMessage message, String callerChatUserId) {
        if (callerChatUserId == null || callerChatUserId.isBlank()) {
            return false;
        }

        ChatPersonRef caller = person(callerChatUserId);
        ChatPersonRef sender = ChatPersonRef.of(message.getSenderMemberId(), message.getSenderAppUserId());

        if (sender.refId() != null && caller.refId() != null) {
            return sender.refId().equals(caller.refId()) && Objects.equals(sender.type(), caller.type());
        }

        // 참조가 비어 있는 옛 행은 저장된 문자열로 비교한다
        return callerChatUserId.equals(message.getSenderId());
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
                .findActiveByRoomAndPerson(roomId, person(userId).memberId(), person(userId).appUserId())
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

    // ==================== 리액션 관리 ====================

    /**
     * 리액션 토글 (있으면 삭제, 없으면 추가)
     */
    @Transactional
    public ChatReactionDTO toggleReaction(Long roomId, Long messageId, String userId, String userName, String emoji) {
        log.info("[Chat Service] 리액션 토글: roomId={}, messageId={}, userId={}, emoji={}",
                roomId, messageId, userId, emoji);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("메시지를 찾을 수 없습니다: " + messageId));

        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new RuntimeException("해당 채팅방의 메시지가 아닙니다");
        }

        // 이미 존재하는지 확인
        Optional<ChatMessageReaction> existing = chatMessageReactionRepository
                .findByMessageAndPersonAndEmoji(messageId, person(userId).memberId(), person(userId).appUserId(), emoji);

        if (existing.isPresent()) {
            // 이미 있으면 삭제
            chatMessageReactionRepository.delete(existing.get());
            log.info("[Chat Service] 리액션 삭제됨: id={}", existing.get().getId());

            // WebSocket으로 리액션 삭제 알림
            ChatWebSocketMessage wsMessage = ChatWebSocketMessage.builder()
                    .type("REACTION_REMOVED")
                    .roomId(roomId)
                    .data(Map.of(
                            "messageId", messageId,
                            "userId", userId,
                            "emoji", emoji
                    ))
                    .build();
            messagingTemplate.convertAndSend("/topic/chat/" + roomId, wsMessage);

            return null; // 삭제됨
        } else {
            // 없으면 추가
            ChatMessageReaction reaction = ChatMessageReaction.builder()
                    .message(message)
                    .userId(userId)
                    .userName(userName)
                    .emoji(emoji)
                    .build();

            ChatMessageReaction saved = chatMessageReactionRepository.save(reaction);
            log.info("[Chat Service] 리액션 추가됨: id={}", saved.getId());

            ChatReactionDTO dto = ChatReactionDTO.fromEntity(saved);

            // WebSocket으로 리액션 추가 알림
            ChatWebSocketMessage wsMessage = ChatWebSocketMessage.builder()
                    .type("REACTION_ADDED")
                    .roomId(roomId)
                    .data(Map.of(
                            "messageId", messageId,
                            "reaction", dto
                    ))
                    .build();
            messagingTemplate.convertAndSend("/topic/chat/" + roomId, wsMessage);

            return dto;
        }
    }

    /**
     * 메시지 리액션 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatReactionDTO.ReactionSummary> getReactions(Long messageId, String currentUserId) {
        log.info("[Chat Service] 리액션 조회: messageId={}", messageId);

        List<ChatMessageReaction> reactions = chatMessageReactionRepository.findByMessageId(messageId);
        return buildReactionSummaries(reactions, currentUserId);
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

    /**
     * 방 안 메시지 검색 — 지워진 메시지는 제외하고 최신순으로 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> searchMessages(Long roomId, String keyword) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));
        resourceScopeGuard.requireSameCompany(room.getCompany());

        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        return chatMessageRepository.searchMessages(roomId, keyword.trim()).stream()
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

        List<ChatParticipant> participations = chatParticipantRepository.findActiveByPerson(person(userId).memberId(), person(userId).appUserId());

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
        if (!chatMessageReadRepository.existsByMessageAndPerson(message.getId(), person(userId).memberId(), person(userId).appUserId())) {
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
                .findActiveByRoomAndPerson(roomId, person(userId).memberId(), person(userId).appUserId())
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
        return findChatUser(userId)
                .map(ChatUserProfile::name)
                .orElse("사용자");
    }

    private String getParticipantPosition(String userId) {
        return findChatUser(userId)
                .map(ChatUserProfile::position)
                .orElse(null);
    }

    private String getParticipantProfileImageUrl(String userId) {
        return findChatUser(userId)
                .map(ChatUserProfile::profileImageUrl)
                .orElse(null);
    }

    private String getParticipantMemberRole(String userId) {
        return findChatUser(userId)
                .map(ChatUserProfile::memberRole)
                .orElse(null);
    }

    /**
     * 채팅 참가자 하나의 표시 정보. 출처가 직원(Member)이든 관리자(AppUser)든 이 형태로 좁혀
     * 조회 쪽에서는 어느 테이블 사람인지 신경 쓰지 않게 한다.
     */
    private record ChatUserProfile(String name, String position, String profileImageUrl,
                                   String memberRole, String fcmToken, Long companyId) {
    }

    /**
     * 채팅 사용자 식별자로 사람을 찾는다.
     *
     * 관리자는 {@link #ADMIN_ID_PREFIX} 접두사가 붙는다 — 접두사 없이 원시 숫자만 쓰면
     * 관리자 3번과 직원 3번을 구별할 수 없다(자세한 이유는 상수 주석 참고).
     * 숫자만 있는 값은 직원으로 보고, 그래도 못 찾으면 예전 방식대로 아이디·이메일로 찾아본다.
     */
    private Optional<ChatUserProfile> findChatUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }

        if (isAdminChatUserId(userId)) {
            return parseId(userId.substring(ADMIN_ID_PREFIX.length()))
                    .flatMap(userRepository::findById)
                    .map(ChatService::adminProfile);
        }

        Optional<Member> member = parseId(userId).flatMap(memberRepository::findById);
        if (member.isPresent()) {
            return member.map(ChatService::memberProfile);
        }

        Optional<Member> byUsername = memberRepository.findByUsername(userId);
        if (byUsername.isPresent()) {
            return byUsername.map(ChatService::memberProfile);
        }

        return memberRepository.findByEmail(userId).map(ChatService::memberProfile);
    }

    private static ChatUserProfile memberProfile(Member member) {
        return new ChatUserProfile(
                member.getName(),
                member.getPosition(),
                member.getProfileImageUrl(),
                member.getRole() != null ? member.getRole().name() : null,
                member.getFcmToken(),
                member.getCompany() != null ? member.getCompany().getId() : null);
    }

    /** 관리자도 직원과 같은 규격의 프로필 사진·직책을 갖는다 (없으면 직책은 '관리자'로 보인다) */
    private static ChatUserProfile adminProfile(AppUser appUser) {
        return new ChatUserProfile(
                appUser.getUsername(),
                AdminDisplay.position(appUser),
                appUser.getProfileImageUrl(),
                Member.Role.ADMIN.name(),
                appUser.getFcmToken(),
                appUser.getCompany() != null ? appUser.getCompany().getId() : null);
    }

    /**
     * 이 사람을 그 기관 방에 넣어도 되는지.
     *
     * 참가자 식별자는 클라이언트가 보내는 값이다. 화면이 같은 기관 사람만 보여준다는 것은
     * 보장이 아니므로, 넣을 수 있는지는 서버가 정한다. 소속을 확인할 수 없는 계정
     * (없는 사람, 어느 기관에도 안 붙은 계정)은 넣지 않는다 — 애초에 후보 목록에도 뜨지 않는다.
     */
    private void requireInvitable(String userId, Long companyId) {
        Long ownerCompanyId = findChatUser(userId)
                .map(ChatUserProfile::companyId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방에 넣을 수 없는 사용자입니다: " + userId));

        if (ownerCompanyId == null || !ownerCompanyId.equals(companyId)) {
            throw new IllegalArgumentException("다른 기관 사람은 채팅방에 넣을 수 없습니다: " + userId);
        }
    }

    private static Optional<Long> parseId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private boolean isAdminRequester(String requesterIdentifier) {
        if (requesterIdentifier == null || requesterIdentifier.isBlank()) {
            return false;
        }

        Optional<AppUser> appUser = userRepository.findByEmail(requesterIdentifier);
        if (appUser.isEmpty()) {
            appUser = Optional.ofNullable(userRepository.findByUsername(requesterIdentifier));
        }

        if (appUser.isPresent()) {
            return appUser.get().getUserRole() == UserRole.ROLE_ADMIN;
        }

        return memberRepository.findByUsername(requesterIdentifier)
                .map(member -> member.getRole() == Member.Role.ADMIN)
                .orElseGet(() -> memberRepository.findByEmail(requesterIdentifier)
                        .map(member -> member.getRole() == Member.Role.ADMIN)
                        .orElse(false));
    }

    private List<ChatReactionDTO.ReactionSummary> buildReactionSummaries(
            List<ChatMessageReaction> reactions, String currentUserId) {
        // 이모지별로 그룹화
        Map<String, List<ChatMessageReaction>> byEmoji = reactions.stream()
                .collect(Collectors.groupingBy(ChatMessageReaction::getEmoji));

        return byEmoji.entrySet().stream()
                .map(entry -> {
                    String emoji = entry.getKey();
                    List<ChatMessageReaction> emojiReactions = entry.getValue();
                    List<String> userNames = emojiReactions.stream()
                            .map(ChatMessageReaction::getUserName)
                            .collect(Collectors.toList());
                    boolean myReaction = currentUserId != null &&
                            emojiReactions.stream().anyMatch(r -> r.getUserId().equals(currentUserId));

                    return ChatReactionDTO.ReactionSummary.builder()
                            .emoji(emoji)
                            .count(emojiReactions.size())
                            .userNames(userNames)
                            .myReaction(myReaction)
                            .build();
                })
                .collect(Collectors.toList());
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
                    String fcmToken = resolveParticipantFcmToken(participant.getUserId(), participant.getUserName());

                    if (fcmToken != null) {
                        // @이름으로 호출된 사람은 일반 메시지와 구분해서 알린다 (많은 대화 속에서 놓치지 않게)
                        boolean mentioned = isMentioned(message.getContent(), participant.getUserName());

                        // 메신저 관례대로 제목=보낸 사람, 본문=내용 (방 이름·이름 접두사 중복 제거).
                        // 이름만 적으면 동명이인일 때 누구인지 알 수 없어 직책을 함께 보여준다.
                        String sender = PersonDisplay.withPosition(
                                message.getSenderName(), message.getSenderPosition());

                        FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                                .recipientToken(fcmToken)
                                .title(mentioned
                                        ? sender + " — 나를 호출했어요"
                                        : sender)
                                .message(message.getDisplayContent())
                                .recipientUserId(participant.getUserId())
                                .recipientUserName(participant.getUserName())
                                .type("CHAT")
                                .relatedEntityId(room.getId())
                                .relatedEntityType("chatRoom")
                                .data(Map.of(
                                        "type", "chat",
                                        "roomId", String.valueOf(room.getId()),
                                        "messageId", String.valueOf(message.getId()),
                                        "mention", String.valueOf(mentioned)
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

    /**
     * 본문에 '@이름' 형태로 이 사람이 호출됐는지.
     *
     * 이름 뒤에 다른 글자가 이어지면 다른 사람(예: @김영 vs @김영희)이므로,
     * 이름 다음 글자가 한글·영문·숫자면 호출로 보지 않는다.
     */
    private boolean isMentioned(String content, String userName) {
        if (content == null || userName == null || userName.isBlank()) {
            return false;
        }
        String token = "@" + userName;
        int from = 0;
        while (true) {
            int idx = content.indexOf(token, from);
            if (idx < 0) return false;
            int after = idx + token.length();
            if (after >= content.length() || !Character.isLetterOrDigit(content.charAt(after))) {
                return true;
            }
            from = after;
        }
    }

    /**
     * 참가자의 FCM 토큰 결정.
     *
     * 식별자에 접두사가 있으면 어느 계정인지 확정되므로 그대로 따른다. 접두사 도입 이전에
     * 저장된 관리자 행이 남아 있을 수 있어(마이그레이션이 이름으로 판별할 수 없었던 경우),
     * 숫자만 있는 식별자는 참가 시점 스냅샷 이름과 맞는 계정을 우선 고르는 기존 판별을
     * 폴백으로 남겨둔다.
     */
    private String resolveParticipantFcmToken(String userId, String snapshotName) {
        if (isAdminChatUserId(userId)) {
            return findChatUser(userId).map(ChatUserProfile::fcmToken).orElse(null);
        }

        Long numericId = parseId(userId).orElse(null);
        if (numericId == null) {
            return findChatUser(userId).map(ChatUserProfile::fcmToken).orElse(null);
        }

        Member member = memberRepository.findById(numericId).orElse(null);
        AppUser appUser = userRepository.findById(numericId).orElse(null);

        boolean memberMatches = member != null && Objects.equals(member.getName(), snapshotName);
        boolean appUserMatches = appUser != null && Objects.equals(appUser.getUsername(), snapshotName);

        if (memberMatches) {
            return member.getFcmToken();
        }
        if (appUserMatches) {
            return appUser.getFcmToken();
        }
        if (member != null) {
            return member.getFcmToken();
        }
        if (appUser != null) {
            return appUser.getFcmToken();
        }
        return null;
    }
}
