package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.VacationDeadlineSetting;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationDeadlineSettingRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationRequestRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 휴무 입력 마감 임박 리마인더(10:00) — 아직 입력하지 않은 직원이 기기가 없어도 웹 알림함에서 본다.
 */
class VacationDeadlinePreReminderSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final VacationDeadlineSettingRepository settingRepository = mock(VacationDeadlineSettingRepository.class);
    private final VacationDeadlineDateService deadlineDateService = mock(VacationDeadlineDateService.class);
    private final VacationRequestRepository requestRepository = mock(VacationRequestRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    private final Company company = mock(Company.class);
    private VacationDeadlinePreReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new VacationDeadlinePreReminderScheduler(settingRepository, deadlineDateService,
                requestRepository, companyRepository, memberRepository, notificationService, redisUtils);
        when(redisUtils.tryAcquireDailySchedulerLock(anyString(), anyInt())).thenReturn(true);

        VacationDeadlineSetting setting = mock(VacationDeadlineSetting.class);
        when(setting.getCompanyId()).thenReturn(1L);
        when(setting.getDeadlineDay()).thenReturn(25);
        when(settingRepository.findByEnabledTrue()).thenReturn(List.of(setting));
        // 마감이 내일 — D-1
        when(deadlineDateService.resolveDeadline(eq(1L), any(YearMonth.class), any())).thenReturn(today().plusDays(1));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(requestRepository.findByCompanyAndDateBetween(eq(company), any(), any())).thenReturn(List.of());
    }

    private static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    private static Member member(long id, String name, String token) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getName()).thenReturn(name);
        when(member.getFcmToken()).thenReturn(token);
        return member;
    }

    private void 직원들(Member... members) {
        org.mockito.Mockito.doReturn(List.of(members)).when(memberRepository)
                .findByCompanyAndStatus(company, Member.MemberStatus.ACTIVE);
    }

    private List<FCMNotificationRequestDTO> 보낸알림() {
        ArgumentCaptor<FCMNotificationRequestDTO> captor = ArgumentCaptor.forClass(FCMNotificationRequestDTO.class);
        verify(notificationService, org.mockito.Mockito.atLeast(0)).sendAndSaveNotification(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("기기 없는 직원도 알림함에 남고 기기 있는 직원은 토큰이 실린다 — 종류·데이터가 맞다")
    void 토큰없어도알림함에() {
        직원들(member(7L, "이수나", null), member(8L, "김보경", "device-8"));

        scheduler.remindUpcomingDeadline();

        List<FCMNotificationRequestDTO> sent = 보낸알림();
        assertThat(sent).hasSize(2);
        assertThat(sent).allSatisfy(dto -> {
            assertThat(dto.getType()).isEqualTo("VACATION_REMINDER");
            assertThat(dto.getRelatedEntityType()).isEqualTo("vacation_deadline_reminder");
            assertThat(dto.getRelatedEntityId()).isNull();
            assertThat(dto.getData()).containsEntry("type", "vacation_deadline_reminder")
                    .containsKeys("targetMonth", "deadline");
            assertThat(dto.getData()).isInstanceOf(Map.class);
            assertThat(dto.getTitle()).contains("D-1");
        });
        FCMNotificationRequestDTO noToken = sent.stream().filter(d -> d.getRecipientUserId().equals("7")).findFirst()
                .orElseThrow();
        assertThat(noToken.getRecipientToken()).isBlank();
        assertThat(noToken.getRecipientUserName()).isEqualTo("이수나");
        assertThat(sent.stream().filter(d -> d.getRecipientUserId().equals("8")).findFirst().orElseThrow()
                .getRecipientToken()).isEqualTo("device-8");
    }

    @Test
    @DisplayName("이미 다음 달 휴무를 입력한 직원에게는 보내지 않는다")
    void 이미입력한직원은제외() {
        VacationRequest done = VacationRequest.builder().date(today().plusMonths(1)).userId("7").userName("이수나")
                .status(VacationRequest.VacationStatus.APPROVED).build();
        when(requestRepository.findByCompanyAndDateBetween(eq(company), any(), any())).thenReturn(List.of(done));
        직원들(member(7L, "이수나", null), member(8L, "김보경", null));

        scheduler.remindUpcomingDeadline();

        assertThat(보낸알림()).extracting(FCMNotificationRequestDTO::getRecipientUserId).containsExactly("8");
    }

    @Test
    @DisplayName("오늘 시작 전에 만든 안읽은 마감 임박 알림을 먼저 읽음 처리한다")
    void 지난알림정리() {
        직원들();

        scheduler.remindUpcomingDeadline();

        verify(notificationService).retireStaleReminders("vacation_deadline_reminder", today().atStartOfDay());
    }

    @Test
    @DisplayName("다른 서버가 이미 잠금을 잡았으면 정리도 전송도 조회도 하지 않는다")
    void 잠금실패면아무것도안한다() {
        when(redisUtils.tryAcquireDailySchedulerLock("vacation-deadline-pre-reminder", 120)).thenReturn(false);

        scheduler.remindUpcomingDeadline();

        verifyNoInteractions(notificationService, settingRepository, requestRepository, memberRepository);
    }

    @Test
    @DisplayName("한 명이 실패해도 나머지에게는 간다")
    void 한명실패해도나머지는간다() {
        직원들(member(7L, "가", null), member(8L, "나", null), member(9L, "다", null));
        doThrow(new RuntimeException("DB 오류")).when(notificationService)
                .sendAndSaveNotification(argThat(dto -> dto != null && "8".equals(dto.getRecipientUserId())));

        scheduler.remindUpcomingDeadline();

        verify(notificationService, times(3)).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("지난 알림 정리가 실패해도 오늘 알림은 보낸다")
    void 정리실패해도보낸다() {
        직원들(member(7L, "가", null));
        doThrow(new RuntimeException("정리 실패")).when(notificationService).retireStaleReminders(anyString(), any());

        scheduler.remindUpcomingDeadline();

        verify(notificationService).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("마감이 아직 멀면 보내지 않는다 — 그래도 지난 알림은 정리한다")
    void 마감이멀면안보낸다() {
        when(deadlineDateService.resolveDeadline(eq(1L), any(YearMonth.class), any()))
                .thenReturn(today().plusDays(10));
        직원들(member(7L, "가", null));

        scheduler.remindUpcomingDeadline();

        verify(notificationService, never()).sendAndSaveNotification(any());
        verify(notificationService).retireStaleReminders(eq("vacation_deadline_reminder"), any());
    }
}
