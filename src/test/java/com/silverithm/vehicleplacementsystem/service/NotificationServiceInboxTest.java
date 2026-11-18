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
    private final ChatCallerResolver callers = mock(ChatCallerResolver.class);
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, fcm, callers);
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

    // ─── 알림을 일으킨 사람(2026-11-18) — 웹 알림 카드가 그 사람 사진을 붙인다 ───

    private Notification savedOne() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(0);
    }

    @Test
    @DisplayName("보내는 쪽이 정하지 않으면 요청을 보낸 로그인 사용자가 보낸 사람이다")
    void 보낸사람_로그인사용자() {
        when(callers.currentChatUserId()).thenReturn("12");

        service.sendAndSaveNotification(결재알림(""));

        assertThat(savedOne().getActorId()).isEqualTo("12");
    }

    @Test
    @DisplayName("보내는 쪽이 정한 값이 먼저다 — 빈 문자열이면 일부러 비운다")
    void 보낸사람_직접지정() {
        when(callers.currentChatUserId()).thenReturn("12");
        FCMNotificationRequestDTO named = 결재알림("");
        named.setActorId("admin_3");
        FCMNotificationRequestDTO none = 결재알림("");
        none.setActorId("");

        service.sendAndSaveNotification(named);
        service.sendAndSaveNotification(none);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getActorId).containsExactly("admin_3", null);
    }

    @Test
    @DisplayName("리마인더처럼 로그인 사용자가 없거나 찾다 실패해도 알림은 남는다 — 보낸 사람만 비어 있다")
    void 보낸사람_없음() {
        when(callers.currentChatUserId()).thenThrow(new IllegalStateException("db down"));

        NotificationDTO saved = service.sendAndSaveNotification(결재알림(""));

        assertThat(saved.getRecipientUserId()).isEqualTo("admin_3");
        assertThat(savedOne().getActorId()).isNull();
    }

    @Test
    @DisplayName("푸시 data에도 보낸 사람을 싣는다 — 원래 Map(불변)은 그대로 두고 새 Map으로")
    void 푸시에도_보낸사람() {
        when(callers.currentChatUserId()).thenReturn("admin_3");
        when(fcm.sendNotification(eq("device-token"), anyString(), anyString(), anyMap())).thenReturn("fcm-1");

        service.sendAndSaveNotification(결재알림("device-token"));

        verify(fcm).sendNotification(eq("device-token"), anyString(), anyString(),
                eq(Map.of("type", "approval", "actorId", "admin_3")));
    }

    @Test
    @DisplayName("휴가 신청 알림의 보낸 사람은 신청한 직원이다 — 시설장이 대신 넣어도 시설장 얼굴이 붙지 않는다")
    void 휴가신청_보낸사람은_신청자() {
        when(callers.currentChatUserId()).thenReturn("admin_3"); // 대신 넣은 시설장

        service.sendVacationSubmittedNotification("", "admin_3", "김도형", "한희정(조리원)", "2026-12-26", 5659L,
                VacationService.applicantChatId(" 41 "));
        service.sendVacationSubmittedNotification("", "admin_3", "김도형", "임시", "2026-12-26", 5660L,
                VacationService.applicantChatId("user_1700000000000"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getActorId).containsExactly("41", null);
    }
}
