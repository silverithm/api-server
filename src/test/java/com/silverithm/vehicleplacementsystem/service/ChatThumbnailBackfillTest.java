package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatRoomRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 축소본이 없는 옛 사진에 축소본을 채우는 정비 작업.
 *
 * 축소본이 없으면 채팅 목록이 수 MB짜리 원본을 그대로 그린다 — 옛 대화를 훑을 때
 * "종종 사진 깨짐"이 나오던 방아쇠다. 여러 번 돌려도 안전해야 하고,
 * 못 만드는 사진(이미 작거나 자바가 못 읽는 포맷) 때문에 멈추면 안 된다.
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
        "spring.datasource.url=jdbc:h2:mem:thumbbackfill;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatThumbnailBackfillTest {

    private static final String PREFIX = "https://bucket.s3.ap-northeast-2.amazonaws.com/carev/";

    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EntityManager em;

    private FileStorageService fileStorageService;
    private ChatThumbnailBackfillService backfill;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        when(fileStorageService.getFileUrl("")).thenReturn(PREFIX);
        backfill = new ChatThumbnailBackfillService(chatMessageRepository, fileStorageService);

        Company company = companyRepository.save(Company.of("햇살요양원", "서울", null));
        room = chatRoomRepository.save(ChatRoom.builder()
                .name("방").company(company).createdBy("1").createdByName("나")
                .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
    }

    private ChatMessage 사진(String path, String thumbnailUrl) {
        return chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(room).senderId("1").senderName("보낸사람")
                .type(ChatMessage.MessageType.IMAGE)
                .content("사진")
                .fileUrl(PREFIX + path)
                .fileName("사진.jpg")
                .thumbnailUrl(thumbnailUrl)
                .isDeleted(false)
                .build());
    }

    private ChatThumbnailBackfillService.Result 실행(int limit) {
        em.flush();
        em.clear();
        return backfill.backfill(limit);
    }

    private String 축소본(Long id) {
        em.flush();
        em.clear();
        return chatMessageRepository.findById(id).orElseThrow().getThumbnailUrl();
    }

    @Test
    @DisplayName("축소본이 없는 사진에 축소본을 붙인다")
    void fillsMissingThumbnail() throws IOException {
        ChatMessage message = 사진("chat/1/a.jpg", null);
        when(fileStorageService.loadFile("chat/1/a.jpg")).thenReturn(new byte[]{1, 2, 3});
        when(fileStorageService.generateAndStoreThumbnail(any(byte[].class), eq("chat/1/a.jpg")))
                .thenReturn("chat/1/a_thumb.jpg");
        when(fileStorageService.getFileUrl("chat/1/a_thumb.jpg")).thenReturn(PREFIX + "chat/1/a_thumb.jpg");

        ChatThumbnailBackfillService.Result result = 실행(10);

        assertThat(result.filled()).isEqualTo(1);
        assertThat(축소본(message.getId())).isEqualTo(PREFIX + "chat/1/a_thumb.jpg");
    }

    @Test
    @DisplayName("이미 축소본이 있는 사진은 건드리지 않는다 — 여러 번 돌려도 안전하다")
    void skipsMessagesThatAlreadyHaveThumbnail() throws IOException {
        사진("chat/1/b.jpg", PREFIX + "chat/1/b_thumb.jpg");

        ChatThumbnailBackfillService.Result result = 실행(10);

        assertThat(result.total()).isZero();
        verify(fileStorageService, never()).loadFile(anyString());
    }

    @Test
    @DisplayName("축소본을 만들 수 없는 사진(이미 작거나 못 읽는 포맷)은 건너뛴다")
    void skipsWhenThumbnailNotNeeded() throws IOException {
        ChatMessage message = 사진("chat/1/small.jpg", null);
        when(fileStorageService.loadFile("chat/1/small.jpg")).thenReturn(new byte[]{1});
        when(fileStorageService.generateAndStoreThumbnail(any(byte[].class), anyString())).thenReturn(null);

        ChatThumbnailBackfillService.Result result = 실행(10);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.filled()).isZero();
        assertThat(축소본(message.getId())).isNull();
    }

    @Test
    @DisplayName("한 장이 실패해도 나머지는 계속 채운다")
    void oneFailureDoesNotStopTheRest() throws IOException {
        사진("chat/1/broken.jpg", null);
        ChatMessage good = 사진("chat/1/good.jpg", null);

        when(fileStorageService.loadFile("chat/1/broken.jpg")).thenThrow(new IOException("S3 없음"));
        when(fileStorageService.loadFile("chat/1/good.jpg")).thenReturn(new byte[]{1, 2});
        when(fileStorageService.generateAndStoreThumbnail(any(byte[].class), eq("chat/1/good.jpg")))
                .thenReturn("chat/1/good_thumb.jpg");
        when(fileStorageService.getFileUrl("chat/1/good_thumb.jpg"))
                .thenReturn(PREFIX + "chat/1/good_thumb.jpg");

        ChatThumbnailBackfillService.Result result = 실행(10);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.filled()).isEqualTo(1);
        assertThat(축소본(good.getId())).isEqualTo(PREFIX + "chat/1/good_thumb.jpg");
    }

    @Test
    @DisplayName("한 번에 limit만큼만 처리한다 — 오래 잡고 있지 않는다")
    void respectsLimit() throws IOException {
        for (int i = 0; i < 5; i++) {
            사진("chat/1/p" + i + ".jpg", null);
        }
        when(fileStorageService.loadFile(anyString())).thenReturn(new byte[]{1});
        when(fileStorageService.generateAndStoreThumbnail(any(byte[].class), anyString())).thenReturn(null);

        assertThat(실행(2).total()).isEqualTo(2);
    }

    @Test
    @DisplayName("지운 메시지와 사진이 아닌 메시지는 대상이 아니다")
    void ignoresDeletedAndNonImages() throws IOException {
        ChatMessage deleted = 사진("chat/1/deleted.jpg", null);
        deleted.setIsDeleted(true);
        chatMessageRepository.save(deleted);

        chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(room).senderId("1").senderName("보낸사람")
                .type(ChatMessage.MessageType.FILE)
                .fileUrl(PREFIX + "chat/1/doc.pdf").fileName("문서.pdf")
                .isDeleted(false).build());

        assertThat(실행(10).total()).isZero();
        verify(fileStorageService, never()).loadFile(anyString());
    }
}
