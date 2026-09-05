package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /** 목록 방 아이콘에 겹쳐 그릴 얼굴 수 — 카카오톡과 같이 넷까지 */
    private static final int AVATAR_PREVIEW_LIMIT = 4;

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
    private final ChatNotificationExecutor chatNotificationExecutor;


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
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).collect(Collectors.toList());

        // 방마다 따로 묻지 않는다 — 마지막 메시지, 내 참가 정보, 안 읽은 수를 각각 한 번에 가져온다.
        // (방마다 물으면 방 개수에 비례해 쿼리가 늘어난다)
        Map<Long, ChatMessage> lastMessageByRoom = chatMessageRepository.findLastMessagesOfRooms(roomIds).stream()
                .collect(Collectors.toMap(m -> m.getChatRoom().getId(), m -> m, (a, b) -> a));

        List<ChatParticipant> myParticipations = chatParticipantRepository.findActiveByRoomsAndPerson(
                roomIds, person(userId).memberId(), person(userId).appUserId());

        Map<Long, Long> unreadByRoom = myParticipations.isEmpty()
                ? Map.of()
                : chatMessageRepository.countUnreadByParticipants(
                        myParticipations.stream().map(ChatParticipant::getId).collect(Collectors.toList()), userId)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1], (a, b) -> a));

        // 마지막 메시지의 '읽은 사람 수'도 한 번에 센다
        Map<Long, Long> readCountByMessage = lastMessageByRoom.isEmpty()
                ? Map.of()
                : chatMessageReadRepository.countByMessageIdIn(
                        lastMessageByRoom.values().stream().map(ChatMessage::getId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1], (a, b) -> a));

        Map<Long, List<ChatRoomAvatarDTO>> avatarsByRoom = roomAvatars(roomIds, userId);

        return rooms.stream()
                .map(room -> {
                    ChatRoomDTO dto = ChatRoomDTO.fromEntity(room);
                    dto.setAvatars(avatarsByRoom.getOrDefault(room.getId(), List.of()));

                    ChatMessage lastMsg = lastMessageByRoom.get(room.getId());
                    if (lastMsg != null) {
                        dto.setLastMessage(ChatMessageDTO.fromEntityWithReadCount(
                                lastMsg, readCountByMessage.getOrDefault(lastMsg.getId(), 0L).intValue()));
                    }

                    dto.setUnreadCount(unreadByRoom.getOrDefault(room.getId(), 0L).intValue());

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

        // 읽은 사람 수도 한 번에 (메시지마다 세면 조회 건수만큼 쿼리가 더 나간다)
        Map<Long, Long> readCountByMessage = readCounts(messageIds);

        return messages.getContent().stream()
                .map(msg -> {
                    int readCount = readCountByMessage.getOrDefault(msg.getId(), 0L).intValue();
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

        Map<Long, Long> readCountByMessage = readCounts(messageIds);

        List<ChatMessageDTO> messages = combined.stream()
                .map(msg -> {
                    int readCount = readCountByMessage.getOrDefault(msg.getId(), 0L).intValue();
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

        // FCM 푸시 알림 전송 (다른 참가자들에게).
        // 보낸 사람을 기다리게 하지 않는다 — 자세한 이유는 dispatchMessageNotification 주석 참고.
        // 사진을 여러 장 한 번에 보낸 경우에는 마지막 장까지 올라온 뒤 한 번만 나간다.
        dispatchMessageNotification(room.getId(), saved.getId(),
                request.getBatchId(), request.getBatchSize());

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
     * 메시지 수정.
     *
     * 업무 기록 성격의 채팅이라 시간 제한 없이 허용한다 — 대신 editedAt과 "수정됨" 표시가
     * 감사 추적을 대신한다. 화면에서 편집 버튼을 내 메시지에만 보여주더라도, 서버가 막지
     * 않으면 messageId만 알면 남의 말을 바꿀 수 있으므로 삭제와 같은 방식으로 서버에서 막는다.
     */
    @Transactional
    public ChatMessageDTO editMessage(Long roomId, Long messageId, String newContent, String callerChatUserId) {
        log.info("[Chat Service] 메시지 수정: roomId={}, messageId={}, caller={}", roomId, messageId, callerChatUserId);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("메시지를 찾을 수 없습니다: " + messageId));

        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new RuntimeException("해당 채팅방의 메시지가 아닙니다");
        }

        // 다른 기관의 방은 애초에 건드릴 수 없다 (다른 채팅 기능과 같은 규칙)
        resourceScopeGuard.requireSameCompany(message.getChatRoom().getCompany());

        // 내가 보낸 것만 고친다.
        if (!isSentBy(message, callerChatUserId)) {
            log.warn("[Chat Service] 남의 메시지 수정 시도 차단: messageId={}, caller={}", messageId, callerChatUserId);
            throw new CustomException("본인이 보낸 메시지만 수정할 수 있습니다", HttpStatus.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new CustomException("삭제된 메시지는 수정할 수 없습니다", HttpStatus.BAD_REQUEST);
        }

        if (message.getType() != ChatMessage.MessageType.TEXT) {
            throw new CustomException("텍스트 메시지만 수정할 수 있습니다", HttpStatus.BAD_REQUEST);
        }

        if (newContent == null || newContent.isBlank()) {
            throw new CustomException("내용을 입력해주세요", HttpStatus.BAD_REQUEST);
        }

        // 내용이 그대로면 아무것도 바꾸지 않는다 — editedAt을 찍지 않고 지금 상태를 그대로 반환한다
        if (newContent.equals(message.getContent())) {
            return ChatMessageDTO.fromEntity(message);
        }

        message.edit(newContent);
        chatMessageRepository.save(message);

        // 이 메시지가 방 공지로 걸려 있으면 배너의 사본도 함께 고친다.
        // 공지 내용은 방에 스냅샷으로 복사돼 있어서(updateChatRoomNotice), 여기서 안 고치면
        // 본문만 바뀌고 배너에는 옛 문구가 영원히 남는다.
        ChatRoom room = message.getChatRoom();
        if (message.getId().equals(room.getNoticeMessageId())) {
            String snapshot = newContent.length() > 1000 ? newContent.substring(0, 1000) : newContent;
            room.setNoticeContent(snapshot);
            chatRoomRepository.save(room);
        }

        // 고친 사실을 방 전체에 알린다. 없으면 상대 화면은 다시 들어올 때까지 옛 내용을 그대로 보여준다.
        ChatMessageDTO dto = ChatMessageDTO.fromEntity(message);
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomId,
                ChatWebSocketMessage.editEvent(roomId, dto));

        return dto;
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

        // 안읽은 메시지들에 대해 읽음 기록 추가.
        // 메시지마다 '조회 + 중복 확인 + 저장'으로 세 번씩 나가던 것을, 대상 조회 한 번 + 저장으로 줄인다.
        // (findUnreadMessageIds가 이미 NOT EXISTS로 걸러 오므로 건별 중복 확인이 필요 없다)
        List<Long> unreadMessageIds = chatMessageReadRepository.findUnreadMessageIds(roomId, lastMessageId, userId);
        if (!unreadMessageIds.isEmpty()) {
            List<ChatMessageRead> reads = chatMessageRepository.findAllById(unreadMessageIds).stream()
                    .map(message -> ChatMessageRead.builder()
                            .message(message)
                            .userId(userId)
                            .userName(userName)
                            .build())
                    .collect(Collectors.toList());
            chatMessageReadRepository.saveAll(reads);
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

        // 사진은 읽음 기록이 아니라 사람 쪽에 있다 — 참가자 목록과 같은 경로로 찾아 넣는다.
        return readers.stream()
                .map(read -> ChatMessageReaderDTO.fromEntity(
                        read, getParticipantProfileImageUrl(read.getUserId())))
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

    /** 메시지 id별 읽은 사람 수. 결과에 없는 메시지는 아무도 읽지 않은 것(0)이다. */
    private Map<Long, Long> readCounts(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return chatMessageReadRepository.countByMessageIdIn(messageIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1], (a, b) -> a));
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

    /**
     * 방마다 목록에 겹쳐 그릴 참여자(최대 4명)를 한 번에 만든다.
     *
     * 방 개수나 사람 수와 무관하게 조회는 **한 번**이다 — 참가자와 사진을 한 쿼리로 잇는다.
     * 사람마다 사진을 따로 물으면(findChatUser) 사람 수만큼 쿼리가 나가서 목록이 느려진다.
     *
     * 나는 뺀다. 내 얼굴은 이미 알고 있고, 카카오톡도 상대 얼굴만 보여준다.
     * 다만 나 혼자 있는 방은 뺄 사람이 없으므로 나라도 보여준다.
     */
    private Map<Long, List<ChatRoomAvatarDTO>> roomAvatars(List<Long> roomIds, String meUserId) {
        List<Object[]> rows = chatParticipantRepository.findAvatarRowsByRoomIds(roomIds);
        if (rows.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ChatRoomAvatarDTO>> byRoom = new LinkedHashMap<>();
        Map<Long, List<ChatRoomAvatarDTO>> mineOnly = new LinkedHashMap<>();

        for (Object[] row : rows) {
            Long roomId = (Long) row[0];
            String userId = (String) row[1];
            if (roomId == null) {
                continue;
            }
            ChatRoomAvatarDTO avatar = ChatRoomAvatarDTO.builder()
                    .userId(userId)
                    .userName((String) row[2])
                    .profileImageUrl((String) row[3])
                    .build();

            if (Objects.equals(userId, meUserId)) {
                mineOnly.computeIfAbsent(roomId, k -> new ArrayList<>()).add(avatar);
                continue;
            }
            List<ChatRoomAvatarDTO> list = byRoom.computeIfAbsent(roomId, k -> new ArrayList<>());
            if (list.size() < AVATAR_PREVIEW_LIMIT) {
                list.add(avatar);
            }
        }

        // 나 혼자인 방은 비어 버린다 — 그럴 때만 내 얼굴을 넣는다
        mineOnly.forEach((roomId, mine) -> byRoom.computeIfAbsent(roomId, k -> mine));

        return byRoom;
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

    /**
     * 푸시 알림을 보낸 사람의 응답 경로에서 떼어낸다.
     *
     * 알림 한 건은 구글 FCM으로 나가는 HTTPS 호출이라 100~700ms가 걸리고, 참가자 수만큼 순서대로
     * 나간다. 운영 로그 실측으로 27명 방은 7.1초, 26명 방은 6.0초였다 — 그동안 메시지 저장 자체는
     * 3ms에 끝나 있었다. 즉 "채팅이 느리다"의 대부분은 DB가 아니라 이 대기였다.
     *
     * 커밋된 뒤에 보낸다 — 트랜잭션이 되돌아갔는데 알림만 나가는 일이 없도록.
     * 실패해도 메시지는 이미 저장·전달됐으므로 로그만 남기고 넘어간다(원래 동작과 같다).
     */
    private void dispatchMessageNotification(Long roomId, Long messageId, String batchId, Integer batchSize) {
        Runnable task = () -> {
            try {
                if (ChatMessageBatchCollector.isBatched(batchId, batchSize)) {
                    batchCollector.collect(roomId, messageId, batchId, batchSize);
                    return;
                }
                ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
                ChatMessage message = chatMessageRepository.findById(messageId).orElse(null);
                if (room != null && message != null) {
                    sendMessageNotification(room, message, 1);
                }
            } catch (Exception e) {
                log.error("[Chat Service] 알림 전송 작업 실패: roomId={}, messageId={}, error={}",
                        roomId, messageId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    chatNotificationExecutor.execute(task);
                }
            });
        } else {
            chatNotificationExecutor.execute(task);
        }
    }

    /**
     * 한 번에 보낸 묶음의 알림을 모아 한 건으로 보낸다.
     *
     * 30초는 마지막 장을 못 기다릴 때의 안전망이다 — 자세한 이유는 ChatMessageBatchCollector 참고.
     */
    private final ChatMessageBatchCollector batchCollector =
            new ChatMessageBatchCollector(this::sendBatchedNotification, 30_000L);

    @PreDestroy
    void shutdownBatchCollector() {
        batchCollector.shutdown();
    }

    /** 묶음이 다 모였다 — FCM 호출은 타이머 스레드가 아니라 알림 실행기에서 한다 */
    private void sendBatchedNotification(Long roomId, Long lastMessageId, int count) {
        chatNotificationExecutor.execute(() -> {
            try {
                ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
                ChatMessage message = chatMessageRepository.findById(lastMessageId).orElse(null);
                if (room != null && message != null) {
                    sendMessageNotification(room, message, count);
                }
            } catch (Exception e) {
                log.error("[Chat Service] 묶음 알림 전송 실패: roomId={}, error={}", roomId, e.getMessage());
            }
        });
    }

    /** 묶음 알림의 본문 — "사진 10장"처럼 몇 개인지 한 줄로 알린다 */
    private String batchBody(ChatMessage message, int batchCount) {
        if (batchCount <= 1) {
            return message.getDisplayContent();
        }
        if (message.getType() == ChatMessage.MessageType.IMAGE) {
            return "사진 " + batchCount + "장";
        }
        if (message.getType() == ChatMessage.MessageType.FILE) {
            return "파일 " + batchCount + "개";
        }
        return message.getDisplayContent() + " 외 " + (batchCount - 1) + "건";
    }

    /**
     * @param batchCount 한 번에 보낸 묶음의 장수. 1이면 평소대로 그 메시지 내용을 본문으로 쓴다.
     */
    private void sendMessageNotification(ChatRoom room, ChatMessage message, int batchCount) {
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
                                .message(batchBody(message, batchCount))
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
