package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.repository.WorkTaskRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 업무진행도 매일 요약 — 알림함 종류는 "work_task_daily", 담당 지정·완료 알림은 "work_task".
 * 종류를 갈라둔 이유: 다음 날 아침에 지난 요약만 읽음 처리해야 하는데 종류가 같으면 아직 안 본 담당 지정 알림까지 사라진다.
 */
class WorkTaskReminderSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WorkTaskRepository tasks = mock(WorkTaskRepository.class);
    private final WorkTaskNotifier notifier = mock(WorkTaskNotifier.class);
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private WorkTaskReminderScheduler scheduler;

    private final WorkTaskPeople people = mock(WorkTaskPeople.class);
    private final NotificationService notifierBackend = mock(NotificationService.class);
    private WorkTaskNotifier realNotifier;

    @BeforeEach
    void setUp() {
        scheduler = new WorkTaskReminderScheduler(tasks, notifier, redisUtils, notificationService);
        when(redisUtils.tryAcquireDailySchedulerLock(anyString(), anyInt())).thenReturn(true);
        realNotifier = new WorkTaskNotifier(notifierBackend, people, mock(PlatformTransactionManager.class));
    }

    private static boolean 일요일() {
        return LocalDate.now(SEOUL).getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    @Test
    @DisplayName("매일 요약 전에 오늘 이전의 안읽은 요약만 정리한다 — 종류는 work_task_daily")
    void 지난요약정리() {
        Assumptions.assumeFalse(일요일(), "일요일에는 돌지 않는다");
        when(tasks.findAllInProgress()).thenReturn(List.of());

        scheduler.remindDaily();

        verify(notificationService).retireStaleReminders("work_task_daily",
                LocalDate.now(SEOUL).atStartOfDay());
        verify(notifier).sendNow(List.of());
    }

    @Test
    @DisplayName("정리가 알림 전송보다 먼저다")
    void 정리가먼저() {
        Assumptions.assumeFalse(일요일(), "일요일에는 돌지 않는다");
        when(tasks.findAllInProgress()).thenReturn(List.of());

        scheduler.remindDaily();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(notificationService, notifier);
        order.verify(notificationService).retireStaleReminders(anyString(), any());
        order.verify(notifier).sendNow(any());
    }

    @Test
    @DisplayName("다른 서버가 이미 잠금을 잡았으면 정리도 전송도 하지 않는다")
    void 잠금실패면아무것도안한다() {
        Assumptions.assumeFalse(일요일(), "일요일에는 돌지 않는다");
        when(redisUtils.tryAcquireDailySchedulerLock("work-task-reminder", 120)).thenReturn(false);

        scheduler.remindDaily();

        verifyNoInteractions(notificationService, notifier, tasks);
    }

    @Test
    @DisplayName("일요일에는 정리도 전송도 하지 않는다")
    void 일요일은건너뛴다() {
        Assumptions.assumeTrue(일요일(), "일요일에만 확인한다");

        scheduler.remindDaily();

        verifyNoInteractions(notificationService, notifier, tasks);
    }

    @Test
    @DisplayName("지난 요약 정리가 실패해도 오늘 요약은 보낸다")
    void 정리실패해도보낸다() {
        Assumptions.assumeFalse(일요일(), "일요일에는 돌지 않는다");
        when(tasks.findAllInProgress()).thenReturn(List.of());
        doThrow(new RuntimeException("정리 실패")).when(notificationService).retireStaleReminders(anyString(), any());

        scheduler.remindDaily();

        verify(notifier).sendNow(any());
    }

    // ---- WorkTaskNotifier: 종류가 알림함 행까지 그대로 간다

    @Test
    @DisplayName("매일 요약은 기기가 있으면 work_task_daily로 알림함 저장+푸시, 푸시 데이터는 그대로(type=work_task)")
    void 요약_기기있음() {
        when(people.fcmTokenOf("7")).thenReturn(Optional.of("device-7"));

        realNotifier.sendNow(List.of(WorkTaskNotifier.Message.daily("7", "이수나", "제목", "내용", 99L)));

        ArgumentCaptor<FCMNotificationRequestDTO> captor = ArgumentCaptor.forClass(FCMNotificationRequestDTO.class);
        verify(notifierBackend).sendAndSaveNotification(captor.capture());
        FCMNotificationRequestDTO dto = captor.getValue();
        assertThat(dto.getRelatedEntityType()).isEqualTo("work_task_daily");
        assertThat(dto.getType()).isEqualTo("work_task");
        assertThat(dto.getRelatedEntityId()).isEqualTo(99L);
        assertThat(dto.getRecipientToken()).isEqualTo("device-7");
        assertThat(dto.getData()).isEqualTo(Map.of("type", "work_task", "taskId", "99"));
    }

    @Test
    @DisplayName("매일 요약은 기기가 없어도 work_task_daily로 알림함에 남는다 — 종류·타입이 기기 있는 경우와 같다")
    void 요약_기기없음() {
        when(people.fcmTokenOf("admin_3")).thenReturn(Optional.empty());

        int saved = realNotifier.sendNow(List.of(WorkTaskNotifier.Message.daily("admin_3", "김도형", "제목", "내용", 99L)));

        assertThat(saved).isEqualTo(1);
        verify(notifierBackend).saveNotification(eq("제목"), eq("내용"), eq(""), eq("admin_3"), eq("김도형"),
                eq(Notification.NotificationType.WORK_TASK), eq(99L), eq("work_task_daily"));
        verify(notifierBackend, never()).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("담당 지정·완료 알림은 그대로 work_task — 기기 있음/없음 모두")
    void 담당지정은work_task() {
        when(people.fcmTokenOf("7")).thenReturn(Optional.of("device-7"));
        when(people.fcmTokenOf("8")).thenReturn(Optional.empty());

        realNotifier.sendNow(List.of(
                new WorkTaskNotifier.Message("7", "이수나", "담당 지정", "내용", 5L),
                new WorkTaskNotifier.Message("8", "김보경", "담당 지정", "내용", 5L)));

        ArgumentCaptor<FCMNotificationRequestDTO> captor = ArgumentCaptor.forClass(FCMNotificationRequestDTO.class);
        verify(notifierBackend).sendAndSaveNotification(captor.capture());
        assertThat(captor.getValue().getRelatedEntityType()).isEqualTo("work_task");
        verify(notifierBackend).saveNotification(anyString(), anyString(), eq(""), eq("8"), anyString(),
                eq(Notification.NotificationType.WORK_TASK), eq(5L), eq("work_task"));
    }

    @Test
    @DisplayName("Message 종류 — 5개 인자 생성자는 work_task, daily()는 work_task_daily")
    void 메시지종류() {
        assertThat(new WorkTaskNotifier.Message("7", "가", "t", "b", 1L).relatedEntityType()).isEqualTo("work_task");
        assertThat(WorkTaskNotifier.Message.daily("7", "가", "t", "b", 1L).relatedEntityType())
                .isEqualTo("work_task_daily");
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
