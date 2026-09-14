package com.silverithm.vehicleplacementsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * "날짜로 이동" 파생 쿼리(findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc)가
 * 실제로 파싱·실행되는지 검증한다.
 *
 * 파생 쿼리라 컴파일은 항상 통과하지만, 메서드 이름이 필드명과 어긋나면(예: 오타) 빈 생성 시점에
 * 터진다(메모리 prod-backend-deploy-gap.md 참고) — 그래서 실제 컨텍스트를 띄우는 슬라이스 테스트로 확인한다.
 *
 * ChatMessage.onCreate()가 @PrePersist에서 createdAt을 항상 now()로 덮어써서 저장 시점에는
 * 원하는 시각을 넣을 수 없다. save()로 먼저 만든 뒤 setCreatedAt으로 고쳐 다시 save()하면
 * update 경로(merge)라 @PrePersist가 다시 돌지 않는다 — 그 방식으로 임의 시각을 만든다.
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
        "spring.datasource.url=jdbc:h2:mem:chatfirstondate;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatMessageFirstOnDateRepositoryTest {

    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EntityManager em;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.of("햇살요양원", "서울", null));
        room = chatRoomRepository.save(ChatRoom.builder()
                .name("방").company(company).createdBy("1").createdByName("나")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
    }

    /** createdAt을 원하는 시각으로 강제한 메시지를 만든다. */
    private ChatMessage 메시지(LocalDateTime createdAt) {
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(room).senderId("1").senderName("보낸사람")
                .type(ChatMessage.MessageType.TEXT)
                .content("메시지 " + createdAt)
                .isDeleted(false)
                .build());
        saved.setCreatedAt(createdAt);
        chatMessageRepository.save(saved); // update 경로 — @PrePersist가 다시 안 돈다
        em.flush();
        em.clear();
        return saved;
    }

    @Test
    @DisplayName("전날 23:59 메시지는 제외하고, 당일 00:00은 포함한다")
    void dateBoundary() {
        메시지(LocalDateTime.of(2026, 9, 9, 23, 59));
        ChatMessage midnight = 메시지(LocalDateTime.of(2026, 9, 10, 0, 0));
        메시지(LocalDateTime.of(2026, 9, 10, 9, 0));

        var result = chatMessageRepository.findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                room.getId(), LocalDateTime.of(2026, 9, 10, 0, 0));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(midnight.getId());
    }

    @Test
    @DisplayName("그 날짜 이후 메시지가 없으면 비어 있다")
    void emptyWhenNothingAfter() {
        메시지(LocalDateTime.of(2026, 9, 1, 12, 0));

        var result = chatMessageRepository.findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                room.getId(), LocalDateTime.of(2026, 9, 10, 0, 0));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 메시지도 포함해서 찾는다 — around 조회와 같은 기준")
    void includesDeletedMessages() {
        ChatMessage deleted = 메시지(LocalDateTime.of(2026, 9, 10, 1, 0));
        deleted.setIsDeleted(true);
        chatMessageRepository.save(deleted);
        em.flush();
        em.clear();

        var result = chatMessageRepository.findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                room.getId(), LocalDateTime.of(2026, 9, 10, 0, 0));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(deleted.getId());
    }

    @Test
    @DisplayName("다른 방의 메시지는 섞이지 않는다")
    void doesNotLeakAcrossRooms() {
        Company otherCompany = companyRepository.save(Company.of("다른요양원", "부산", null));
        ChatRoom otherRoom = chatRoomRepository.save(ChatRoom.builder()
                .name("남의방").company(otherCompany).createdBy("2").createdByName("남")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(otherRoom).senderId("2").senderName("남")
                .type(ChatMessage.MessageType.TEXT)
                .content("남의 메시지")
                .isDeleted(false)
                .build());
        saved.setCreatedAt(LocalDateTime.of(2026, 9, 10, 5, 0));
        chatMessageRepository.save(saved);
        em.flush();
        em.clear();

        var result = chatMessageRepository.findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                room.getId(), LocalDateTime.of(2026, 9, 10, 0, 0));

        assertThat(result).isEmpty();
    }
}
