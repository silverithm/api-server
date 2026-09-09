package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatMessageRead;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;
import org.springframework.test.context.TestPropertySource;

/**
 * 읽음 처리는 **여러 번 해도 같은 결과**여야 한다.
 *
 * 같은 사람이 앱과 웹을 함께 켜 두면 두 경로가 거의 동시에 읽음을 보낸다. 둘 다 '안 읽음'으로
 * 조회한 뒤 둘 다 저장을 시도하는데, (message_id, user_id) 유니크 제약이 뒤엣것을 막아
 * **읽음 처리가 500으로 떨어졌다** — 운영에서 하루 15건, 채팅방 읽음 API에서 났다.
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
        "spring.datasource.url=jdbc:h2:mem:markasread;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatMarkAsReadIdempotentTest {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private ChatService chatService;
    private ChatReadRecorder chatReadRecorder;
    private ChatRoom room;
    private ChatMessage message;

    private static final String READER = "7";

    @BeforeEach
    void setUp() {
        chatReadRecorder = new ChatReadRecorder(chatMessageReadRepository, em);
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                mock(SimpMessagingTemplate.class), mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor(), chatReadRecorder);

        Company company = companyRepository.save(Company.of("햇살요양원", "서울", null));
        room = chatRoomRepository.save(ChatRoom.builder()
                .name("방").company(company).createdBy("1").createdByName("보낸이")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).build());

        chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(room).userId(READER).userName("읽는이").isActive(true).build());

        message = chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(room).senderId("1").senderName("보낸이")
                .type(ChatMessage.MessageType.TEXT).content("안녕하세요")
                .isDeleted(false).build());
        em.flush();
        em.clear();
    }

    private long readRecordCount() {
        em.flush();
        em.clear();
        return chatMessageReadRepository.findByMessageIdOrderByReadAtDesc(message.getId()).size();
    }

    private ChatMessageRead readRecordFor(ChatMessage target) {
        return ChatMessageRead.builder()
                .message(target).userId(READER).userName("읽는이").build();
    }

    /**
     * 경합은 순차 호출로 재현되지 않는다 — 두 번째 호출은 '안 읽음' 조회에서 이미 걸러진다.
     * 실제로 부딪히는 지점은 **기록을 넣는 순간**이므로 거기를 직접 겨눈다.
     * (앱과 웹이 동시에 읽으면 둘 다 '안 읽음'으로 보고 둘 다 이 자리로 들어온다)
     */
    @Test
    @DisplayName("이미 남은 기록과 겹쳐도 터지지 않는다 — 겹치는 순간이 500이 나던 자리다")
    void duplicateRecordDoesNotThrow() {
        chatReadRecorder.recordOne(readRecordFor(message));

        assertThatCode(() -> chatReadRecorder.recordOne(readRecordFor(message)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("겹쳐도 읽음 기록은 하나다 — 읽은 사람 수가 부풀지 않는다")
    void readRecordStaysSingle() {
        chatReadRecorder.recordOne(readRecordFor(message));
        chatReadRecorder.recordOne(readRecordFor(message));

        assertThat(readRecordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("한꺼번에 넣다 겹치면 실패를 알린다 — 호출자가 한 건씩 다시 넣어 나머지를 살린다")
    void batchReportsFailureSoCallerCanRetryOneByOne() {
        chatReadRecorder.recordOne(readRecordFor(message));

        ChatMessage another = chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(room).senderId("1").senderName("보낸이")
                .type(ChatMessage.MessageType.TEXT).content("두 번째")
                .isDeleted(false).build());
        em.flush();

        final boolean allSaved = chatReadRecorder.recordAll(
                List.of(readRecordFor(message), readRecordFor(another)));
        assertThat(allSaved).isFalse();

        // 호출자가 하는 대로 한 건씩 다시 넣으면 겹치지 않은 것은 남는다
        chatReadRecorder.recordOne(readRecordFor(message));
        chatReadRecorder.recordOne(readRecordFor(another));

        em.flush();
        em.clear();
        assertThat(chatMessageReadRepository.findByMessageIdOrderByReadAtDesc(another.getId())).hasSize(1);
    }

    @Test
    @DisplayName("처음 읽으면 기록이 남는다 — 중복을 막느라 아예 안 남기면 안 된다")
    void firstReadIsRecorded() {
        chatService.markAsRead(room.getId(), READER, "읽는이", message.getId());

        assertThat(readRecordCount()).isEqualTo(1);
    }
}
