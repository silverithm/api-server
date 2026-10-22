package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleParticipant;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 오늘 시작하는 일정 아침 알림 — 앱이 없는 사람도 웹 알림함에 남고(2026-10-22 원장 "새 알림은 전부 알림 하나로"),
 * 지난 날 카드는 오늘 것을 보내기 전에 정리된다.
 */
class ScheduleStartReminderSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final ScheduleParticipantRepository participantRepository = mock(ScheduleParticipantRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    private ScheduleStartReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ScheduleStartReminderScheduler(scheduleRepository, participantRepository, memberRepository,
                userRepository, notificationService, redisUtils);
        when(redisUtils.tryAcquireDailySchedulerLock(anyString(), anyInt())).thenReturn(true);
    }

    private static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    private static Schedule schedule(Long id, Long managerId, Schedule.ManagerType managerType) {
        return Schedule.builder()
                .id(id)
                .title("월례회의")
                .startDate(today())
                .startTime(LocalTime.of(14, 0))
                .managerMemberId(managerId)
                .managerType(managerType)
                .build();
    }

    private static Member member(long id, String name, String token) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getName()).thenReturn(name);
        when(member.getFcmToken()).thenReturn(token);
        return member;
    }

    private static AppUser admin(long id, String name, String token) {
        AppUser admin = mock(AppUser.class);
        when(admin.getId()).thenReturn(id);
        when(admin.getUsername()).thenReturn(name);
        when(admin.getFcmToken()).thenReturn(token);
        return admin;
    }

    private static ScheduleParticipant participant(Schedule schedule, long memberId) {
        return ScheduleParticipant.builder().schedule(schedule).memberId(memberId).build();
    }

    private List<FCMNotificationRequestDTO> 보낸알림() {
        ArgumentCaptor<FCMNotificationRequestDTO> captor = ArgumentCaptor.forClass(FCMNotificationRequestDTO.class);
        verify(notificationService, org.mockito.Mockito.atLeast(0)).sendAndSaveNotification(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("기기 없는 직원도 알림함에 남는다 — 토큰은 빈 문자열, 종류·대상 일정이 맞다")
    void 토큰없는직원도알림함에() {
        Schedule schedule = schedule(55L, null, null);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(schedule));
        when(participantRepository.findByScheduleId(55L)).thenReturn(List.of(participant(schedule, 7L)));
        doReturn(List.of(member(7L, "이수나", null))).when(memberRepository).findAllById(any());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        scheduler.notifyTodayScheduleStart();

        List<FCMNotificationRequestDTO> sent = 보낸알림();
        assertThat(sent).hasSize(1);
        FCMNotificationRequestDTO dto = sent.get(0);
        assertThat(dto.getRecipientToken()).isBlank();
        assertThat(dto.getRecipientUserId()).isEqualTo("7");
        assertThat(dto.getRecipientUserName()).isEqualTo("이수나");
        assertThat(dto.getType()).isEqualTo("GENERAL");
        assertThat(dto.getRelatedEntityId()).isEqualTo(55L);
        assertThat(dto.getRelatedEntityType()).isEqualTo("schedule_start_reminder");
        assertThat(dto.getTitle()).isEqualTo("오늘 일정이 있습니다");
        assertThat(dto.getMessage()).isEqualTo("월례회의 · 14:00");
        // 푸시에 실리는 데이터는 예전 그대로 — 앱이 눌렀을 때 갈 곳을 안다
        assertThat(dto.getData()).isEqualTo(ScheduleService.scheduleNotificationData(schedule));
    }

    @Test
    @DisplayName("기기가 있으면 토큰이 그대로 실려 푸시도 간다(알림함 저장과 푸시는 NotificationService가 한 번에)")
    void 토큰있으면토큰이실린다() {
        Schedule schedule = schedule(55L, null, null);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(schedule));
        when(participantRepository.findByScheduleId(55L)).thenReturn(List.of(participant(schedule, 7L)));
        doReturn(List.of(member(7L, "이수나", "device-7"))).when(memberRepository).findAllById(any());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        scheduler.notifyTodayScheduleStart();

        assertThat(보낸알림()).singleElement().satisfies(dto -> {
            assertThat(dto.getRecipientToken()).isEqualTo("device-7");
            assertThat(dto.getRecipientUserId()).isEqualTo("7");
        });
    }

    @Test
    @DisplayName("관리자 담당자는 admin_<id>로 남는다 — 웹 알림함이 관리자를 그 id로 읽는다")
    void 관리자담당자는admin접두사() {
        Schedule schedule = schedule(56L, 3L, Schedule.ManagerType.ADMIN);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(schedule));
        when(participantRepository.findByScheduleId(56L)).thenReturn(List.of());
        when(memberRepository.findAllById(any())).thenReturn(List.of());
        doReturn(List.of(admin(3L, "김도형", ""))).when(userRepository).findAllById(any());

        scheduler.notifyTodayScheduleStart();

        assertThat(보낸알림()).singleElement().satisfies(dto -> {
            assertThat(dto.getRecipientUserId()).isEqualTo("admin_3");
            assertThat(dto.getRecipientUserName()).isEqualTo("김도형");
            assertThat(dto.getRecipientToken()).isBlank();
            assertThat(dto.getRelatedEntityType()).isEqualTo("schedule_start_reminder");
            assertThat(dto.getRelatedEntityId()).isEqualTo(56L);
        });
    }

    @Test
    @DisplayName("MEMBER 담당자는 members.id로 조회하고 admin 조회에는 섞이지 않는다")
    void 직원담당자는직원id공간() {
        Schedule schedule = schedule(57L, 3L, Schedule.ManagerType.MEMBER);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(schedule));
        when(participantRepository.findByScheduleId(57L)).thenReturn(List.of());
        doReturn(List.of(member(3L, "박직원", "t"))).when(memberRepository).findAllById(any());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        scheduler.notifyTodayScheduleStart();

        assertThat(보낸알림()).singleElement().satisfies(dto -> assertThat(dto.getRecipientUserId()).isEqualTo("3"));
        verify(userRepository).findAllById(argThat(ids -> !ids.iterator().hasNext()));
    }

    @Test
    @DisplayName("오늘 시작 전에 만든 안읽은 일정 시작 알림을 먼저 읽음 처리한다 — 일정이 없는 날에도")
    void 지난알림정리() {
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of());

        scheduler.notifyTodayScheduleStart();

        verify(notificationService).retireStaleReminders("schedule_start_reminder", today().atStartOfDay());
        verify(notificationService, never()).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("정리가 알림 전송보다 먼저다")
    void 정리가먼저() {
        Schedule schedule = schedule(55L, null, null);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(schedule));
        when(participantRepository.findByScheduleId(55L)).thenReturn(List.of(participant(schedule, 7L)));
        doReturn(List.of(member(7L, "이수나", null))).when(memberRepository).findAllById(any());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        scheduler.notifyTodayScheduleStart();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(notificationService);
        order.verify(notificationService).retireStaleReminders(eq("schedule_start_reminder"), any());
        order.verify(notificationService).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("다른 서버가 이미 잠금을 잡았으면 정리도 전송도 조회도 하지 않는다")
    void 잠금실패면아무것도안한다() {
        when(redisUtils.tryAcquireDailySchedulerLock("schedule-start-reminder", 120)).thenReturn(false);

        scheduler.notifyTodayScheduleStart();

        verifyNoInteractions(notificationService, scheduleRepository, participantRepository, memberRepository,
                userRepository);
    }

    @Test
    @DisplayName("한 명이 실패해도 나머지에게는 간다 — 같은 일정의 다음 사람, 다음 일정 모두")
    void 한명실패해도나머지는간다() {
        Schedule first = schedule(55L, null, null);
        Schedule second = schedule(56L, null, null);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(first, second));
        when(participantRepository.findByScheduleId(55L))
                .thenReturn(List.of(participant(first, 1L), participant(first, 2L)));
        when(participantRepository.findByScheduleId(56L)).thenReturn(List.of(participant(second, 3L)));
        when(memberRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.List<Member> found = new java.util.ArrayList<>();
            for (Long id : (Iterable<Long>) inv.getArgument(0)) {
                found.add(member(id, "직원" + id, null));
            }
            return found;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of());
        doThrow(new RuntimeException("DB 오류")).when(notificationService)
                .sendAndSaveNotification(argThat(dto -> dto != null && "1".equals(dto.getRecipientUserId())));

        scheduler.notifyTodayScheduleStart();

        assertThat(보낸알림()).extracting(FCMNotificationRequestDTO::getRecipientUserId)
                .containsExactly("1", "2", "3"); // 1번은 던졌지만 시도됐고 2·3번은 이어서 간다
        verify(notificationService, times(3)).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("지난 알림 정리가 실패해도 오늘 알림은 보낸다")
    void 정리실패해도보낸다() {
        Schedule schedule = schedule(55L, null, null);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(schedule));
        when(participantRepository.findByScheduleId(55L)).thenReturn(List.of(participant(schedule, 7L)));
        doReturn(List.of(member(7L, "이수나", null))).when(memberRepository).findAllById(any());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        doThrow(new RuntimeException("정리 실패")).when(notificationService).retireStaleReminders(anyString(), any());

        scheduler.notifyTodayScheduleStart();

        verify(notificationService).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("참석자이면서 담당자인 직원에게는 한 번만 보낸다")
    void 참석자와담당자겹치면한번() {
        Schedule schedule = schedule(55L, 7L, Schedule.ManagerType.MEMBER);
        when(scheduleRepository.findByStartDateWithNotification(today())).thenReturn(List.of(schedule));
        when(participantRepository.findByScheduleId(55L)).thenReturn(List.of(participant(schedule, 7L)));
        doReturn(List.of(member(7L, "이수나", null))).when(memberRepository).findAllById(argThat(ids -> {
            int count = 0;
            for (Long ignored : ids) {
                count++;
            }
            return count == 1;
        }));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        scheduler.notifyTodayScheduleStart();

        verify(notificationService, times(1)).sendAndSaveNotification(any());
    }
}
