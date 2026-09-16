package com.silverithm.vehicleplacementsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * markReadByRelatedEntity가 대상(수신자+관련 엔티티)이 일치하는 "안읽은" 알림만 읽음 처리하고,
 * 다른 사용자·다른 방·이미 읽은 알림은 건드리지 않는지 확인한다.
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
        "spring.datasource.url=jdbc:h2:mem:notifmarkread;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class NotificationRepositoryMarkReadByRelatedEntityTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification save(String recipientUserId, Long relatedEntityId, String relatedEntityType, boolean isRead) {
        return notificationRepository.save(Notification.builder()
                .title("제목")
                .message("내용")
                .recipientToken("token")
                .recipientUserId(recipientUserId)
                .recipientUserName("이름")
                .type(Notification.NotificationType.CHAT)
                .relatedEntityId(relatedEntityId)
                .relatedEntityType(relatedEntityType)
                .sent(true)
                .isRead(isRead)
                .build());
    }

    @Test
    @DisplayName("같은 방·같은 수신자의 안읽은 알림만 읽음 처리되고 나머지는 그대로다")
    void onlyMatchingUnreadNotificationsAreMarkedRead() {
        Notification target = save("3", 100L, "chatRoom", false);
        Notification alreadyRead = save("3", 100L, "chatRoom", true);
        Notification otherUser = save("admin_3", 100L, "chatRoom", false);
        Notification otherRoom = save("3", 200L, "chatRoom", false);
        Notification otherType = save("3", 100L, "approval", false);

        int updated = notificationRepository.markReadByRelatedEntity("3", 100L, "chatRoom", LocalDateTime.now());

        assertThat(updated).isEqualTo(1);

        assertThat(notificationRepository.findById(target.getId()).orElseThrow().getIsRead()).isTrue();
        assertThat(notificationRepository.findById(target.getId()).orElseThrow().getReadAt()).isNotNull();

        assertThat(notificationRepository.findById(otherUser.getId()).orElseThrow().getIsRead()).isFalse();
        assertThat(notificationRepository.findById(otherRoom.getId()).orElseThrow().getIsRead()).isFalse();
        assertThat(notificationRepository.findById(otherType.getId()).orElseThrow().getIsRead()).isFalse();
        // 이미 읽은 건 다시 건드리지 않는다는 전제(대상 조건에도 isRead=false가 들어있다) 확인용으로 그대로 true
        assertThat(notificationRepository.findById(alreadyRead.getId()).orElseThrow().getIsRead()).isTrue();
    }
}
