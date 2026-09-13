package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * "날짜로 이동"(카톡처럼 채팅 검색에서 날짜를 지정해 그 날짜 대화 처음으로 이동, 버그제보 2026-09-10
 * 배정민) 서비스 로직 테스트. 참가자 검사는 "주변 조회"(around)와 같은 방식이라 그 테스트와
 * 같은 목 구성을 따른다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 날짜로 이동(first-on-date) 테스트")
class ChatServiceGetFirstMessageOnDateTest {

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
                new com.silverithm.vehicleplacementsystem.config.ThreadConfig().chatNotificationExecutor(),
                new ChatReadRecorder(chatMessageReadRepository, mock(jakarta.persistence.EntityManager.class)));

        lenient().when(chatParticipantRepository.findActiveByRoomAndPerson(eq(ROOM_ID), eq(9L), isNull()))
                .thenReturn(Optional.of(mock(ChatParticipant.class)));
    }

    private ChatMessage messageOf(Long id, LocalDateTime createdAt) {
        ChatRoom room = ChatRoom.builder().id(ROOM_ID).build();
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

    @Test
    @DisplayName("그 날짜 00시 이후 첫 메시지를 반환한다")
    void returnsFirstMessageOnOrAfterDate() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        LocalDateTime startOfDay = date.atStartOfDay();
        ChatMessage first = messageOf(42L, startOfDay.plusHours(3));
        when(chatMessageRepository.findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                eq(ROOM_ID), eq(startOfDay))).thenReturn(Optional.of(first));

        var result = chatService.getFirstMessageOnDate(ROOM_ID, date, CALLER_ID);

        assertEquals(42L, result.getMessageId());
        assertEquals(startOfDay.plusHours(3), result.getCreatedAt());
    }

    @Test
    @DisplayName("그 날짜 이후 메시지가 없으면 예외가 발생한다(컨트롤러가 404로 매핑)")
    void throwsWhenNoMessageAfterDate() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(chatMessageRepository.findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                eq(ROOM_ID), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> chatService.getFirstMessageOnDate(ROOM_ID, date, CALLER_ID));
    }

    @Test
    @DisplayName("그 방의 참가자가 아니면 조회할 수 없다(컨트롤러가 403으로 매핑)")
    void nonParticipantIsRejected() {
        String strangerId = "77";
        when(chatParticipantRepository.findActiveByRoomAndPerson(ROOM_ID, 77L, null))
                .thenReturn(Optional.empty());

        assertThrows(SecurityException.class,
                () -> chatService.getFirstMessageOnDate(ROOM_ID, LocalDate.of(2026, 9, 10), strangerId));
    }
}
