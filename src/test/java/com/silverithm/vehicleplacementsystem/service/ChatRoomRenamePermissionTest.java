package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ChatRoomDTO;
import com.silverithm.vehicleplacementsystem.dto.ChatRoomUpdateRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채팅방 이름 변경 권한 (2026-10 버그제보방 '채팅방 이름 변경' 요청).
 * 이름은 방 사람 모두의 목록에 바로 보이므로 기관 관리자나 방을 만든 사람만 바꾼다.
 * 설명 같은 나머지 항목은 예전처럼 둔다.
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
        "spring.datasource.url=jdbc:h2:mem:roomrename;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatRoomRenamePermissionTest {

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
    private Long roomId;

    private static final String CREATOR = "7";
    private static final String COLLEAGUE = "8";
    private static final String COMPANY_ADMIN = "admin_3";

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
            chatParticipantRepository.deleteAll();
            chatRoomRepository.deleteAll();
            companyRepository.deleteAll();

            Company company = companyRepository.save(Company.of("햇살요양원", "서울", null));
            ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                    .name("요양보호사방").company(company).createdBy(CREATOR).createdByName("만든이")
                    .status(ChatRoom.ChatRoomStatus.ACTIVE).lastMessageAt(LocalDateTime.now()).build());
            for (String m : new String[]{CREATOR, COLLEAGUE}) {
                chatParticipantRepository.save(ChatParticipant.builder()
                        .chatRoom(room).userId(m).userName("직원" + m).isActive(true).build());
            }
            roomId = room.getId();
        });
    }

    private ChatRoomDTO rename(String caller, String name) {
        ChatRoomUpdateRequest request = ChatRoomUpdateRequest.builder().name(name).build();
        return tx.execute(s -> chatService.updateChatRoom(roomId, request, caller, null));
    }

    private String currentName() {
        return tx.execute(s -> chatRoomRepository.findById(roomId).orElseThrow().getName());
    }

    @Test
    @DisplayName("방을 만든 사람은 이름을 바꿀 수 있고, 앞뒤 공백은 지운다")
    void creatorCanRename() {
        ChatRoomDTO room = rename(CREATOR, "  1층 요양보호사방 ");

        assertThat(room.getName()).isEqualTo("1층 요양보호사방");
        assertThat(currentName()).isEqualTo("1층 요양보호사방");
    }

    @Test
    @DisplayName("기관 관리자 계정은 자기가 만들지 않은 방도 이름을 바꿀 수 있다")
    void companyAdminCanRename() {
        rename(COMPANY_ADMIN, "요양팀 전체방");

        assertThat(currentName()).isEqualTo("요양팀 전체방");
    }

    @Test
    @DisplayName("같은 방의 다른 직원은 이름을 바꿀 수 없다")
    void participantWhoDidNotCreateCannotRename() {
        assertThatThrownBy(() -> rename(COLLEAGUE, "내 맘대로 바꾼 방"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("권한");
        assertThat(currentName()).isEqualTo("요양보호사방");
    }

    @Test
    @DisplayName("빈 이름이나 너무 긴 이름은 받지 않는다")
    void rejectsBlankOrTooLongName() {
        assertThatThrownBy(() -> rename(CREATOR, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rename(CREATOR, "가".repeat(ChatService.MAX_ROOM_NAME_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(currentName()).isEqualTo("요양보호사방");
    }

    @Test
    @DisplayName("이름을 그대로 두고 설명만 바꾸는 건 예전처럼 누구나 된다")
    void descriptionOnlyUpdateIsUnchanged() {
        ChatRoomUpdateRequest request = ChatRoomUpdateRequest.builder()
                .name("요양보호사방").description("근무 공지").build();

        ChatRoomDTO room = tx.execute(s -> chatService.updateChatRoom(roomId, request, COLLEAGUE, null));

        assertThat(room.getName()).isEqualTo("요양보호사방");
        assertThat(room.getDescription()).isEqualTo("근무 공지");
    }
}
