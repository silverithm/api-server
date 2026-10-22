package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.NotificationDTO;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.repository.NotificationRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 알림함에는 늘 남기고, 푸시는 기기가 있을 때만 — 2026-10-22 원장 "알림이 중구난방(언제는 뜨고 언제는 안 뜬다)".
 *
 * 예전에는 보내는 쪽마다 토큰이 없으면 이 메서드를 부르지도 않아, 앱을 안 쓰는 사람은 웹 알림이 하나도 없었다.
 */
class NotificationServiceInboxTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final FCMService fcm = mock(FCMService.class);
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, fcm);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static FCMNotificationRequestDTO 결재알림(String token) {
        return FCMNotificationRequestDTO.builder()
                .recipientToken(token)
                .title("결재 요청 도착")
                .message("이수나님의 '퇴소 체크리스트' 결재가 결재를 기다리고 있습니다.")
                .recipientUserId("admin_3")
                .recipientUserName("김도형")
                .type("approval")
                .relatedEntityId(990L)
                .relatedEntityType("approval_request")
                .data(Map.of("type", "approval"))
                .build();
    }

    @Test
    @DisplayName("기기가 없어도 알림함에 남는다 — 푸시는 보내지 않고, 실패로 적지도 않는다")
    void 기기없으면알림함에만() {
        for (String token : new String[]{null, "", "  "}) {
            NotificationDTO saved = service.sendAndSaveNotification(결재알림(token));

            assertThat(saved.getRecipientUserId()).isEqualTo("admin_3");
            assertThat(saved.getType()).isEqualTo("approval");
            assertThat(saved.getErrorMessage()).isNull();
        }
        verify(fcm, never()).sendNotification(anyString(), anyString(), anyString(), anyMap());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        // recipient_token 칸은 NOT NULL — 빈 문자열로 남긴다
        assertThat(captor.getAllValues()).allSatisfy(n -> assertThat(n.getRecipientToken()).isNotNull());
    }

    @Test
    @DisplayName("기기가 있으면 알림함에 남기고 푸시도 보낸다")
    void 기기있으면푸시도() {
        when(fcm.sendNotification(eq("device-token"), anyString(), anyString(), anyMap())).thenReturn("fcm-1");

        NotificationDTO saved = service.sendAndSaveNotification(결재알림("device-token"));

        verify(fcm).sendNotification(eq("device-token"), eq("결재 요청 도착"), anyString(), anyMap());
        assertThat(saved.getSent()).isTrue();
        assertThat(saved.getFcmMessageId()).isEqualTo("fcm-1");
    }
}
