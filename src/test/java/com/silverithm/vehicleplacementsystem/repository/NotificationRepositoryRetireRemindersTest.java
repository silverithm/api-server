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
 * markReadByRelatedEntityTypeCreatedBefore — 같은 종류의 "기준 시각 이전에 만든 안읽은" 알림만 읽음 처리하고,
 * 다른 종류·기준 시각 이후·이미 읽은 알림은 건드리지 않는다.
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
        "spring.datasource.url=jdbc:h2:mem:notifretire;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class NotificationRepositoryRetireRemindersTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification save(String recipient, String entityType, boolean isRead) {
        return notificationRepository.save(Notification.builder()
                .title("제목")
                .message("내용")
                .recipientToken("")
                .recipientUserId(recipient)
                .recipientUserName("이름")
                .type(Notification.NotificationType.GENERAL)
                .relatedEntityId(1L)
                .relatedEntityType(entityType)
                .sent(false)
                .isRead(isRead)
                .build());
    }

    private Notification reload(Notification n) {
        return notificationRepository.findById(n.getId()).orElseThrow();
    }

    @Test
    @DisplayName("같은 종류의 안읽은 알림만 읽음 처리된다 — 사람이 달라도 전부, 다른 종류·이미 읽은 것은 그대로")
    void 같은종류의안읽은것만() {
        Notification member = save("7", "schedule_start_reminder", false);
        Notification admin = save("admin_3", "schedule_start_reminder", false);
        Notification alreadyRead = save("7", "schedule_start_reminder", true);
        Notification otherKind = save("7", "schedule_task_reminder", false);
        Notification assignment = save("7", "work_task", false);
        notificationRepository.flush();

        // createdAt은 저장 시각으로 찍히므로 기준 시각을 미래로 잡아 "지난 날 것"으로 만든다
        LocalDateTime readAt = LocalDateTime.now();
        int updated = notificationRepository.markReadByRelatedEntityTypeCreatedBefore(
                "schedule_start_reminder", LocalDateTime.now().plusHours(1), readAt);

        assertThat(updated).isEqualTo(2);
        assertThat(reload(member).getIsRead()).isTrue();
        assertThat(reload(member).getReadAt()).isNotNull();
        assertThat(reload(admin).getIsRead()).isTrue();
        assertThat(reload(alreadyRead).getReadAt()).isNull(); // 이미 읽은 것의 읽은 시각은 덮어쓰지 않는다
        assertThat(reload(otherKind).getIsRead()).isFalse();
        assertThat(reload(assignment).getIsRead()).isFalse();
    }

    @Test
    @DisplayName("기준 시각 이후에 만든 알림(오늘 것)은 건드리지 않는다")
    void 기준시각이후는그대로() {
        Notification today = save("7", "vacation_adjustment", false);
        notificationRepository.flush();

        int updated = notificationRepository.markReadByRelatedEntityTypeCreatedBefore(
                "vacation_adjustment", LocalDateTime.now().minusHours(1), LocalDateTime.now());

        assertThat(updated).isZero();
        assertThat(reload(today).getIsRead()).isFalse();
    }
}
