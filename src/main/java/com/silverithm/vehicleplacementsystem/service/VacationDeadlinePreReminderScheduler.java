package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴무 입력 마감 임박 리마인더 — 마감일 3일 전(D-3)부터 마감 당일까지,
 * 다음 달 휴무를 아직 하나도 입력하지 않은 직원에게 매일 입력 요청 푸시를 보낸다.
 *
 * 마감이 지난 뒤의 초과 인원 조정은 {@link VacationAdjustmentReminderScheduler}가 맡는다 —
 * 이 스케줄러는 "입력 자체를 안 한" 직원을 마감 전에 챙기는 반대쪽 절반이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VacationDeadlinePreReminderScheduler {

    private final VacationDeadlineSettingRepository settingRepository;
    private final VacationDeadlineDateService deadlineDateService;
    private final VacationRequestRepository vacationRequestRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final FCMService fcmService;
    private final RedisUtils redisUtils;

    /** 마감 며칠 전부터 알리기 시작하는지 (당일 포함) */
    private static final int REMIND_FROM_DAYS_BEFORE = 3;

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("M월 d일");

    // 마감 지남 조정 알림(09:30)과 겹치지 않게 10시에 보낸다.
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void remindUpcomingDeadline() {
        // 블루그린 이중 기동 대비 — 하루 한 인스턴스만 발송
        if (!redisUtils.tryAcquireDailySchedulerLock("vacation-deadline-pre-reminder", 120)) {
            log.info("[DeadlinePreReminder] 오늘 리마인더는 다른 인스턴스가 이미 실행함 — 스킵");
            return;
        }

        LocalDate today = LocalDate.now();
        List<VacationDeadlineSetting> settings = settingRepository.findByEnabledTrue();
        log.info("[DeadlinePreReminder] 마감 임박 리마인더 시작: 대상 회사 {}곳", settings.size());

        for (VacationDeadlineSetting setting : settings) {
            try {
                LocalDate deadline = deadlineDateService.resolveDeadline(
                        setting.getCompanyId(), YearMonth.from(today), setting.getDeadlineDay());
                if (deadline == null) {
                    continue;
                }
                long daysLeft = ChronoUnit.DAYS.between(today, deadline);
                if (daysLeft < 0 || daysLeft > REMIND_FROM_DAYS_BEFORE) {
                    continue; // 마감이 지났거나 아직 멀었음
                }
                remindCompany(setting.getCompanyId(), today, deadline, daysLeft);
            } catch (Exception e) {
                log.error("[DeadlinePreReminder] 회사 처리 실패: companyId={}, {}",
                        setting.getCompanyId(), e.getMessage());
            }
        }
    }

    private void remindCompany(Long companyId, LocalDate today, LocalDate deadline, long daysLeft) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return;
        }

        // 이번 달 마감의 대상은 "다음 달" 휴무다 (VacationDeadlineSetting 참고)
        YearMonth targetMonth = YearMonth.from(today).plusMonths(1);
        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEnd = targetMonth.atEndOfMonth();

        // 다음 달에 이미 휴무를 입력한 직원 집합 — userId(멤버 id)와 이름 양쪽으로 수집
        // (VacationRequest.userId가 숫자가 아닌 레거시 데이터는 이름으로만 매칭됨)
        Set<Long> requestedMemberIds = new HashSet<>();
        Set<String> requestedUserNames = new HashSet<>();
        for (VacationRequest request : vacationRequestRepository
                .findByCompanyAndDateBetween(company, monthStart, monthEnd)) {
            if (request.getStatus() == VacationRequest.VacationStatus.REJECTED) {
                continue; // 반려된 신청만 있는 직원은 다시 입력해야 하므로 미입력으로 본다
            }
            if (request.getUserId() != null && !request.getUserId().isBlank()) {
                try {
                    requestedMemberIds.add(Long.parseLong(request.getUserId()));
                } catch (NumberFormatException ignored) {
                    // 숫자가 아닌 레거시 userId — 이름으로 폴백
                }
            }
            if (request.getUserName() != null && !request.getUserName().isBlank()) {
                requestedUserNames.add(request.getUserName());
            }
        }

        String monthLabel = targetMonth.getMonthValue() + "월";
        String title = daysLeft == 0
                ? monthLabel + " 휴무 입력이 오늘 마감됩니다"
                : String.format("%s 휴무 입력 마감 D-%d", monthLabel, daysLeft);
        String body = String.format("%s까지 %s 휴무를 입력해주세요. 아직 입력된 휴무가 없습니다.",
                deadline.format(DATE_LABEL), monthLabel);

        int notified = 0;
        List<Member> members = memberRepository.findByCompanyAndStatus(company, Member.MemberStatus.ACTIVE);
        for (Member member : members) {
            if (requestedMemberIds.contains(member.getId()) || requestedUserNames.contains(member.getName())) {
                continue; // 이미 입력함
            }
            if (member.getFcmToken() == null || member.getFcmToken().isEmpty()) {
                continue;
            }
            try {
                fcmService.sendNotification(member.getFcmToken(), title, body,
                        Map.of("type", "vacation_deadline_reminder",
                                "targetMonth", targetMonth.toString(),
                                "deadline", deadline.toString()));
                notified++;
            } catch (Exception e) {
                log.warn("[DeadlinePreReminder] 발송 실패: memberId={}, {}", member.getId(), e.getMessage());
            }
        }

        if (notified > 0) {
            log.info("[DeadlinePreReminder] companyId={} 마감 D-{} 미입력 직원 {}명에게 발송 (전체 {}명)",
                    companyId, daysLeft, notified, members.size());
        }
    }
}
