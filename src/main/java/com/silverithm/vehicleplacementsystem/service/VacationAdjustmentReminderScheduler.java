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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        // 관리자 화면(VacationService.getVacationForDate/getVacationCalendar)과 같은 기준으로 세기 위해
        // 신청 당시 저장된 역할이 아니라 회원에게 현재 배정된 역할로 그룹핑한다. 직책이 신청 후 바뀌면
        // 저장된 역할 기준 판정은 관리자 화면과 어긋나 초과가 누락되거나 엉뚱한 사람에게 알림이 간다.
        MemberRoleResolver roleResolver = buildMemberRoleResolver(company);

        // (날짜, 현재 배정된 정규화 직종) → 신청 목록
        Map<String, List<VacationRequest>> byDateRole = requests.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDate() + "|" + VacationRequest.normalizeRole(roleResolver.resolve(r))));
        // 날짜 → 전체 신청 목록 ('전체(all)' 제한은 직종 무관 그 날짜 총인원 기준)
        Map<LocalDate, List<VacationRequest>> byDate = requests.stream()
                .collect(Collectors.groupingBy(VacationRequest::getDate));

        int notified = 0;
        // 같은 날 직종 한도와 전체(all) 한도가 동시에 초과되면 한 사람이 두 그룹에
        // 모두 걸린다 — 같은 (사람, 날짜)에는 하루 한 번만 보낸다
        Set<String> sentKeys = new HashSet<>();
        for (VacationLimit limit : limits) {
            // all 제한은 직종 매칭 키에 절대 안 걸리므로 날짜 전체 그룹으로 판정한다
            // (예전엔 이 분기가 없어 전체 제한 초과가 리마인드되지 않았다)
            boolean isAllLimit = "all".equals(VacationRequest.normalizeRole(limit.getRole()));
            String key = limit.getDate() + "|" + VacationRequest.normalizeRole(limit.getRole());
            List<VacationRequest> dayRequests = isAllLimit
                    ? byDate.getOrDefault(limit.getDate(), List.of())
                    : byDateRole.getOrDefault(key, List.of());
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
                if (!sentKeys.add(member.getId() + "|" + limit.getDate())) {
                    continue; // 이미 이 날짜로 알림을 보낸 사람
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

    /**
     * 휴가 신청에 저장된 역할 대신 회원에게 현재 배정된 역할(position)을 우선 사용하기 위한 해석기.
     * VacationService.MemberRoleResolver와 같은 방식(관리자 화면의 초과 판정 기준)을 그대로 따른다.
     */
    private record MemberRoleResolver(Map<String, String> roleByMemberId, Map<String, String> roleByMemberName) {

        String resolve(String userId, String userName, String storedRole) {
            if (userId != null && !userId.isBlank()) {
                String roleById = roleByMemberId.get(userId.trim());
                if (roleById != null) {
                    return roleById;
                }
            }

            if (userName != null && !userName.isBlank()) {
                String roleByName = roleByMemberName.get(userName.trim());
                if (roleByName != null) {
                    return roleByName;
                }
            }

            return VacationRequest.normalizeRole(storedRole);
        }

        String resolve(VacationRequest vacation) {
            return resolve(vacation.getUserId(), vacation.getUserName(), vacation.getRole());
        }
    }

    /** 회사 회원을 한 번에 조회해 맵으로 만든다 — 신청마다 조회하면 N+1이 난다 */
    private MemberRoleResolver buildMemberRoleResolver(Company company) {
        Map<String, String> roleByMemberId = new HashMap<>();
        Map<String, String> roleByMemberName = new HashMap<>();
        Set<String> ambiguousNames = new HashSet<>();

        for (Member member : memberRepository.findByCompanyOrderByCreatedAtDesc(company)) {
            String position = member.getPosition() == null ? "" : member.getPosition().trim();
            if (position.isEmpty()) {
                continue;
            }

            roleByMemberId.put(member.getId().toString(), position);

            String name = member.getName() == null ? "" : member.getName().trim();
            if (name.isEmpty()) {
                continue;
            }

            String previousPosition = roleByMemberName.put(name, position);
            if (previousPosition != null && !previousPosition.equals(position)) {
                // 동명이인이 서로 다른 역할이면 이름만으로는 판단할 수 없다
                ambiguousNames.add(name);
            }
        }

        ambiguousNames.forEach(roleByMemberName::remove);

        return new MemberRoleResolver(roleByMemberId, roleByMemberName);
    }
}
