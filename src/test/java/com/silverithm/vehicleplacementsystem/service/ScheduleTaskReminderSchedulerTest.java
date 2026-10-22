package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 마감 지난 미완료 업무 아침 알림은 알림함에 남기고, 매시간 도는 '오늘 미수행 일정' 알림은 푸시만 보낸다.
 */
class ScheduleTaskReminderSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ScheduleTaskRepository taskRepository = mock(ScheduleTaskRepository.class);
    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FCMService fcmService = mock(FCMService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    private ScheduleTaskReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ScheduleTaskReminderScheduler(taskRepository, scheduleRepository, memberRepository,
                userRepository, fcmService, notificationService, redisUtils);
        when(redisUtils.tryAcquireDailySchedulerLock(anyString(), anyInt())).thenReturn(true);
    }

    private static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    private static Schedule schedule(Long id) {
        return Schedule.builder().id(id).title("소방 점검").startDate(today().minusDays(3))
                .endDate(today().minusDays(1)).build();
    }

    private static ScheduleTask task(Long id, Schedule schedule, Long assigneeId) {
        return ScheduleTask.builder().id(id).schedule(schedule).content("점검표 작성")
                .assigneeMemberId(assigneeId).build();
    }

    private static Member member(long id, String name, String token) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getName()).thenReturn(name);
        when(member.getFcmToken()).thenReturn(token);
        return member;
    }

    private void 담당자(long id, Member member) {
        org.mockito.Mockito.doReturn(Optional.ofNullable(member)).when(memberRepository).findById(id);
    }

    private List<FCMNotificationRequestDTO> 보낸알림() {
        ArgumentCaptor<FCMNotificationRequestDTO> captor = ArgumentCaptor.forClass(FCMNotificationRequestDTO.class);
        verify(notificationService, org.mockito.Mockito.atLeast(0)).sendAndSaveNotification(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("기기 없는 담당자도 알림함에 남는다 — 일정 id·종류·데이터가 맞다")
    void 토큰없는담당자도알림함에() {
        Schedule schedule = schedule(70L);
        when(taskRepository.findOverdueByScheduleEndDate(today().minusDays(1)))
                .thenReturn(List.of(task(1L, schedule, 7L)));
        담당자(7L, member(7L, "김요양", null));

        scheduler.notifyOverdueTasks();

        assertThat(보낸알림()).singleElement().satisfies(dto -> {
            assertThat(dto.getRecipientToken()).isBlank();
            assertThat(dto.getRecipientUserId()).isEqualTo("7");
            assertThat(dto.getRecipientUserName()).isEqualTo("김요양");
            assertThat(dto.getType()).isEqualTo("GENERAL");
            assertThat(dto.getRelatedEntityId()).isEqualTo(70L);
            assertThat(dto.getRelatedEntityType()).isEqualTo("schedule_task_reminder");
            assertThat(dto.getTitle()).isEqualTo("완료하지 않은 업무가 있습니다");
            assertThat(dto.getMessage()).isEqualTo("소방 점검 - 점검표 작성");
            assertThat(dto.getData()).isEqualTo(ScheduleService.scheduleNotificationData(schedule));
        });
        // 푸시는 NotificationService가 알림함에 저장하면서 판단한다 — 여기서 직접 보내면 안 된다
        verifyNoInteractions(fcmService);
    }

    @Test
    @DisplayName("기기가 있으면 토큰이 실려 푸시도 간다")
    void 토큰있으면토큰이실린다() {
        when(taskRepository.findOverdueByScheduleEndDate(today().minusDays(1)))
                .thenReturn(List.of(task(1L, schedule(70L), 7L)));
        담당자(7L, member(7L, "김요양", "device-7"));

        scheduler.notifyOverdueTasks();

        assertThat(보낸알림()).singleElement()
                .satisfies(dto -> assertThat(dto.getRecipientToken()).isEqualTo("device-7"));
    }

    @Test
    @DisplayName("담당 직원을 찾을 수 없으면 건너뛴다")
    void 없는직원은건너뛴다() {
        when(taskRepository.findOverdueByScheduleEndDate(today().minusDays(1)))
                .thenReturn(List.of(task(1L, schedule(70L), 7L)));
        담당자(7L, null);

        scheduler.notifyOverdueTasks();

        verify(notificationService, never()).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("오늘 시작 전에 만든 안읽은 마감 미완료 알림을 먼저 읽음 처리한다 — 대상이 없는 날에도")
    void 지난알림정리() {
        when(taskRepository.findOverdueByScheduleEndDate(today().minusDays(1))).thenReturn(List.of());

        scheduler.notifyOverdueTasks();

        verify(notificationService).retireStaleReminders("schedule_task_reminder", today().atStartOfDay());
        verify(notificationService, never()).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("다른 서버가 이미 잠금을 잡았으면 정리도 전송도 조회도 하지 않는다")
    void 잠금실패면아무것도안한다() {
        when(redisUtils.tryAcquireDailySchedulerLock("schedule-task-overdue-reminder", 120)).thenReturn(false);

        scheduler.notifyOverdueTasks();

        verifyNoInteractions(notificationService, taskRepository, memberRepository, fcmService);
    }

    @Test
    @DisplayName("한 명이 실패해도 나머지에게는 간다")
    void 한명실패해도나머지는간다() {
        Schedule schedule = schedule(70L);
        when(taskRepository.findOverdueByScheduleEndDate(today().minusDays(1)))
                .thenReturn(List.of(task(1L, schedule, 7L), task(2L, schedule, 8L), task(3L, schedule, 9L)));
        담당자(7L, member(7L, "가", null));
        담당자(8L, member(8L, "나", null));
        담당자(9L, member(9L, "다", null));
        doThrow(new RuntimeException("DB 오류")).when(notificationService)
                .sendAndSaveNotification(argThat(dto -> dto != null && "8".equals(dto.getRecipientUserId())));

        scheduler.notifyOverdueTasks();

        verify(notificationService, times(3)).sendAndSaveNotification(any());
        assertThat(보낸알림()).extracting(FCMNotificationRequestDTO::getRecipientUserId).containsExactly("7", "8", "9");
    }

    @Test
    @DisplayName("지난 알림 정리가 실패해도 오늘 알림은 보낸다")
    void 정리실패해도보낸다() {
        when(taskRepository.findOverdueByScheduleEndDate(today().minusDays(1)))
                .thenReturn(List.of(task(1L, schedule(70L), 7L)));
        담당자(7L, member(7L, "김요양", null));
        doThrow(new RuntimeException("정리 실패")).when(notificationService).retireStaleReminders(anyString(), any());

        scheduler.notifyOverdueTasks();

        verify(notificationService).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("매시간 도는 '오늘 미수행 일정' 알림은 알림함에 남기지 않고 푸시만 보낸다 — 하루 열 번이 스팸이라서")
    void 매시간알림은푸시만() {
        Schedule schedule = Schedule.builder().id(80L).title("오늘 회의").startDate(today())
                .managerMemberId(7L).managerType(Schedule.ManagerType.MEMBER).build();
        when(scheduleRepository.findTodayIncompleteWithManager(any())).thenReturn(List.of(schedule));
        Member manager = member(7L, "김요양", "device-7");
        org.mockito.Mockito.doReturn(Optional.of(manager)).when(memberRepository).findById(7L);

        scheduler.notifyIncompleteTodaySchedules();

        verify(fcmService).sendNotification(eq("device-7"), eq("오늘 일정이 아직 완료되지 않았습니다"), eq("오늘 회의"),
                anyMap());
        verifyNoInteractions(notificationService, redisUtils);
    }
}
