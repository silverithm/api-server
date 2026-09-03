package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ChatRoomAvatarDTO;
import com.silverithm.vehicleplacementsystem.dto.ChatRoomDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
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

/**
 * 채팅방 목록에 카카오톡처럼 참여자 얼굴이 실려 오는지 지킨다.
 *
 * 화면이 아무리 잘 그려도 서버가 얼굴을 안 실어 주면 빈 원만 나온다.
 * 여기서 못박는 것은 네 가지다 — 넷까지만, 나는 빼고, 사진은 직원·관리자 양쪽에서,
 * 그리고 나 혼자인 방은 나라도 보여준다.
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
        "spring.datasource.url=jdbc:h2:mem:chatavatar;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatRoomAvatarTest {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private ChatService chatService;
    private Long companyId;
    private String 나;

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                mock(SimpMessagingTemplate.class), mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor());

        companyId = companyRepository.save(Company.of("숲속재활어르신재가복지센터", "서울", null)).getId();
    }

    private Company 회사() {
        return companyRepository.findById(companyId).orElseThrow();
    }

    /** 사진이 있는 직원을 만든다. photoUrl이 null이면 사진 없는 사람. */
    private Member 직원(String name, String photoUrl) {
        return memberRepository.save(Member.builder()
                .name(name)
                .username(name + "@test.local")
                .email(name + "@test.local")
                .password("x")
                .role(Member.Role.CAREGIVER)
                .status(Member.MemberStatus.ACTIVE)
                .profileImageUrl(photoUrl)
                .company(회사())
                .build());
    }

    private AppUser 관리자(String name, String photoUrl) {
        AppUser u = userRepository.save(
                new AppUser(name, name + "@admin.local", "x", null, null, 회사(), null));
        u.updateProfileImageUrl(photoUrl);
        return userRepository.save(u);
    }

    private ChatRoom 방(String name) {
        return chatRoomRepository.save(ChatRoom.builder()
                .name(name).company(회사())
                .createdBy("1").createdByName("만든이")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
    }

    private void 참가(ChatRoom room, String userId, String userName) {
        chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(room).userId(userId).userName(userName).isActive(true).build());
    }

    private List<ChatRoomAvatarDTO> 얼굴들(Long roomId) {
        em.flush();
        em.clear();
        return chatService.getChatRooms(companyId, 나).stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst().orElseThrow()
                .getAvatars();
    }

    @Test
    @DisplayName("사람이 여섯이어도 얼굴은 넷까지만 온다")
    void 넷까지만() {
        Member me = 직원("나", null);
        나 = String.valueOf(me.getId());

        ChatRoom room = 방("여섯 명 방");
        참가(room, 나, "나");
        for (int i = 1; i <= 6; i++) {
            참가(room, String.valueOf(직원("동료" + i, "https://img/" + i + ".jpg").getId()), "동료" + i);
        }

        assertThat(얼굴들(room.getId())).hasSize(4);
    }

    @Test
    @DisplayName("내 얼굴은 빠진다 — 카카오톡처럼 상대만 보여준다")
    void 나는뺀다() {
        Member me = 직원("나", "https://img/me.jpg");
        나 = String.valueOf(me.getId());

        ChatRoom room = 방("셋 있는 방");
        참가(room, 나, "나");
        참가(room, String.valueOf(직원("김보경", null).getId()), "김보경");
        참가(room, String.valueOf(직원("이수나", null).getId()), "이수나");

        assertThat(얼굴들(room.getId()))
                .extracting(ChatRoomAvatarDTO::getUserName)
                .containsExactly("김보경", "이수나")
                .doesNotContain("나");
    }

    @Test
    @DisplayName("나 혼자인 방은 뺄 사람이 없으니 내 얼굴이라도 보여준다")
    void 혼자면나라도() {
        Member me = 직원("나", "https://img/me.jpg");
        나 = String.valueOf(me.getId());

        ChatRoom room = 방("나 혼자 방");
        참가(room, 나, "나");

        assertThat(얼굴들(room.getId()))
                .extracting(ChatRoomAvatarDTO::getUserName)
                .containsExactly("나");
    }

    @Test
    @DisplayName("직원 사진과 관리자 사진을 둘 다 찾아 온다")
    void 직원과관리자둘다() {
        Member me = 직원("나", null);
        나 = String.valueOf(me.getId());

        ChatRoom room = 방("직원+관리자 방");
        참가(room, 나, "나");
        참가(room, String.valueOf(직원("직원A", "https://img/staff.jpg").getId()), "직원A");
        참가(room, "admin_" + 관리자("관리자B", "https://img/admin.jpg").getId(), "관리자B");

        assertThat(얼굴들(room.getId()))
                .extracting(ChatRoomAvatarDTO::getProfileImageUrl)
                .containsExactlyInAnyOrder("https://img/staff.jpg", "https://img/admin.jpg");
    }

    @Test
    @DisplayName("사진이 없는 사람도 목록에 남는다 — 이름 첫 글자로 그려야 하니까")
    void 사진없어도빠지지않는다() {
        Member me = 직원("나", null);
        나 = String.valueOf(me.getId());

        ChatRoom room = 방("사진 없는 방");
        참가(room, 나, "나");
        참가(room, String.valueOf(직원("무사진", null).getId()), "무사진");

        List<ChatRoomAvatarDTO> faces = 얼굴들(room.getId());
        assertThat(faces).hasSize(1);
        assertThat(faces.get(0).getUserName()).isEqualTo("무사진");
        assertThat(faces.get(0).getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("방을 나간 사람은 얼굴에서 빠진다")
    void 나간사람은빠진다() {
        Member me = 직원("나", null);
        나 = String.valueOf(me.getId());

        ChatRoom room = 방("한 명 나간 방");
        참가(room, 나, "나");
        참가(room, String.valueOf(직원("남은이", null).getId()), "남은이");

        ChatParticipant 떠난이 = chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(room).userId(String.valueOf(직원("떠난이", null).getId()))
                .userName("떠난이").isActive(false).build());
        assertThat(떠난이.getIsActive()).isFalse();

        assertThat(얼굴들(room.getId()))
                .extracting(ChatRoomAvatarDTO::getUserName)
                .containsExactly("남은이");
    }

    @Test
    @DisplayName("방이 여럿이어도 얼굴이 서로 섞이지 않는다")
    void 방끼리섞이지않는다() {
        Member me = 직원("나", null);
        나 = String.valueOf(me.getId());

        ChatRoom a = 방("가 방");
        참가(a, 나, "나");
        참가(a, String.valueOf(직원("가사람", null).getId()), "가사람");

        ChatRoom b = 방("나 방");
        참가(b, 나, "나");
        참가(b, String.valueOf(직원("나사람", null).getId()), "나사람");

        em.flush();
        em.clear();
        List<ChatRoomDTO> rooms = chatService.getChatRooms(companyId, 나);

        assertThat(rooms).hasSize(2);
        assertThat(rooms.stream().filter(r -> r.getId().equals(a.getId())).findFirst().orElseThrow()
                .getAvatars()).extracting(ChatRoomAvatarDTO::getUserName).containsExactly("가사람");
        assertThat(rooms.stream().filter(r -> r.getId().equals(b.getId())).findFirst().orElseThrow()
                .getAvatars()).extracting(ChatRoomAvatarDTO::getUserName).containsExactly("나사람");
    }
}
