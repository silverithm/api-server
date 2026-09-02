package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageDTO;
import com.silverithm.vehicleplacementsystem.dto.ChatWebSocketMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 메시지 수정(editMessage)의 서버 측 강제 규칙을 못박는다.
 *
 * 화면에서 편집 버튼을 내 메시지에만 보여주더라도, 서버가 막지 않으면 messageId만 알면
 * 남의 말을 바꿀 수 있다 — deleteMessage와 같은 위협 모델이라 같은 방식(isSentBy)으로 검증한다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:chateditmsg;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatServiceEditMessageTest {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private ChatService chatService;
    private SimpMessagingTemplate messagingTemplate;
    private ChatRoom room;

    private static final String SENDER = "3"; // member id 3
    private static final String OTHER = "99";

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                messagingTemplate, mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor());

        Company company = companyRepository.save(Company.of("테스트기관", "서울", null));
        room = chatRoomRepository.save(ChatRoom.builder()
                .name("방").company(company).createdBy(SENDER).createdByName("나")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
    }

    private ChatMessage saveMessage(String senderId, ChatMessage.MessageType type, String content, boolean deleted) {
        return chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(room).senderId(senderId).senderName("사람")
                .type(type).content(content).isDeleted(deleted).build());
    }

    @Test
    @DisplayName("남의 메시지는 수정할 수 없다 — FORBIDDEN")
    void cannotEditOthersMessage() {
        ChatMessage message = saveMessage(SENDER, ChatMessage.MessageType.TEXT, "원본", false);

        assertThatThrownBy(() -> chatService.editMessage(room.getId(), message.getId(), "바꾼 내용", OTHER))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("삭제된 메시지는 수정할 수 없다 — BAD_REQUEST")
    void cannotEditDeletedMessage() {
        ChatMessage message = saveMessage(SENDER, ChatMessage.MessageType.TEXT, "원본", true);

        assertThatThrownBy(() -> chatService.editMessage(room.getId(), message.getId(), "바꾼 내용", SENDER))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("텍스트가 아닌 메시지(이미지)는 수정할 수 없다 — BAD_REQUEST")
    void cannotEditNonTextMessage() {
        ChatMessage message = saveMessage(SENDER, ChatMessage.MessageType.IMAGE, null, false);
        message.setFileUrl("https://example.com/a.png");
        chatMessageRepository.save(message);

        assertThatThrownBy(() -> chatService.editMessage(room.getId(), message.getId(), "바꾼 내용", SENDER))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("빈 내용은 거절된다 — BAD_REQUEST")
    void blankContentRejected() {
        ChatMessage message = saveMessage(SENDER, ChatMessage.MessageType.TEXT, "원본", false);

        assertThatThrownBy(() -> chatService.editMessage(room.getId(), message.getId(), "   ", SENDER))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("내 텍스트 메시지는 수정에 성공하고, editedAt이 찍히고, EDIT 이벤트가 브로드캐스트된다")
    void editOwnTextMessageSucceeds() {
        ChatMessage message = saveMessage(SENDER, ChatMessage.MessageType.TEXT, "원본", false);

        ChatMessageDTO result = chatService.editMessage(room.getId(), message.getId(), "바꾼 내용", SENDER);

        assertThat(result.getContent()).isEqualTo("바꾼 내용");
        assertThat(result.getEditedAt()).isNotNull();

        ChatMessage reloaded = chatMessageRepository.findById(message.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("바꾼 내용");
        assertThat(reloaded.getEditedAt()).isNotNull();

        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/" + room.getId()),
                org.mockito.ArgumentMatchers.argThat((ChatWebSocketMessage ws) -> "EDIT".equals(ws.getType())));
    }

    @Test
    @DisplayName("내용이 그대로면 무동작 — editedAt을 찍지 않고 200으로 반환한다")
    void noopWhenContentUnchanged() {
        ChatMessage message = saveMessage(SENDER, ChatMessage.MessageType.TEXT, "원본", false);

        ChatMessageDTO result = chatService.editMessage(room.getId(), message.getId(), "원본", SENDER);

        assertThat(result.getContent()).isEqualTo("원본");
        assertThat(result.getEditedAt()).isNull();

        ChatMessage reloaded = chatMessageRepository.findById(message.getId()).orElseThrow();
        assertThat(reloaded.getEditedAt()).isNull();
    }

    @Test
    @DisplayName("관리자/직원 id 충돌: admin_3은 member 3의 메시지를 수정할 수 없다")
    void adminMemberIdCollisionIsNotConfused() {
        ChatMessage message = saveMessage("3", ChatMessage.MessageType.TEXT, "원본", false); // member id 3

        assertThatThrownBy(() -> chatService.editMessage(room.getId(), message.getId(), "바꾼 내용", "admin_3"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
