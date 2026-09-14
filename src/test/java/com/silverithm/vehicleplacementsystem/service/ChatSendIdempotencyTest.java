package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageCreateRequest;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageDTO;
import com.silverithm.vehicleplacementsystem.dto.ChatWebSocketMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 메시지를 두 번 보내도 한 건만 남는다.
 *
 * <p>앱은 소켓으로 보낸 뒤 5초 안에 서버 응답이 없으면 REST로 같은 메시지를 다시 보낸다.
 * 소켓 전송이 실은 도착해 있었다면 이 재전송이 두 번째 메시지가 되어서는 안 된다.
 * 열쇠는 보내는 쪽이 붙인 {@code clientMessageId}다. 없으면(구버전) 예전처럼 매번 새 메시지다.
 *
 * <p>테스트 트랜잭션을 쓰지 않는다 — 저장기의 트랜잭션이 실제로 커밋돼야 '커밋 뒤 방송'까지 볼 수 있다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class, ChatMessageWriter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:chatidem;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatSendIdempotencyTest {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ChatMessageWriter writer;   // 프록시가 붙은 진짜 빈 — 트랜잭션이 실제로 커밋된다

    private SimpMessagingTemplate messagingTemplate;
    private ChatService chatService;
    private Long roomId;

    private static final String SENDER = "7";
    private static final String OTHER = "8";

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                messagingTemplate, mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor(),
                new ChatReadRecorder(chatMessageReadRepository, txManager),
                writer, ChatTestSupport.metrics());

        TransactionTemplate tx = new TransactionTemplate(txManager);
        roomId = tx.execute(status -> {
            chatMessageReadRepository.deleteAll();
            chatMessageRepository.deleteAll();
            chatParticipantRepository.deleteAll();
            chatRoomRepository.deleteAll();
            companyRepository.deleteAll();

            Company company = companyRepository.save(Company.of("햇살요양원", "서울", null));
            ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                    .name("방").company(company).createdBy(SENDER).createdByName("보낸이")
                    .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
            for (String userId : new String[]{SENDER, OTHER}) {
                chatParticipantRepository.save(ChatParticipant.builder()
                        .chatRoom(room).userId(userId).userName("사람" + userId).isActive(true).build());
            }
            return room.getId();
        });
    }

    private static ChatMessageCreateRequest text(String senderId, String content, String clientMessageId) {
        return ChatMessageCreateRequest.builder()
                .senderId(senderId).senderName("사람" + senderId).type("TEXT").content(content)
                .clientMessageId(clientMessageId).build();
    }

    @Test
    @DisplayName("같은 식별자로 두 번 보내면 한 건만 남고, 두 번째도 같은 메시지를 돌려받는다")
    void sameKeyStoresOnce() {
        ChatMessageDTO first = chatService.sendMessage(roomId, text(SENDER, "안녕", "uuid-1"));
        ChatMessageDTO second = chatService.sendMessage(roomId, text(SENDER, "안녕", "uuid-1"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getClientMessageId()).isEqualTo("uuid-1");
        assertThat(chatMessageRepository.count()).isEqualTo(1);
        // 상대는 이미 받았다 — 방송은 처음 한 번뿐
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/chat/" + roomId), any(Object.class));
    }

    @Test
    @DisplayName("방송되는 메시지에 식별자가 실려 있다 — 보낸 사람이 자기 '전송 중' 말풍선을 이걸로 찾는다")
    void broadcastCarriesClientMessageId() {
        chatService.sendMessage(roomId, text(SENDER, "안녕", "uuid-2"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/" + roomId), payload.capture());
        ChatWebSocketMessage sent = (ChatWebSocketMessage) payload.getValue();
        assertThat(sent.getMessage().getClientMessageId()).isEqualTo("uuid-2");
    }

    @Test
    @DisplayName("식별자 없이 보내면(구버전) 예전처럼 매번 새 메시지다 — 같은 말을 두 번 해도 둘 다 남는다")
    void withoutKeyEverySendIsNew() {
        chatService.sendMessage(roomId, text(SENDER, "네", null));
        chatService.sendMessage(roomId, text(SENDER, "네", null));
        chatService.sendMessage(roomId, text(SENDER, "네", "   "));

        assertThat(chatMessageRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("식별자는 보낸 사람 안에서만 유일하다 — 다른 사람이 우연히 같은 값을 써도 막히지 않는다")
    void keyIsScopedToSender() {
        chatService.sendMessage(roomId, text(SENDER, "안녕", "shared"));
        chatService.sendMessage(roomId, text(OTHER, "안녕", "shared"));

        assertThat(chatMessageRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("저장되면 방의 마지막 메시지 시각이 갱신된다 — 한 컬럼만 고치는 경로로 바뀌어도 빠지면 안 된다")
    void roomLastMessageAtIsTouched() {
        assertThat(chatRoomRepository.findById(roomId).orElseThrow().getLastMessageAt()).isNull();

        chatService.sendMessage(roomId, text(SENDER, "안녕", "uuid-3"));

        assertThat(chatRoomRepository.findById(roomId).orElseThrow().getLastMessageAt()).isNotNull();
    }

    @Test
    @DisplayName("보낸 사람은 자기 메시지를 읽은 것으로 남는다 — 읽은 사람 수가 0에서 시작하지 않는다")
    void senderReadIsRecorded() {
        ChatMessageDTO dto = chatService.sendMessage(roomId, text(SENDER, "안녕", "uuid-4"));

        assertThat(dto.getReadCount()).isEqualTo(1);
        assertThat(chatMessageReadRepository.findByMessageIdOrderByReadAtDesc(dto.getId())).hasSize(1);
    }
}
