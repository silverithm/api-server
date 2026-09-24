package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ChatRoomDTO;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.*;
import java.time.LocalDateTime;
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
 * 자주 쓰는 채팅방을 목록 맨 위에 고정한다 (2026-09-24 버그제보방 요청).
 * 고정은 사람마다 다르고, 고정한 방끼리는 최근 대화 순을 지킨다.
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
        "spring.datasource.url=jdbc:h2:mem:roompin;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatRoomPinTest {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager txManager;

    private ChatService chatService;
    private TransactionTemplate tx;
    private Long companyId;
    private Long newestRoom;
    private Long middleRoom;
    private Long oldestRoom;
    private Long strangerRoom;

    private static final String ME = "7";
    private static final String COLLEAGUE = "8";

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                mock(SimpMessagingTemplate.class), mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor(),
                new ChatReadRecorder(chatMessageReadRepository, txManager),
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
            companyId = company.getId();
            LocalDateTime now = LocalDateTime.now();
            newestRoom = room(company, "최근 방", now.minusMinutes(1), ME, COLLEAGUE);
            middleRoom = room(company, "중간 방", now.minusHours(1), ME, COLLEAGUE);
            oldestRoom = room(company, "오래된 방", now.minusDays(3), ME, COLLEAGUE);
            strangerRoom = room(company, "내가 없는 방", now, COLLEAGUE);
        });
    }

    private Long room(Company company, String name, LocalDateTime lastMessageAt, String... members) {
        ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                .name(name).company(company).createdBy(members[0]).createdByName("만든이")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).lastMessageAt(lastMessageAt).build());
        for (String m : members) {
            chatParticipantRepository.save(ChatParticipant.builder()
                    .chatRoom(room).userId(m).userName("직원" + m).isActive(true).build());
        }
        return room.getId();
    }

    // 서비스를 스프링 프록시 없이 만들었으므로 운영처럼 트랜잭션 안에서 부른다
    private List<ChatRoomDTO> roomsFor(String userId) {
        return tx.execute(s -> chatService.getChatRooms(companyId, userId));
    }

    private boolean pin(Long roomId, String userId, boolean pinned) {
        return Boolean.TRUE.equals(tx.execute(s -> chatService.setRoomPinned(roomId, userId, pinned)));
    }

    private List<String> namesFor(String userId) {
        return roomsFor(userId).stream().map(ChatRoomDTO::getName).toList();
    }

    @Test
    @DisplayName("고정하지 않으면 지금처럼 최근 대화 순이다")
    void defaultOrderIsByRecentActivity() {
        assertThat(namesFor(ME)).containsExactly("최근 방", "중간 방", "오래된 방");
        assertThat(roomsFor(ME)).noneMatch(ChatRoomDTO::isPinned);
    }

    @Test
    @DisplayName("고정한 방은 대화가 오래됐어도 맨 위에 온다")
    void pinnedRoomGoesToTop() {
        boolean result = pin(oldestRoom, ME, true);

        assertThat(result).isTrue();
        List<ChatRoomDTO> rooms = roomsFor(ME);
        assertThat(rooms).extracting(ChatRoomDTO::getName).containsExactly("오래된 방", "최근 방", "중간 방");
        assertThat(rooms.get(0).isPinned()).isTrue();
        assertThat(rooms.get(0).getPinnedAt()).isNotNull();
    }

    @Test
    @DisplayName("고정한 방이 여럿이면 그 안에서도 최근 대화 순이다")
    void pinnedRoomsKeepActivityOrderAmongThemselves() {
        pin(oldestRoom, ME, true);
        pin(middleRoom, ME, true);

        assertThat(namesFor(ME)).containsExactly("중간 방", "오래된 방", "최근 방");
    }

    @Test
    @DisplayName("고정은 나만의 것이다 — 같은 방에 있는 동료의 목록은 그대로다")
    void pinIsPerPerson() {
        pin(oldestRoom, ME, true);

        assertThat(namesFor(COLLEAGUE)).containsExactly("내가 없는 방", "최근 방", "중간 방", "오래된 방");
        assertThat(roomsFor(COLLEAGUE)).noneMatch(ChatRoomDTO::isPinned);
    }

    @Test
    @DisplayName("고정을 풀면 원래 자리로 돌아간다")
    void unpinRestoresActivityOrder() {
        pin(oldestRoom, ME, true);
        boolean result = pin(oldestRoom, ME, false);

        assertThat(result).isFalse();
        assertThat(namesFor(ME)).containsExactly("최근 방", "중간 방", "오래된 방");
    }

    @Test
    @DisplayName("다시 고정해도 처음 고정한 시각이 바뀌지 않는다")
    void repinKeepsOriginalTime() {
        pin(oldestRoom, ME, true);
        LocalDateTime first = roomsFor(ME).get(0).getPinnedAt();

        pin(oldestRoom, ME, true);

        assertThat(roomsFor(ME).get(0).getPinnedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("참가하지 않은 방은 고정할 수 없다")
    void cannotPinRoomNotJoined() {
        assertThatThrownBy(() -> pin(strangerRoom, ME, true))
                .hasMessageContaining("참가자를 찾을 수 없습니다");
    }
}
