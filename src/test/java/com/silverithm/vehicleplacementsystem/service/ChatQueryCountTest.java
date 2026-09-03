package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.silverithm.vehicleplacementsystem.dto.ChatMessageDTO;
import com.silverithm.vehicleplacementsystem.dto.ChatRoomDTO;
import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 채팅 조회가 방/메시지 개수에 비례해 쿼리를 날리지 않는지(N+1) 못박는다.
 *
 * 지금 규모(운영 chat_messages 614행)에서는 인덱스가 아니라 쿼리 '개수'가 응답 시간을 정한다.
 * 그래서 시간이 아니라 실행된 SQL 수를 잰다 — 기계가 느려도 흔들리지 않는 기준이다.
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
        "spring.datasource.url=jdbc:h2:mem:chatperf;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.hibernate.stat=WARN"
})
class ChatQueryCountTest {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    /** 테스트에서는 알림 스레드를 그 자리에서 돌린다 (전송 자체는 목이라 실제 호출은 없다) */
    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private ChatService chatService;
    private Statistics stats;
    private Long companyId;

    private static final int ROOMS = 6;
    private static final int PARTICIPANTS = 5;
    private static final int MESSAGES_PER_ROOM = 30;
    private static final String ME = "1";

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                mock(SimpMessagingTemplate.class), mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor());

        Company company = companyRepository.save(Company.of("테스트기관", "서울", null));
        companyId = company.getId();

        for (int r = 0; r < ROOMS; r++) {
            ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                    .name("방" + r).company(company).createdBy(ME).createdByName("나")
                    .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
            for (int p = 1; p <= PARTICIPANTS; p++) {
                chatParticipantRepository.save(ChatParticipant.builder()
                        .chatRoom(room).userId(String.valueOf(p)).userName("사람" + p)
                        .isActive(true).build());
            }
            for (int m = 0; m < MESSAGES_PER_ROOM; m++) {
                chatMessageRepository.save(ChatMessage.builder()
                        .chatRoom(room).senderId("2").senderName("사람2")
                        .type(ChatMessage.MessageType.TEXT).content("안녕 " + m)
                        .isDeleted(false).build());
            }
        }
        em.flush();
        em.clear();

        stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private long countQueries(Runnable action) {
        em.clear();
        long before = stats.getPrepareStatementCount();
        action.run();
        em.flush(); // 미뤄둔 쓰기까지 세고, 다음 단계가 그 결과를 보게 한다 (clear는 flush하지 않는다)
        long after = stats.getPrepareStatementCount();
        em.clear();
        return after - before;
    }

    @Test
    @DisplayName("채팅방 목록: 방 개수에 비례해 쿼리가 늘지 않는다")
    void roomListIsConstantQueries() {
        long q = countQueries(() -> {
            List<ChatRoomDTO> rooms = chatService.getChatRooms(companyId, ME);
            assertThat(rooms).hasSize(ROOMS);
            // 한 번에 세더라도 값은 예전과 같아야 한다: 아직 아무것도 안 읽었으니 전부 안 읽은 상태
            assertThat(rooms).allSatisfy(r -> {
                assertThat(r.getUnreadCount()).isEqualTo(MESSAGES_PER_ROOM);
                assertThat(r.getParticipantCount()).isEqualTo(PARTICIPANTS);
                assertThat(r.getLastMessage()).isNotNull();
                assertThat(r.getLastMessage().getContent()).isEqualTo("안녕 " + (MESSAGES_PER_ROOM - 1));
            });
        });
        System.out.println("### 채팅방 목록(" + ROOMS + "개) 쿼리 수 = " + q);
        // 6 → 7: 목록에 참여자 얼굴(카카오톡식 방 아이콘)을 실으면서 한 번 늘었다.
        // 참가자와 그 사람의 사진을 한 쿼리로 이어 붙였으므로 이게 최소다.
        // 지키려는 것은 '방 개수에 비례하지 않는다'이고 그건 그대로다 —
        // 방마다 물었다면 방 6개에서 열몇 번이 나왔을 것이다.
        assertThat(q).isLessThanOrEqualTo(7);
    }

    @Test
    @DisplayName("메시지 목록: 메시지 개수에 비례해 쿼리가 늘지 않는다")
    void messageListIsConstantQueries() {
        Long roomId = chatRoomRepository.findAll().get(0).getId();
        long q = countQueries(() -> {
            List<ChatMessageDTO> msgs = chatService.getMessages(roomId, 0, MESSAGES_PER_ROOM, ME);
            assertThat(msgs).hasSize(MESSAGES_PER_ROOM);
        });
        System.out.println("### 메시지 목록(" + MESSAGES_PER_ROOM + "건) 쿼리 수 = " + q);
        assertThat(q).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("읽음 처리: 안 읽은 메시지 개수에 비례해 쿼리가 폭증하지 않는다")
    void markAsReadIsNotPerMessage() {
        ChatRoom room = chatRoomRepository.findAll().get(0);
        Long lastId = chatMessageRepository
                .findFirstByChatRoomIdOrderByCreatedAtDesc(room.getId()).orElseThrow().getId();
        long q = countQueries(() -> chatService.markAsRead(room.getId(), ME, "나", lastId));
        System.out.println("### 읽음 처리(" + MESSAGES_PER_ROOM + "건) 쿼리 수 = " + q);
        // 남는 것은 행을 실제로 만드는 INSERT뿐 — 건별 조회·중복확인이 사라졌다
        assertThat(q).isLessThanOrEqualTo(MESSAGES_PER_ROOM + 5);

        // 읽음 처리가 실제로 반영돼 방 목록의 안 읽은 수가 0이 된다
        assertThat(chatService.getChatRooms(companyId, ME))
                .filteredOn(r -> r.getId().equals(room.getId()))
                .allSatisfy(r -> assertThat(r.getUnreadCount()).isZero());
    }
}
