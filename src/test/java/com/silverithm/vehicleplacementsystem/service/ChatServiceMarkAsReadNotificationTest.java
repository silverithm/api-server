package com.silverithm.vehicleplacementsystem.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.*;
import java.time.LocalDateTime;
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
 * 채팅방을 읽으면(markAsRead) 그 방과 관련된 CHAT 알림도 같이 읽음 처리되는지 확인한다.
 *
 * 예전엔 ChatParticipant.lastRead만 업데이트하고 Notification 테이블은 건드리지 않아,
 * 방을 다 읽고 나와도 알림함 종 아이콘엔 계속 안읽음 배지가 남아 있었다.
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
        "spring.datasource.url=jdbc:h2:mem:chatmarkasreadnotif;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatServiceMarkAsReadNotificationTest {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager txManager;

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private ChatService chatService;
    private NotificationService notificationService;
    private ChatRoom room;

    private static final String READER = "3"; // member id 3

    @BeforeEach
    void setUp() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        notificationService = mock(NotificationService.class);

        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                messagingTemplate, notificationService,
                mock(ResourceScopeGuard.class), directExecutor(),
                new ChatReadRecorder(chatMessageReadRepository, txManager),
                ChatTestSupport.writer(chatRoomRepository, chatParticipantRepository,
                        chatMessageRepository, chatMessageReadRepository),
                ChatTestSupport.metrics());

        Company company = companyRepository.save(Company.of("테스트기관", "서울", null));
        room = chatRoomRepository.save(ChatRoom.builder()
                .name("방").company(company).createdBy(READER).createdByName("나")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).build());

        chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(room).userId(READER).memberId(3L).userName("나")
                .joinedAt(LocalDateTime.now()).isActive(true).build());
    }

    @Test
    @DisplayName("방을 읽으면 그 방(relatedEntityType=chatRoom) 알림도 같은 사용자 식별자로 읽음 처리를 요청한다")
    void markAsReadAlsoClearsRelatedChatNotifications() {
        chatService.markAsRead(room.getId(), READER, "나", null);

        verify(notificationService).markReadByRelatedEntity(
                eq(READER), eq(room.getId()), eq("chatRoom"));
    }
}
