package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴무 조정 리마인더 — 휴무 입력 마감일이 지나도 특정 날짜·직종의 휴무 신청
 * 인원이 제한(vacation_limits.max_people)을 초과한 채 남아 있으면, 그 날짜에
 * 신청한 직원들에게 조정 요청 푸시를 매일 보낸다 (조정될 때까지 반복).
 *
 * 검사 대상 기간: 내일부터 다음 달 말일까지 (이미 지난 날짜는 조정 불가).
 * 마감 판정: 이번 달 deadline_day(말일 초과 시 말일)를 지난 날부터 발송.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VacationAdjustmentReminderScheduler {

    private final VacationDeadlineSettingRepository settingRepository;
    private final VacationDeadlineDateService deadlineDateService;
    private final VacationLimitRepository vacationLimitRepository;
    private final VacationRequestRepository vacationRequestRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final FCMService fcmService;
    private final RedisUtils redisUtils;

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("M월 d일");

    @Scheduled(cron = "0 30 9 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void remindUnresolvedVacationOverages() {
        // 블루그린 이중 기동 대비 — 하루 한 인스턴스만 발송
        if (!redisUtils.tryAcquireDailySchedulerLock("vacation-adjustment-reminder", 120)) {
            log.info("[VacationReminder] 오늘 리마인더는 다른 인스턴스가 이미 실행함 — 스킵");
            return;
        }

        LocalDate today = LocalDate.now();
        List<VacationDeadlineSetting> settings = settingRepository.findByEnabledTrue();
        log.info("[VacationReminder] 휴무 조정 리마인더 시작: 대상 회사 {}곳", settings.size());

        for (VacationDeadlineSetting setting : settings) {
            try {
                // 이번 달에 따로 지정한 마감일이 있으면 그 날짜가 매월 고정일보다 우선한다
                LocalDate deadline = deadlineDateService.resolveDeadline(
                        setting.getCompanyId(), YearMonth.from(today), setting.getDeadlineDay());
                if (deadline == null || !today.isAfter(deadline)) {
                    continue; // 아직 마감 전
                }
                remindCompany(setting.getCompanyId(), today);
            } catch (Exception e) {
                log.error("[VacationReminder] 회사 처리 실패: companyId={}, {}",
                        setting.getCompanyId(), e.getMessage());
            }
        }
    }

    private void remindCompany(Long companyId, LocalDate today) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return;
        }

        LocalDate rangeStart = today.plusDays(1);
        LocalDate rangeEnd = today.plusMonths(1).withDayOfMonth(today.plusMonths(1).lengthOfMonth());

        List<VacationLimit> limits = vacationLimitRepository.findByCompanyAndDateBetween(company, rangeStart, rangeEnd);
        if (limits.isEmpty()) {
            return;
        }

        List<VacationRequest> requests =
                vacationRequestRepository.findByCompanyAndDateBetween(company, rangeStart, rangeEnd).stream()
                        .filter(r -> r.getStatus() == VacationRequest.VacationStatus.PENDING
                                || r.getStatus() == VacationRequest.VacationStatus.APPROVED)
                        .collect(Collectors.toList());

        // (날짜, 정규화 직종) → 신청 목록
        Map<String, List<VacationRequest>> byDateRole = requests.stream()
                .collect(Collectors.groupingBy(r -> r.getDate() + "|" + r.getNormalizedRole()));

        int notified = 0;
        for (VacationLimit limit : limits) {
            String key = limit.getDate() + "|" + VacationRequest.normalizeRole(limit.getRole());
            List<VacationRequest> dayRequests = byDateRole.getOrDefault(key, List.of());
            if (limit.getMaxPeople() == null || dayRequests.size() <= limit.getMaxPeople()) {
                continue;
            }

            String title = "휴무 조정이 필요합니다";
            String body = String.format("%s 휴무 신청이 %d명으로 제한(%d명)을 초과했습니다. 휴무 입력 마감일이 지나 조정이 필요해요.",
                    limit.getDate().format(DATE_LABEL), dayRequests.size(), limit.getMaxPeople());

            for (VacationRequest request : dayRequests) {
                Member member = findMember(request);
                if (member == null || member.getFcmToken() == null || member.getFcmToken().isEmpty()) {
                    continue;
                }
                try {
                    fcmService.sendNotification(member.getFcmToken(), title, body,
                            Map.of("type", "vacation_adjustment", "date", limit.getDate().toString()));
                    notified++;
                } catch (Exception e) {
                    log.warn("[VacationReminder] 발송 실패: memberId={}, {}", member.getId(), e.getMessage());
                }
            }
        }

        if (notified > 0) {
            log.info("[VacationReminder] companyId={} 조정 요청 푸시 {}건 발송", companyId, notified);
        }
    }

    private Member findMember(VacationRequest request) {
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            try {
                Member byId = memberRepository.findById(Long.parseLong(request.getUserId())).orElse(null);
                if (byId != null) {
                    return byId;
                }
            } catch (NumberFormatException ignored) {
                // userId가 숫자가 아닌 레거시 데이터 — 이름으로 폴백
            }
        }
        // 이름은 암호화 컬럼이라 DB LIKE가 안 된다 — 회사 회원을 불러 복호화된 이름으로 맞춘다
        return memberRepository.findByCompanyOrderByCreatedAtDesc(request.getCompany())
                .stream()
                .filter(m -> request.getUserName().equals(m.getName()))
                .findFirst().orElse(null);
    }
}
