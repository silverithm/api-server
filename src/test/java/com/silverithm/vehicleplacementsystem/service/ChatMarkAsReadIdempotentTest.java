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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * 읽음 처리는 **여러 번 해도 같은 결과**여야 하고, 겹쳐도 **부르는 쪽 트랜잭션이 죽지 않아야** 한다.
 *
 * <p>같은 사람이 앱과 웹을 함께 켜 두면 두 경로가 거의 동시에 읽음을 보낸다. 둘 다 '안 읽음'으로
 * 조회한 뒤 둘 다 저장을 시도하는데, (message_id, user_id) 유니크 제약이 뒤엣것을 막아
 * 읽음 처리가 500으로 떨어졌다.
 *
 * <p>첫 번째 고침(REQUIRES_NEW 안에서 예외 잡기)은 테스트에서만 통과했다 — 테스트가 저장기를
 * 프록시 없이 만들어 별도 트랜잭션이 실제로 열리지 않았기 때문이다. 운영에서는 별도 트랜잭션이
 * rollback-only가 된 채 커밋을 시도해 "Transaction silently rolled back"으로 다시 500이 났다
 * (2026-09-14). 그래서 이 테스트는 **테스트 트랜잭션 없이** 픽스처를 실제로 커밋하고, 운영과
 * 같은 모양(바깥 트랜잭션 안에서 겹치는 저장)을 그대로 돌린다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    @Autowired private PlatformTransactionManager txManager;

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private ChatService chatService;
    private ChatReadRecorder chatReadRecorder;
    private TransactionTemplate tx;
    private Long roomId;
    private Long messageId;

    private static final String READER = "7";

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        chatReadRecorder = new ChatReadRecorder(chatMessageReadRepository, txManager);
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                mock(SimpMessagingTemplate.class), mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor(), chatReadRecorder,
                ChatTestSupport.writer(chatRoomRepository, chatParticipantRepository,
                        chatMessageRepository, chatMessageReadRepository),
                ChatTestSupport.metrics());

        tx.executeWithoutResult(status -> {
            chatMessageReadRepository.deleteAll();
            chatMessageRepository.deleteAll();
            chatParticipantRepository.deleteAll();
            chatRoomRepository.deleteAll();
            companyRepository.deleteAll();

            Company company = companyRepository.save(Company.of("햇살요양원", "서울", null));
            ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                    .name("방").company(company).createdBy("1").createdByName("보낸이")
                    .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
            chatParticipantRepository.save(ChatParticipant.builder()
                    .chatRoom(room).userId(READER).userName("읽는이").isActive(true).build());
            ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(room).senderId("1").senderName("보낸이")
                    .type(ChatMessage.MessageType.TEXT).content("안녕하세요")
                    .isDeleted(false).build());
            roomId = room.getId();
            messageId = message.getId();
        });
    }

    private long readRecordCount(Long target) {
        return chatMessageReadRepository.findByMessageIdOrderByReadAtDesc(target).size();
    }

    private ChatMessageRead readRecordFor(Long target) {
        return ChatMessageRead.builder()
                .message(chatMessageRepository.getReferenceById(target)).userId(READER).userName("읽는이").build();
    }

    private Long anotherMessage(String content) {
        return tx.execute(status -> chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(chatRoomRepository.getReferenceById(roomId)).senderId("1").senderName("보낸이")
                .type(ChatMessage.MessageType.TEXT).content(content)
                .isDeleted(false).build()).getId());
    }

    @Test
    @DisplayName("이미 남은 기록과 겹쳐도 터지지 않는다 — 겹치는 순간이 500이 나던 자리다")
    void duplicateRecordDoesNotThrow() {
        chatReadRecorder.recordOne(readRecordFor(messageId));

        assertThatCode(() -> chatReadRecorder.recordOne(readRecordFor(messageId)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("겹쳐도 읽음 기록은 하나다 — 읽은 사람 수가 부풀지 않는다")
    void readRecordStaysSingle() {
        chatReadRecorder.recordOne(readRecordFor(messageId));
        chatReadRecorder.recordOne(readRecordFor(messageId));

        assertThat(readRecordCount(messageId)).isEqualTo(1);
    }

    @Test
    @DisplayName("한꺼번에 넣다 겹치면 실패를 알린다 — 호출자가 한 건씩 다시 넣어 나머지를 살린다")
    void batchReportsFailureSoCallerCanRetryOneByOne() {
        chatReadRecorder.recordOne(readRecordFor(messageId));
        Long another = anotherMessage("두 번째");

        boolean allSaved = chatReadRecorder.recordAll(
                List.of(readRecordFor(messageId), readRecordFor(another)));
        assertThat(allSaved).isFalse();

        // 호출자가 하는 대로 한 건씩 다시 넣으면 겹치지 않은 것은 남는다
        chatReadRecorder.recordOne(readRecordFor(messageId));
        chatReadRecorder.recordOne(readRecordFor(another));

        assertThat(readRecordCount(another)).isEqualTo(1);
        assertThat(readRecordCount(messageId)).isEqualTo(1);
    }

    /**
     * 운영에서 실제로 난 모양. 읽음 처리(markAsRead)는 자기 트랜잭션 안에서 저장기를 부른다.
     * 저장기가 겹침을 삼키지 못하면 바깥 트랜잭션이 rollback-only가 되어 커밋에서 터진다.
     */
    @Test
    @DisplayName("바깥 트랜잭션 안에서 겹쳐도 바깥 트랜잭션은 멀쩡히 커밋된다 — 2026-09-14 운영 500")
    void outerTransactionSurvivesDuplicateInside() {
        chatReadRecorder.recordOne(readRecordFor(messageId));
        Long another = anotherMessage("두 번째");

        assertThatCode(() -> tx.executeWithoutResult(status -> {
            // 바깥 트랜잭션이 한 다른 일 — 겹침 때문에 같이 사라지면 안 된다
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoomRepository.getReferenceById(roomId)).senderId("1").senderName("보낸이")
                    .type(ChatMessage.MessageType.TEXT).content("바깥 일").isDeleted(false).build());

            if (!chatReadRecorder.recordAll(List.of(readRecordFor(messageId), readRecordFor(another)))) {
                chatReadRecorder.recordOne(readRecordFor(messageId));
                chatReadRecorder.recordOne(readRecordFor(another));
            }
        })).doesNotThrowAnyException();

        assertThat(readRecordCount(messageId)).isEqualTo(1);
        assertThat(readRecordCount(another)).isEqualTo(1);
        assertThat(chatMessageRepository.count()).as("바깥 트랜잭션의 저장이 살아 있다").isEqualTo(3);
    }

    @Test
    @DisplayName("처음 읽으면 기록이 남는다 — 중복을 막느라 아예 안 남기면 안 된다")
    void firstReadIsRecorded() {
        chatService.markAsRead(roomId, READER, "읽는이", messageId);

        assertThat(readRecordCount(messageId)).isEqualTo(1);
    }

    @Test
    @DisplayName("앱과 웹이 같은 위치를 연달아 읽어도 두 번째 읽음 처리가 500으로 떨어지지 않는다")
    void secondMarkAsReadWithExistingRecordSucceeds() {
        // 앱이 먼저 읽음 기록만 남긴 상태(마지막 읽은 위치는 아직 갱신 전)
        chatReadRecorder.recordOne(readRecordFor(messageId));

        assertThatCode(() -> chatService.markAsRead(roomId, READER, "읽는이", messageId))
                .doesNotThrowAnyException();
        assertThat(readRecordCount(messageId)).isEqualTo(1);
    }
}
