package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.VacationDeadlineSetting;
import com.silverithm.vehicleplacementsystem.entity.VacationLimit;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationDeadlineSettingRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationLimitRepository;
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
 * 휴무 조정 리마인더(09:30) — 앱이 없는 직원도 웹 알림함에 남고, 같은 (직원, 날짜)에는 하루 한 번만 간다.
 */
class VacationAdjustmentReminderSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final VacationDeadlineSettingRepository settingRepository = mock(VacationDeadlineSettingRepository.class);
    private final VacationDeadlineDateService deadlineDateService = mock(VacationDeadlineDateService.class);
    private final VacationLimitRepository limitRepository = mock(VacationLimitRepository.class);
    private final VacationRequestRepository requestRepository = mock(VacationRequestRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    private final Company company = mock(Company.class);
    private VacationAdjustmentReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new VacationAdjustmentReminderScheduler(settingRepository, deadlineDateService, limitRepository,
                requestRepository, companyRepository, memberRepository, notificationService, redisUtils);
        when(redisUtils.tryAcquireDailySchedulerLock(anyString(), anyInt())).thenReturn(true);

        VacationDeadlineSetting setting = mock(VacationDeadlineSetting.class);
        when(setting.getCompanyId()).thenReturn(1L);
        when(setting.getDeadlineDay()).thenReturn(10);
        when(settingRepository.findByEnabledTrue()).thenReturn(List.of(setting));
        // 마감일이 어제 — 이미 마감이 지났다
        when(deadlineDateService.resolveDeadline(eq(1L), any(YearMonth.class), any())).thenReturn(today().minusDays(1));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
    }

    private static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    private static VacationLimit limit(LocalDate date, String role, int max) {
        return VacationLimit.builder().date(date).role(role).maxPeople(max).build();
    }

    private static VacationRequest request(LocalDate date, long memberId, String role) {
        return VacationRequest.builder().date(date).userId(String.valueOf(memberId)).userName("직원" + memberId)
                .role(role).status(VacationRequest.VacationStatus.PENDING).build();
    }

    private Member member(long id, String token) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getName()).thenReturn("직원" + id);
        when(member.getFcmToken()).thenReturn(token);
        doReturn(Optional.of(member)).when(memberRepository).findById(id);
        return member;
    }

    private List<FCMNotificationRequestDTO> 보낸알림() {
        ArgumentCaptor<FCMNotificationRequestDTO> captor = ArgumentCaptor.forClass(FCMNotificationRequestDTO.class);
        verify(notificationService, org.mockito.Mockito.atLeast(0)).sendAndSaveNotification(captor.capture());
        return captor.getAllValues();
    }

    private void 초과상황(List<VacationLimit> limits, List<VacationRequest> requests) {
        when(limitRepository.findByCompanyAndDateBetween(eq(company), any(), any())).thenReturn(limits);
        when(requestRepository.findByCompanyAndDateBetween(eq(company), any(), any())).thenReturn(requests);
        when(memberRepository.findByCompanyOrderByCreatedAtDesc(company)).thenReturn(List.of());
    }

    @Test
    @DisplayName("기기 없는 직원도 알림함에 남고 기기 있는 직원은 토큰이 실린다 — 종류·데이터가 맞다")
    void 토큰없어도알림함에() {
        LocalDate date = today().plusDays(2);
        member(7L, null);
        member(8L, "device-8");
        초과상황(List.of(limit(date, "all", 1)), List.of(request(date, 7L, "caregiver"), request(date, 8L, "caregiver")));

        scheduler.remindUnresolvedVacationOverages();

        List<FCMNotificationRequestDTO> sent = 보낸알림();
        assertThat(sent).hasSize(2);
        assertThat(sent).extracting(FCMNotificationRequestDTO::getRecipientUserId).containsExactlyInAnyOrder("7", "8");
        assertThat(sent).allSatisfy(dto -> {
            assertThat(dto.getType()).isEqualTo("VACATION_REMINDER");
            assertThat(dto.getRelatedEntityType()).isEqualTo("vacation_adjustment");
            assertThat(dto.getRelatedEntityId()).isNull();
            assertThat(dto.getTitle()).isEqualTo("휴무 조정이 필요합니다");
            assertThat(dto.getData()).isEqualTo(Map.of("type", "vacation_adjustment", "date", date.toString()));
        });
        assertThat(sent.stream().filter(d -> d.getRecipientUserId().equals("7")).findFirst().orElseThrow()
                .getRecipientToken()).isBlank();
        assertThat(sent.stream().filter(d -> d.getRecipientUserId().equals("8")).findFirst().orElseThrow()
                .getRecipientToken()).isEqualTo("device-8");
    }

    @Test
    @DisplayName("같은 날 직종 한도와 전체 한도가 모두 초과돼도 (직원, 날짜)에는 한 번만 간다 — 토큰이 없는 직원도")
    void 직원날짜당한번() {
        LocalDate date = today().plusDays(2);
        member(7L, null);
        member(8L, "device-8");
        초과상황(List.of(limit(date, "all", 1), limit(date, "caregiver", 1)),
                List.of(request(date, 7L, "caregiver"), request(date, 8L, "caregiver")));

        scheduler.remindUnresolvedVacationOverages();

        assertThat(보낸알림()).extracting(FCMNotificationRequestDTO::getRecipientUserId)
                .containsExactlyInAnyOrder("7", "8");
    }

    @Test
    @DisplayName("날짜가 다르면 같은 직원에게도 날짜마다 따로 간다")
    void 날짜가다르면따로() {
        LocalDate first = today().plusDays(2);
        LocalDate second = today().plusDays(3);
        member(7L, null);
        member(8L, null);
        초과상황(List.of(limit(first, "all", 1), limit(second, "all", 1)),
                List.of(request(first, 7L, "caregiver"), request(first, 8L, "caregiver"),
                        request(second, 7L, "caregiver"), request(second, 8L, "caregiver")));

        scheduler.remindUnresolvedVacationOverages();

        assertThat(보낸알림()).hasSize(4);
    }

    @Test
    @DisplayName("제한 이하면 아무에게도 보내지 않는다")
    void 제한이하면안보낸다() {
        LocalDate date = today().plusDays(2);
        member(7L, null);
        초과상황(List.of(limit(date, "all", 2)), List.of(request(date, 7L, "caregiver")));

        scheduler.remindUnresolvedVacationOverages();

        verify(notificationService, never()).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("오늘 시작 전에 만든 안읽은 휴무 조정 알림을 먼저 읽음 처리한다")
    void 지난알림정리() {
        초과상황(List.of(), List.of());

        scheduler.remindUnresolvedVacationOverages();

        verify(notificationService).retireStaleReminders("vacation_adjustment", today().atStartOfDay());
    }

    @Test
    @DisplayName("다른 서버가 이미 잠금을 잡았으면 정리도 전송도 조회도 하지 않는다")
    void 잠금실패면아무것도안한다() {
        when(redisUtils.tryAcquireDailySchedulerLock("vacation-adjustment-reminder", 120)).thenReturn(false);

        scheduler.remindUnresolvedVacationOverages();

        verifyNoInteractions(notificationService, settingRepository, limitRepository, requestRepository,
                memberRepository);
    }

    @Test
    @DisplayName("한 명이 실패해도 나머지에게는 간다")
    void 한명실패해도나머지는간다() {
        LocalDate date = today().plusDays(2);
        member(7L, null);
        member(8L, null);
        member(9L, null);
        초과상황(List.of(limit(date, "all", 1)),
                List.of(request(date, 7L, "caregiver"), request(date, 8L, "caregiver"),
                        request(date, 9L, "caregiver")));
        doThrow(new RuntimeException("DB 오류")).when(notificationService)
                .sendAndSaveNotification(argThat(dto -> dto != null && "8".equals(dto.getRecipientUserId())));

        scheduler.remindUnresolvedVacationOverages();

        verify(notificationService, times(3)).sendAndSaveNotification(any());
    }

    @Test
    @DisplayName("지난 알림 정리가 실패해도 오늘 알림은 보낸다")
    void 정리실패해도보낸다() {
        LocalDate date = today().plusDays(2);
        member(7L, null);
        member(8L, null);
        초과상황(List.of(limit(date, "all", 1)), List.of(request(date, 7L, "caregiver"), request(date, 8L, "caregiver")));
        doThrow(new RuntimeException("정리 실패")).when(notificationService).retireStaleReminders(anyString(), any());

        scheduler.remindUnresolvedVacationOverages();

        verify(notificationService, times(2)).sendAndSaveNotification(any());
    }
}
