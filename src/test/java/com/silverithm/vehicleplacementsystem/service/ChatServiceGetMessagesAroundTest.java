package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageReactionRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageReadRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatRoomRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 메시지 "주변 조회"(around) 테스트.
 *
 * 검색 결과 등에서 현재 화면에 없는 과거 메시지로 바로 이동할 때 쓰는 기능이다.
 * 가운데 메시지를 두고 앞뒤로 나눠 가져오는 경계 처리와, 참가자만 접근 가능한지가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 메시지 주변 조회(around) 테스트")
class ChatServiceGetMessagesAroundTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatParticipantRepository chatParticipantRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMessageReadRepository chatMessageReadRepository;
    @Mock
    private ChatMessageReactionRepository chatMessageReactionRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ResourceScopeGuard resourceScopeGuard;

    private ChatService chatService;

    private static final Long ROOM_ID = 100L;
    private static final String CALLER_ID = "9";

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository, messagingTemplate, notificationService, resourceScopeGuard,
                new com.silverithm.vehicleplacementsystem.config.ThreadConfig().chatNotificationExecutor());

        // 참가자 검증은 대부분의 테스트에서 통과해야 한다 — 권한 테스트에서만 다르게 스텁한다.
        lenient().when(chatParticipantRepository.findActiveByRoomAndPerson(eq(ROOM_ID), eq(9L), isNull()))
                .thenReturn(Optional.of(mock(ChatParticipant.class)));
    }

    private ChatMessage messageOf(Long id, ChatRoom room, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .id(id)
                .chatRoom(room)
                .senderId("1")
                .senderName("발신자")
                .type(ChatMessage.MessageType.TEXT)
                .content("메시지 " + id)
                .createdAt(createdAt)
                .isDeleted(false)
                .build();
    }

    /** id=1..count인 메시지 목록을 만든다(오래된 순). */
    private List<ChatMessage> buildTimeline(ChatRoom room, int count) {
        List<ChatMessage> list = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 0, 0);
        for (long id = 1; id <= count; id++) {
            list.add(messageOf(id, room, base.plusMinutes(id)));
        }
        return list;
    }

    /** findMessagesBefore/After가 실제 리포지토리처럼 timeline에서 잘라 페이지로 돌려주도록 스텁한다. */
    private void stubAroundQueries(List<ChatMessage> timeline, Long centerId, int half) {
        // before: id < centerId, createdAt DESC(=id 내림차순), 최대 half건
        List<ChatMessage> before = new ArrayList<>();
        for (int i = (int) (centerId - 2); i >= 0 && before.size() < half; i--) {
            before.add(timeline.get(i));
        }
        when(chatMessageRepository.findMessagesBefore(eq(ROOM_ID), eq(centerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(before, PageRequest.of(0, half), timeline.size()));

        // after: id > centerId, createdAt ASC(=id 오름차순), 최대 half건
        List<ChatMessage> after = new ArrayList<>();
        for (int i = centerId.intValue(); i < timeline.size() && after.size() < half; i++) {
            after.add(timeline.get(i));
        }
        when(chatMessageRepository.findMessagesAfter(eq(ROOM_ID), eq(centerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(after, PageRequest.of(0, half), timeline.size()));
    }

    @Test
    @DisplayName("가운데 메시지가 결과에 포함되고, 앞뒤 개수가 요청한 만큼 맞는다")
    void includesCenterAndCorrectCounts() {
        ChatRoom room = ChatRoom.builder().id(ROOM_ID).build();
        List<ChatMessage> timeline = buildTimeline(room, 20); // id 1~20
        Long centerId = 10L;
        when(chatMessageRepository.findById(centerId)).thenReturn(Optional.of(timeline.get(9)));
        stubAroundQueries(timeline, centerId, 5); // size=10 -> half=5

        var result = chatService.getMessagesAround(ROOM_ID, centerId, 10, CALLER_ID);

        assertEquals(11, result.getMessages().size()); // before 5 + center 1 + after 5
        assertTrue(result.getMessages().stream().anyMatch(m -> m.getId().equals(centerId)));
        assertTrue(result.isHasBefore());
        assertTrue(result.isHasAfter());

        // 최신순 정렬 확인: 맨 앞이 가장 최근(centerId+5), 맨 뒤가 가장 과거(centerId-5)
        assertEquals(15L, result.getMessages().get(0).getId());
        assertEquals(5L, result.getMessages().get(result.getMessages().size() - 1).getId());
    }

    @Test
    @DisplayName("맨 처음 메시지를 중심으로 요청하면 이전 메시지가 없다(hasBefore=false)")
    void firstMessageHasNoBefore() {
        ChatRoom room = ChatRoom.builder().id(ROOM_ID).build();
        List<ChatMessage> timeline = buildTimeline(room, 20);
        Long centerId = 1L;
        when(chatMessageRepository.findById(centerId)).thenReturn(Optional.of(timeline.get(0)));
        stubAroundQueries(timeline, centerId, 5);

        var result = chatService.getMessagesAround(ROOM_ID, centerId, 10, CALLER_ID);

        assertFalse(result.isHasBefore());
        assertTrue(result.isHasAfter());
        assertEquals(6, result.getMessages().size()); // center + 5 after만
        assertTrue(result.getMessages().stream().anyMatch(m -> m.getId().equals(centerId)));
    }

    @Test
    @DisplayName("맨 끝(최신) 메시지를 중심으로 요청하면 이후 메시지가 없다(hasAfter=false)")
    void lastMessageHasNoAfter() {
        ChatRoom room = ChatRoom.builder().id(ROOM_ID).build();
        List<ChatMessage> timeline = buildTimeline(room, 20);
        Long centerId = 20L;
        when(chatMessageRepository.findById(centerId)).thenReturn(Optional.of(timeline.get(19)));
        stubAroundQueries(timeline, centerId, 5);

        var result = chatService.getMessagesAround(ROOM_ID, centerId, 10, CALLER_ID);

        assertFalse(result.isHasAfter());
        assertTrue(result.isHasBefore());
        assertEquals(6, result.getMessages().size()); // before 5 + center만
        assertTrue(result.getMessages().stream().anyMatch(m -> m.getId().equals(centerId)));
    }

    @Test
    @DisplayName("존재하지 않는 messageId면 예외가 발생한다")
    void missingMessageIdThrows() {
        when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> chatService.getMessagesAround(ROOM_ID, 999L, 10, CALLER_ID));
    }

    @Test
    @DisplayName("다른 방의 messageId면 접근을 막는다")
    void messageFromDifferentRoomIsRejected() {
        ChatRoom otherRoom = ChatRoom.builder().id(999L).build();
        ChatMessage messageInOtherRoom = messageOf(5L, otherRoom, LocalDateTime.now());
        when(chatMessageRepository.findById(5L)).thenReturn(Optional.of(messageInOtherRoom));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.getMessagesAround(ROOM_ID, 5L, 10, CALLER_ID));
    }

    @Test
    @DisplayName("그 방의 참가자가 아니면 조회할 수 없다")
    void nonParticipantIsRejected() {
        String strangerId = "77";
        when(chatParticipantRepository.findActiveByRoomAndPerson(ROOM_ID, 77L, null))
                .thenReturn(Optional.empty());

        assertThrows(SecurityException.class,
                () -> chatService.getMessagesAround(ROOM_ID, 5L, 10, strangerId));
    }
}
