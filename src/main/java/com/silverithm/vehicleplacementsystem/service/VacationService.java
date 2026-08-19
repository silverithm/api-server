package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.VacationLimit;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationLimitRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationDeadlineSettingRepository;
import com.silverithm.vehicleplacementsystem.entity.VacationDeadlineSetting;
import com.silverithm.vehicleplacementsystem.repository.VacationRequestRepository;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.silverithm.vehicleplacementsystem.util.PersonDisplay;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacationService {
    private static final String ALL_ROLE = "all";
    private static final String DEFAULT_ROLE = "caregiver";

    private final VacationRequestRepository vacationRequestRepository;
    private final VacationLimitRepository vacationLimitRepository;
    private final VacationDeadlineSettingRepository vacationDeadlineSettingRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final AdminNotificationTargets adminNotificationTargets;
    private final ResourceScopeGuard resourceScopeGuard;

    public VacationCalendarResponseDTO getVacationCalendar(
            Long companyId,
            LocalDate startDate,
            LocalDate endDate,
            String roleFilter,
            String nameFilter) {

        log.info("[Vacation Service] 휴가 캘린더 요청: companyId={}, {} ~ {}, role={}, name={}",
                companyId, startDate, endDate, roleFilter, nameFilter);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        // 회사별 휴가 신청 데이터 조회
        List<VacationRequest> vacations = vacationRequestRepository.findByCompanyAndDateBetween(company, startDate,
                endDate);
        List<VacationLimit> limits = vacationLimitRepository.findByCompanyAndDateBetween(company, startDate, endDate);
        String normalizedRoleFilter = normalizeRequestedRole(roleFilter);
        MemberRoleResolver roleResolver = buildMemberRoleResolver(company);

        // 역할별 필터링 (신청 당시 값이 아니라 현재 배정된 역할 기준)
        if (!ALL_ROLE.equals(normalizedRoleFilter)) {
            vacations = vacations.stream()
                    .filter(v -> matchesRole(roleResolver.resolve(v), normalizedRoleFilter))
                    .collect(Collectors.toList());
            limits = limits.stream()
                    .filter(l -> matchesExactRole(l.getRole(), normalizedRoleFilter))
                    .collect(Collectors.toList());
        }

        // 이름별 필터링
        if (nameFilter != null && !nameFilter.trim().isEmpty()) {
            vacations = vacations.stream()
                    .filter(v -> nameFilter.equals(v.getUserName()))
                    .collect(Collectors.toList());
        }

        // 날짜별로 그룹화
        Map<String, VacationCalendarResponseDTO.VacationDateInfo> dateMap = new HashMap<>();

        // 휴가 신청 데이터 처리
        Map<LocalDate, List<VacationRequest>> vacationsByDate = vacations.stream()
                .collect(Collectors.groupingBy(VacationRequest::getDate));

        vacationsByDate.forEach((date, dateVacations) -> {
            String dateKey = date.toString();

            List<VacationRequestDTO> vacationDTOs = dateVacations.stream()
                    .map(vacation -> VacationRequestDTO.fromEntity(vacation, roleResolver.resolve(vacation)))
                    .collect(Collectors.toList());

            // 거부되지 않은 휴가만 카운트
            int totalVacationers = (int) dateVacations.stream()
                    .filter(v -> v.getStatus() != VacationRequest.VacationStatus.REJECTED)
                    .count();

            dateMap.put(dateKey, VacationCalendarResponseDTO.VacationDateInfo.builder()
                    .date(dateKey)
                    .vacations(vacationDTOs)
                    .totalVacationers(totalVacationers)
                    .maxPeople(3) // 기본값
                    .build());
        });

        // 휴가 제한 데이터 적용
        limits.forEach(limit -> {
            String dateKey = limit.getDate().toString();
            VacationCalendarResponseDTO.VacationDateInfo dateInfo = dateMap.get(dateKey);

            if (dateInfo != null) {
                dateInfo.setMaxPeople(Math.max(dateInfo.getMaxPeople(), limit.getMaxPeople()));
            } else {
                dateMap.put(dateKey, VacationCalendarResponseDTO.VacationDateInfo.builder()
                        .date(dateKey)
                        .vacations(List.of())
                        .totalVacationers(0)
                        .maxPeople(limit.getMaxPeople())
                        .build());
            }
        });

        log.info("[Vacation Service] 응답 완료: 회사 {}, 날짜 수={}", company.getName(), dateMap.size());

        return VacationCalendarResponseDTO.builder()
                .dates(dateMap)
                .build();
    }

    public VacationDateResponseDTO getVacationForDate(Long companyId, LocalDate date, String role, String nameFilter) {
        log.info("[Vacation Service] 날짜 {} 휴가 요청: companyId={}, role={}, nameFilter={}", date, companyId, role,
                nameFilter);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        // 회사별 휴가 신청 조회
        List<VacationRequest> vacations = vacationRequestRepository.findByCompanyAndDate(company, date);
        String normalizedRole = normalizeRequestedRole(role);
        MemberRoleResolver roleResolver = buildMemberRoleResolver(company);

        // 역할별 필터링 (신청 당시 값이 아니라 현재 배정된 역할 기준)
        if (!ALL_ROLE.equals(normalizedRole)) {
            vacations = vacations.stream()
                    .filter(v -> matchesExactRole(roleResolver.resolve(v), normalizedRole))
                    .collect(Collectors.toList());
        }

        // 이름별 필터링
        if (nameFilter != null && !nameFilter.trim().isEmpty()) {
            vacations = vacations.stream()
                    .filter(v -> nameFilter.equals(v.getUserName()))
                    .collect(Collectors.toList());
        }

        List<VacationRequestDTO> vacationDTOs = vacations.stream()
                .map(vacation -> VacationRequestDTO.fromEntity(vacation, roleResolver.resolve(vacation)))
                .collect(Collectors.toList());

        // 거부되지 않은 휴가만 카운트
        int totalVacationers = (int) vacations.stream()
                .filter(v -> v.getStatus() != VacationRequest.VacationStatus.REJECTED)
                .count();

        // 회사별 휴가 제한 조회
        List<VacationLimit> dailyLimits = vacationLimitRepository.findByCompanyAndDate(company, date);
        Integer maxPeople;
        if (ALL_ROLE.equals(normalizedRole)) {
            maxPeople = dailyLimits.stream()
                    .map(VacationLimit::getMaxPeople)
                    .max(Integer::compareTo)
                    .orElse(3);
        } else {
            maxPeople = dailyLimits.stream()
                    .filter(limit -> matchesExactRole(limit.getRole(), normalizedRole))
                    .map(VacationLimit::getMaxPeople)
                    .findFirst()
                    .orElse(3);
        }

        log.info("[Vacation Service] 날짜 {} 응답: 회사 {}, {}명의 휴가자, 제한={}", date, company.getName(), totalVacationers,
                maxPeople);

        return VacationDateResponseDTO.builder()
                .date(date.toString())
                .vacations(vacationDTOs)
                .totalVacationers(totalVacationers)
                .maxPeople(maxPeople)
                .build();
    }

    @Transactional
    public VacationRequestDTO createVacationRequest(Long companyId, VacationCreateRequestDTO requestDTO) {
        log.info("[Vacation Service] 휴가 신청 생성: companyId={}, {}, 날짜: {}", companyId, requestDTO.getUserName(),
                requestDTO.getDate());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        // 화면에서 막더라도 서버가 다시 확인한다 (구버전 앱·직접 호출 대비)
        validateNextMonthOnly(companyId, requestDTO.getDate());

        // 클라이언트가 보낸 legacy role(caregiver/office)보다 회원에게 배정된 역할을 우선한다
        String role = resolveRoleForNewRequest(company, requestDTO.getUserId(), requestDTO.getUserName(),
                requestDTO.getRole());

        // 관리자가 설정한 날짜별 휴무 제한 인원을 서버에서도 확인한다 — 화면이 못 막아도
        // (구버전 앱 포함) 초과 신청이 조용히 쌓이지 않게 즉시 사유를 돌려준다.
        validateDailyVacationLimit(company, requestDTO.getDate(), role);

        // userId 생성 (없으면 자동 생성)
        String userId = requestDTO.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            userId = "user_" + System.currentTimeMillis();
        }

        String type = requestDTO.getType() != null ? requestDTO.getType() : VacationRequest.TYPE_REGULAR;

        VacationRequest entity = VacationRequest.builder()
                .userName(requestDTO.getUserName())
                .date(requestDTO.getDate())
                .reason(requestDTO.getReason())
                .role(role)
                .type(type)
                .vacationType(resolveVacationType(type, requestDTO.getVacationType()))
                .duration(resolveDuration(requestDTO.getDuration(), true))
                .userId(userId)
                .company(company)
                .status(VacationRequest.VacationStatus.PENDING)
                .build();

        VacationRequest saved = vacationRequestRepository.save(entity);

        log.info("[Vacation Service] 휴가 신청 생성 완료: 회사 {}, ID={}", company.getName(), saved.getId());

        try {
            sendVacationSubmittedNotificationToAdmins(saved, company);
        } catch (Exception e) {
            log.error("[Vacation Service] 관리자 알림 전송 실패: {}", e.getMessage());
            // 알림 전송 실패는 휴가 신청 자체에는 영향을 주지 않음
        }

        return VacationRequestDTO.fromEntity(saved);
    }

    /**
     * 날짜별 휴무 제한 인원 검증. 관리자가 그 날짜·역할에 제한을 명시했을 때만 막는다 —
     * 화면의 기본값 3명은 표시용이라, 제한을 안 쓰는 기관까지 갑자기 차단하면 안 된다.
     * 집계는 달력 표시와 같은 규칙(거부 제외, 현재 배정 역할 기준)을 쓴다.
     */
    private void validateDailyVacationLimit(Company company, LocalDate date, String role) {
        String normalizedRole = normalizeRequestedRole(role);

        List<VacationLimit> dailyLimits = vacationLimitRepository.findByCompanyAndDate(company, date);
        Integer maxPeople = dailyLimits.stream()
                .filter(limit -> matchesExactRole(limit.getRole(), normalizedRole))
                .map(VacationLimit::getMaxPeople)
                .findFirst()
                .orElse(null);
        if (maxPeople == null) {
            return; // 이 날짜·역할에 설정된 제한 없음
        }

        MemberRoleResolver roleResolver = buildMemberRoleResolver(company);
        long active = vacationRequestRepository.findByCompanyAndDate(company, date).stream()
                .filter(v -> v.getStatus() != VacationRequest.VacationStatus.REJECTED)
                .filter(v -> matchesExactRole(roleResolver.resolve(v), normalizedRole))
                .count();

        if (active >= maxPeople) {
            throw new CustomException(
                    String.format("%d월 %d일은 휴무 가능 인원(%d명)이 가득 찼습니다. 현재 %d명이 신청되어 있어요. "
                                    + "다른 날짜를 선택하시거나 관리자에게 문의해주세요.",
                            date.getMonthValue(), date.getDayOfMonth(), maxPeople, active),
                    HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void approveVacation(Long id) {
        log.info("[Vacation Service] 휴가 승인: ID={}", id);

        VacationRequest vacation = vacationRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 휴가 신청을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(vacation.getCompany());

        vacation.setStatus(VacationRequest.VacationStatus.APPROVED);
        VacationRequest saved = vacationRequestRepository.save(vacation);

        log.info("[Vacation Service] 휴가 승인 완료: ID={}", id);

        // 신청자에게 승인 알림 전송
        try {
            sendVacationApprovedNotificationToUser(saved);
        } catch (Exception e) {
            log.error("[Vacation Service] 승인 알림 전송 실패: {}", e.getMessage());
        }
    }

    @Transactional
    public void rejectVacation(Long id) {
        log.info("[Vacation Service] 휴가 거부: ID={}", id);

        VacationRequest vacation = vacationRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 휴가 신청을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(vacation.getCompany());

        vacation.setStatus(VacationRequest.VacationStatus.REJECTED);
        VacationRequest saved = vacationRequestRepository.save(vacation);

        log.info("[Vacation Service] 휴가 거부 완료: ID={}", id);

        // 신청자에게 거부 알림 전송
        try {
            sendVacationRejectedNotificationToUser(saved);
        } catch (Exception e) {
            log.error("[Vacation Service] 거부 알림 전송 실패: {}", e.getMessage());
        }
    }

    @Transactional
    public void deleteVacation(Long id, VacationDeleteRequestDTO deleteRequest) {
        log.info("[Vacation Service] 휴가 삭제 요청: ID={}, 관리자권한={}", id, deleteRequest.getIsAdmin());

        VacationRequest vacation = vacationRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 휴가 신청을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(vacation.getCompany());

        vacationRequestRepository.delete(vacation);

        log.info("[Vacation Service] 휴가 삭제 완료: ID={}", id);
    }

    public List<VacationRequestDTO> getAllVacationRequests(Long companyId) {
        log.info("[Vacation Service] 모든 휴가 요청 조회: companyId={}", companyId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        List<VacationRequest> vacations = vacationRequestRepository.findByCompanyOrderByCreatedAtDesc(company);
        MemberRoleResolver roleResolver = buildMemberRoleResolver(company);

        log.info("[Vacation Service] 휴가 요청 조회 완료: 회사 {}, {}건", company.getName(), vacations.size());

        return vacations.stream()
                .map(vacation -> VacationRequestDTO.fromEntity(vacation, roleResolver.resolve(vacation)))
                .collect(Collectors.toList());
    }

    public List<VacationLimitDTO> getVacationLimits(Long companyId, LocalDate startDate, LocalDate endDate) {
        log.info("[Vacation Service] 휴가 제한 조회: companyId={}, {} ~ {}", companyId, startDate, endDate);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        List<VacationLimit> limits = vacationLimitRepository.findByCompanyAndDateBetween(company, startDate, endDate);

        log.info("[Vacation Service] 휴가 제한 조회 완료: 회사 {}, {}건", company.getName(), limits.size());

        return limits.stream()
                .map(VacationLimitDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<VacationLimitDTO> saveVacationLimits(Long companyId, VacationLimitRequestDTO requestDTO) {
        log.info("[Vacation Service] 휴가 제한 저장: companyId={}, {}건", companyId, requestDTO.getLimits().size());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        List<VacationLimitDTO> savedLimits = new ArrayList<>();

        for (VacationLimitRequestDTO.VacationLimitCreateDTO limitDTO : requestDTO.getLimits()) {
            try {
                LocalDate date = LocalDate.parse(limitDTO.getDate());
                String role = normalizePersistedRole(limitDTO.getRole());

                // 기존 제한이 있는지 확인 (회사별로)
                VacationLimit existingLimit = vacationLimitRepository.findByCompanyAndDate(company, date)
                        .stream()
                        .filter(limit -> matchesExactRole(limit.getRole(), role))
                        .findFirst()
                        .orElse(null);

                if (existingLimit != null) {
                    // 기존 제한 업데이트
                    existingLimit.setMaxPeople(limitDTO.getMaxPeople());
                    VacationLimit saved = vacationLimitRepository.save(existingLimit);
                    savedLimits.add(VacationLimitDTO.fromEntity(saved));
                } else {
                    // 새 제한 생성
                    VacationLimit newLimit = VacationLimit.builder()
                            .date(date)
                            .maxPeople(limitDTO.getMaxPeople())
                            .role(role)
                            .company(company)
                            .build();
                    VacationLimit saved = vacationLimitRepository.save(newLimit);
                    savedLimits.add(VacationLimitDTO.fromEntity(saved));
                }

                log.info("[Vacation Service] 휴가 제한 저장 완료: 회사 {}, {}, 최대 {}명", company.getName(), date,
                        limitDTO.getMaxPeople());

            } catch (Exception e) {
                log.error("[Vacation Service] 휴가 제한 저장 실패: {}", limitDTO, e);
                // 개별 오류는 로깅만 하고 계속 진행
            }
        }

        log.info("[Vacation Service] 휴가 제한 저장 전체 완료: 회사 {}, {}건", company.getName(), savedLimits.size());

        return savedLimits;
    }

    @Transactional
    public List<VacationLimitDTO> saveVacationLimitsV2(Long companyId, VacationLimitRequestDTO requestDTO) {
        log.info("[Vacation Service] 휴가 제한 저장: companyId={}, {}건", companyId, requestDTO.getLimits().size());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        // 1. 모든 날짜와 역할 추출 (파싱 한번만)
        List<LocalDate> dates = new ArrayList<>();
        Map<String, VacationLimitRequestDTO.VacationLimitCreateDTO> requestMap = new HashMap<>();

        for (VacationLimitRequestDTO.VacationLimitCreateDTO limitDTO : requestDTO.getLimits()) {
            try {
                LocalDate date = LocalDate.parse(limitDTO.getDate());
                String role = normalizePersistedRole(limitDTO.getRole());

                dates.add(date);
                requestMap.put(buildLimitKey(date, role), limitDTO);
            } catch (Exception e) {
                log.error("[Vacation Service] 휴가 제한 파싱 실패: {}", limitDTO, e);
            }
        }

        if (dates.isEmpty()) {
            return List.of();
        }

        LocalDate minDate = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate maxDate = dates.stream().max(LocalDate::compareTo).orElseThrow();

        // 2. 한 번에 기존 데이터 조회 후 정규화 키로 매핑
        Map<String, VacationLimit> existingMap = vacationLimitRepository
                .findByCompanyAndDateBetween(company, minDate, maxDate)
                .stream()
                .collect(Collectors.toMap(
                        limit -> buildLimitKey(limit.getDate(), limit.getRole()),
                        Function.identity(),
                        (first, second) -> first
                ));

        // 3. 업데이트/생성 처리
        List<VacationLimit> toSave = new ArrayList<>();

        for (Map.Entry<String, VacationLimitRequestDTO.VacationLimitCreateDTO> entry : requestMap.entrySet()) {
            String key = entry.getKey();
            VacationLimitRequestDTO.VacationLimitCreateDTO dto = entry.getValue();

            VacationLimit existingLimit = existingMap.get(key);

            if (existingLimit != null) {
                // 기존 엔티티 업데이트
                existingLimit.setMaxPeople(dto.getMaxPeople());
                toSave.add(existingLimit);
            } else {
                // 새 엔티티 생성
                LocalDate date = LocalDate.parse(dto.getDate());
                String role = normalizePersistedRole(dto.getRole());

                VacationLimit newLimit = VacationLimit.builder()
                        .date(date)
                        .maxPeople(dto.getMaxPeople())
                        .role(role)
                        .company(company)
                        .build();
                toSave.add(newLimit);
            }
        }

        // 4. 한 번에 저장
        List<VacationLimit> saved = vacationLimitRepository.saveAll(toSave);

        log.info("[Vacation Service] 휴가 제한 저장 완료: 회사 {}, {}건", company.getName(), saved.size());

        // 5. DTO 변환 (한 번만)
        return saved.stream()
                .map(VacationLimitDTO::fromEntity)
                .collect(Collectors.toList());
    }


    // 알림 전송 헬퍼 메서드들
    private void sendVacationApprovedNotificationToUser(VacationRequest vacation) {
        // 실제 환경에서는 사용자의 FCM 토큰을 조회해야 함
        String userFcmToken = getUserFcmToken(vacation.getUserId(), vacation.getUserName());

        if (userFcmToken != null) {
            notificationService.sendVacationApprovedNotification(
                    userFcmToken,
                    vacation.getUserId(),
                    vacation.getUserName(),
                    vacation.getDate().toString(),
                    vacation.getId()
            );
        } else {
            log.warn("[Vacation Service] 사용자 FCM 토큰을 찾을 수 없음: {}", vacation.getUserName());
        }
    }

    private void sendVacationRejectedNotificationToUser(VacationRequest vacation) {
        String userFcmToken = getUserFcmToken(vacation.getUserId(), vacation.getUserName());

        if (userFcmToken != null) {
            notificationService.sendVacationRejectedNotification(
                    userFcmToken,
                    vacation.getUserId(),
                    vacation.getUserName(),
                    vacation.getDate().toString(),
                    vacation.getId()
            );
        } else {
            log.warn("[Vacation Service] 사용자 FCM 토큰을 찾을 수 없음: {}", vacation.getUserName());
        }
    }

    private void sendVacationSubmittedNotificationToAdmins(VacationRequest vacation, Company company) {
        List<String> adminFcmTokens = getAdminFcmTokens(company);
        // 이름만 적으면 동명이인일 때 누구인지 알 수 없어 직책을 함께 보여준다
        String submitter = PersonDisplay.withPosition(vacation.getUserName(), findSubmitterPosition(vacation));

        for (String adminToken : adminFcmTokens) {
            try {
                notificationService.sendVacationSubmittedNotification(
                        adminToken,
                        "admin", // 관리자 사용자 ID
                        "관리자", // 관리자 이름
                        submitter,
                        vacation.getDate().toString(),
                        vacation.getId()
                );
            } catch (Exception e) {
                log.error("[Vacation Service] 관리자 알림 전송 실패: {}", e.getMessage());
            }
        }
    }

    /** 신청자의 직책. 못 찾으면 null이라 이름만 나간다 (알림 때문에 신청이 막히면 안 된다) */
    private String findSubmitterPosition(VacationRequest vacation) {
        String userId = vacation.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return memberRepository.findById(Long.valueOf(userId.trim()))
                    .map(Member::getPosition)
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getUserFcmToken(String userId, String userName) {
        log.debug("[Vacation Service] 사용자 FCM 토큰 조회: userId={}, userName={}", userId, userName);

        try {
            // userId 또는 userName으로 Member 조회
            Member member = memberRepository.findById(Long.valueOf(userId)).orElseThrow(
                    () -> new CustomException("존재하지 않는 사용자입니다: " + userId, HttpStatus.NOT_FOUND));

            // Member를 찾았으면 FCM 토큰 반환
            if (member != null && member.getFcmToken() != null) {
                log.debug("[Vacation Service] FCM 토큰 조회 성공: userId={}, userName={}", userId, userName);
                return member.getFcmToken();
            } else {
                log.warn("[Vacation Service] FCM 토큰이 없습니다: userId={}, userName={}", userId, userName);
                return null; // FCM 토큰이 없는 경우 null 반환
            }
        } catch (Exception e) {
            // 알림은 부수 기능 — 토큰 조회 실패가 승인/거절 본 처리를 깨뜨리지 않도록 null 반환
            log.error("[Vacation Service] FCM 토큰 조회 중 오류 발생: userId={}, userName={}", userId, userName, e);
            return null;
        }
    }

    private List<String> getAdminFcmTokens(Company company) {
        log.debug("[Vacation Service] 관리자 FCM 토큰 목록 조회");

        try {
            return adminNotificationTargets.fcmTokensOf(company);
        } catch (Exception e) {
            log.error("[Vacation Service] 관리자 FCM 토큰 조회 중 오류 발생", e);
            return List.of(); // 빈 리스트 반환
        }
    }

    // 관리자가 직원 대신 휴무 신청하는 메서드
    
    @Transactional
    public VacationRequestDTO createVacationRequestByAdmin(Long companyId, AdminVacationCreateRequestDTO requestDTO) {
        log.info("[Vacation Service] 관리자가 직원 대신 휴가 신청: companyId={}, memberId={}, 날짜: {}", 
                companyId, requestDTO.getMemberId(), requestDTO.getDate());
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        
        Member member = memberRepository.findById(requestDTO.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직원입니다: " + requestDTO.getMemberId()));
        
        // 직원이 해당 회사 소속인지 확인
        if (!member.getCompany().getId().equals(companyId)) {
            throw new IllegalArgumentException("해당 직원은 이 회사 소속이 아닙니다");
        }
        
        String vacationRole = resolveMemberVacationRole(member);
        
        String type = requestDTO.getType() != null ? requestDTO.getType() : "admin_created";
        boolean useAnnualLeave = !Boolean.FALSE.equals(requestDTO.getUseAnnualLeave());

        VacationRequest entity = VacationRequest.builder()
                .userName(member.getName())
                .date(requestDTO.getDate())
                .reason(requestDTO.getReason() != null ? requestDTO.getReason() : "관리자 대신 신청")
                .role(vacationRole)
                .type(type)
                .vacationType(resolveVacationType(type, requestDTO.getVacationType()))
                .duration(resolveDuration(requestDTO.getDuration(), useAnnualLeave))
                .userId(member.getId().toString())
                .company(company)
                .status(VacationRequest.VacationStatus.PENDING) // 관리자가 신청해도 대기중 상태로 생성
                .build();
        
        VacationRequest saved = vacationRequestRepository.save(entity);
        
        log.info("[Vacation Service] 관리자 대신 휴가 신청 완료: 회사 {}, 직원 {}, ID={}", 
                company.getName(), member.getName(), saved.getId());
        
        // 직원에게 휴무 등록 알림 전송
        try {
            sendVacationCreatedByAdminNotificationToUser(saved, member);
        } catch (Exception e) {
            log.error("[Vacation Service] 직원 알림 전송 실패: {}", e.getMessage());
        }
        
        return VacationRequestDTO.fromEntity(saved);
    }
    
    /**
     * duration은 휴무를 종일 쓰는지 반일 쓰는지에 대한 표시값이다.
     * 연차를 사용하지 않는 휴무는 종일/반차 구분이 의미가 없으므로 UNUSED를 저장한다.
     */
    private String resolveDuration(VacationRequest.VacationDuration requested, boolean useAnnualLeave) {
        if (!useAnnualLeave) {
            return VacationRequest.VacationDuration.UNUSED.name();
        }

        return requested != null
                ? requested.name()
                : VacationRequest.VacationDuration.FULL_DAY.name();
    }

    /**
     * 대체휴무는 세부 유형도 substitute로 고정한다. 그 외에는 클라이언트가 보낸 값을 그대로 보존한다.
     * (기존에는 vacationType을 받고도 저장하지 않아 병가/긴급 등의 정보가 유실됐다)
     */
    private String resolveVacationType(String type, String requestedVacationType) {
        if (VacationRequest.isSubstituteType(type)) {
            return VacationRequest.TYPE_SUBSTITUTE;
        }

        if (requestedVacationType == null || requestedVacationType.isBlank()) {
            return null;
        }

        return requestedVacationType.trim();
    }

    private void sendVacationCreatedByAdminNotificationToUser(VacationRequest vacation, Member member) {
        if (member.getFcmToken() != null && !member.getFcmToken().isEmpty()) {
            notificationService.sendVacationApprovedNotification(
                    member.getFcmToken(),
                    member.getId().toString(),
                    member.getName(),
                    vacation.getDate().toString(),
                    vacation.getId()
            );
        } else {
            log.warn("[Vacation Service] 직원 FCM 토큰을 찾을 수 없음: {}", PrivacyMask.name(member.getName()));
        }
    }

    private String normalizeRequestedRole(String role) {
        if (role == null || role.isBlank()) {
            return ALL_ROLE;
        }

        return VacationRequest.normalizeRole(role);
    }

    private String normalizePersistedRole(String role) {
        if (role == null || role.isBlank()) {
            return DEFAULT_ROLE;
        }

        return VacationRequest.normalizeRole(role);
    }

    private boolean matchesRole(String storedRole, String requestedRole) {
        String normalizedStoredRole = VacationRequest.normalizeRole(storedRole);
        return normalizedStoredRole.equals(requestedRole) || ALL_ROLE.equals(normalizedStoredRole);
    }

    private boolean matchesExactRole(String storedRole, String requestedRole) {
        return VacationRequest.normalizeRole(storedRole)
                .equals(VacationRequest.normalizeRole(requestedRole));
    }

    private String buildLimitKey(LocalDate date, String role) {
        return date + "_" + VacationRequest.normalizeRole(role);
    }

    private String resolveMemberVacationRole(Member member) {
        if (member.getPosition() != null && !member.getPosition().isBlank()) {
            return member.getPosition().trim();
        }

        return switch (member.getRole()) {
            case CAREGIVER -> "caregiver";
            case OFFICE -> "office";
            case ADMIN -> "admin";
            default -> "employee";
        };
    }

    /**
     * 휴가 신청에 저장된 역할 대신 회원에게 현재 배정된 역할(position)을 우선 사용하기 위한 해석기.
     * 신청 시점 이후에 역할이 새로 만들어지거나 재배정되어도 화면에 최신 역할이 보이도록 한다.
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

    /**
     * 신규 휴가 신청에 저장할 역할 결정. 회원을 찾을 수 있으면 배정된 역할을, 못 찾으면 요청값을 쓴다.
     */
    private String resolveRoleForNewRequest(Company company, String userId, String userName, String requestedRole) {
        Member member = findCompanyMember(company, userId, userName);
        if (member != null) {
            return resolveMemberVacationRole(member);
        }

        return normalizePersistedRole(requestedRole);
    }

    private Member findCompanyMember(Company company, String userId, String userName) {
        if (userId != null && userId.trim().matches("\\d+")) {
            Member member = memberRepository.findById(Long.parseLong(userId.trim())).orElse(null);
            if (member != null && member.getCompany() != null
                    && member.getCompany().getId().equals(company.getId())) {
                return member;
            }
        }

        if (userName != null && !userName.isBlank()) {
            String trimmedUserName = userName.trim();
            List<Member> matches = memberRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                    .filter(member -> trimmedUserName.equals(member.getName()))
                    .collect(Collectors.toList());

            // 동명이인이면 어느 쪽인지 알 수 없으므로 요청값을 그대로 둔다
            if (matches.size() == 1) {
                return matches.get(0);
            }
        }

        return null;
    }
    
    // 일괄 승인/거부 메서드들
    
    @Transactional
    public VacationBulkActionResponseDTO bulkApproveVacations(List<Long> vacationIds) {
        log.info("[Vacation Service] 휴가 일괄 승인: {}건", vacationIds.size());
        
        if (vacationIds == null || vacationIds.isEmpty()) {
            return VacationBulkActionResponseDTO.builder()
                    .totalRequested(0)
                    .successCount(0)
                    .failureCount(0)
                    .message("처리할 휴가가 없습니다")
                    .build();
        }
        
        // 한 번에 모든 휴가 신청 조회
        List<VacationRequest> vacations = vacationRequestRepository.findAllById(vacationIds);
        Map<Long, VacationRequest> vacationMap = vacations.stream()
                .collect(Collectors.toMap(VacationRequest::getId, Function.identity()));
        
        List<VacationRequest> toUpdate = new ArrayList<>();
        List<Long> successIds = new ArrayList<>();
        List<Long> failureIds = new ArrayList<>();
        Map<Long, String> failureReasons = new HashMap<>();
        
        // 각 ID에 대해 처리
        for (Long vacationId : vacationIds) {
            VacationRequest vacation = vacationMap.get(vacationId);
            
            if (vacation == null) {
                failureIds.add(vacationId);
                failureReasons.put(vacationId, "휴가 신청을 찾을 수 없습니다");
                continue;
            }
            
            if (vacation.getStatus() == VacationRequest.VacationStatus.APPROVED) {
                failureIds.add(vacationId);
                failureReasons.put(vacationId, "이미 승인된 휴가입니다");
                continue;
            }
            
            vacation.setStatus(VacationRequest.VacationStatus.APPROVED);
            toUpdate.add(vacation);
            successIds.add(vacationId);
        }
        
        // 변경된 모든 엔티티를 한 번에 저장
        if (!toUpdate.isEmpty()) {
            vacationRequestRepository.saveAll(toUpdate);
            
            // 알림 전송 (비동기 처리 고려 가능)
            for (VacationRequest vacation : toUpdate) {
                try {
                    sendVacationApprovedNotificationToUser(vacation);
                } catch (Exception e) {
                    log.error("[Vacation Service] 휴가 승인 알림 전송 실패: ID={}", vacation.getId(), e);
                }
            }
        }
        
        return VacationBulkActionResponseDTO.builder()
                .totalRequested(vacationIds.size())
                .successCount(successIds.size())
                .failureCount(failureIds.size())
                .successIds(successIds)
                .failureIds(failureIds)
                .failureReasons(failureReasons)
                .message(String.format("%d건 중 %d건 승인 완료", vacationIds.size(), successIds.size()))
                .build();
    }
    
    @Transactional
    public VacationBulkActionResponseDTO bulkRejectVacations(List<Long> vacationIds) {
        log.info("[Vacation Service] 휴가 일괄 거부: {}건", vacationIds.size());
        
        if (vacationIds == null || vacationIds.isEmpty()) {
            return VacationBulkActionResponseDTO.builder()
                    .totalRequested(0)
                    .successCount(0)
                    .failureCount(0)
                    .message("처리할 휴가가 없습니다")
                    .build();
        }
        
        // 한 번에 모든 휴가 신청 조회
        List<VacationRequest> vacations = vacationRequestRepository.findAllById(vacationIds);
        Map<Long, VacationRequest> vacationMap = vacations.stream()
                .collect(Collectors.toMap(VacationRequest::getId, Function.identity()));
        
        List<VacationRequest> toUpdate = new ArrayList<>();
        List<Long> successIds = new ArrayList<>();
        List<Long> failureIds = new ArrayList<>();
        Map<Long, String> failureReasons = new HashMap<>();
        
        // 각 ID에 대해 처리
        for (Long vacationId : vacationIds) {
            VacationRequest vacation = vacationMap.get(vacationId);
            
            if (vacation == null) {
                failureIds.add(vacationId);
                failureReasons.put(vacationId, "휴가 신청을 찾을 수 없습니다");
                continue;
            }
            
            if (vacation.getStatus() == VacationRequest.VacationStatus.REJECTED) {
                failureIds.add(vacationId);
                failureReasons.put(vacationId, "이미 거부된 휴가입니다");
                continue;
            }
            
            vacation.setStatus(VacationRequest.VacationStatus.REJECTED);
            toUpdate.add(vacation);
            successIds.add(vacationId);
        }
        
        // 변경된 모든 엔티티를 한 번에 저장
        if (!toUpdate.isEmpty()) {
            vacationRequestRepository.saveAll(toUpdate);
            
            // 알림 전송 (비동기 처리 고려 가능)
            for (VacationRequest vacation : toUpdate) {
                try {
                    sendVacationRejectedNotificationToUser(vacation);
                } catch (Exception e) {
                    log.error("[Vacation Service] 휴가 거부 알림 전송 실패: ID={}", vacation.getId(), e);
                }
            }
        }
        
        return VacationBulkActionResponseDTO.builder()
                .totalRequested(vacationIds.size())
                .successCount(successIds.size())
                .failureCount(failureIds.size())
                .successIds(successIds)
                .failureIds(failureIds)
                .failureReasons(failureReasons)
                .message(String.format("%d건 중 %d건 거부 완료", vacationIds.size(), successIds.size()))
                .build();
    }

    // 멤버 개인용 휴무 관련 메서드들

    /**
     * 멤버 개인의 모든 휴무 신청 조회 (userId와 userName 모두 필수)
     */
    public List<VacationRequestDTO> getMyVacationRequests(Long companyId, String userId, String userName) {
        log.info("[Vacation Service] 개인 휴무 신청 조회: companyId={}, userId={}, userName={}", companyId, userId, userName);

        // userId와 userName 모두 필수
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID가 필요합니다");
        }
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름이 필요합니다");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        // userId와 userName 모두 일치하는 휴무 신청만 조회
        List<VacationRequest> myVacations = vacationRequestRepository.findByCompanyAndUserNameAndDateBetween(
                        company, userName, LocalDate.of(1900, 1, 1), LocalDate.of(2100, 12, 31))
                .stream()
                .filter(v -> userId.equals(v.getUserId()) && userName.equals(v.getUserName()))
                .collect(Collectors.toList());

        // 최신순으로 정렬
        myVacations.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        MemberRoleResolver roleResolver = buildMemberRoleResolver(company);

        log.info("[Vacation Service] 개인 휴무 신청 조회 완료: 회사 {}, 사용자 {}({}), {}건",
                company.getName(), userName, userId, myVacations.size());

        return myVacations.stream()
                .map(vacation -> VacationRequestDTO.fromEntity(vacation, roleResolver.resolve(vacation)))
                .collect(Collectors.toList());
    }

    /**
     * 멤버 개인의 휴무 신청 삭제 (userId와 userName 모두 필수)
     */
    @Transactional
    public void deleteMyVacationRequest(Long vacationId, String userId, String userName) {
        log.info("[Vacation Service] 개인 휴무 삭제 요청: vacationId={}, userId={}, userName={}",
                vacationId, userId, userName);

        // userId와 userName 모두 필수
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID가 필요합니다");
        }
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름이 필요합니다");
        }

        VacationRequest vacation = vacationRequestRepository.findById(vacationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 휴가 신청을 찾을 수 없습니다: " + vacationId));
        resourceScopeGuard.requireSameCompany(vacation.getCompany());

        // userId와 userName 모두 일치하는지 확인
        if (!userId.equals(vacation.getUserId()) || !userName.equals(vacation.getUserName())) {
            throw new IllegalArgumentException("본인의 휴가 신청만 삭제할 수 있습니다");
        }

        // 이미 승인된 휴가는 삭제 불가 (선택사항)
        if (vacation.getStatus() == VacationRequest.VacationStatus.APPROVED) {
            throw new IllegalArgumentException("이미 승인된 휴가는 삭제할 수 없습니다. 관리자에게 문의하세요.");
        }

        vacationRequestRepository.delete(vacation);

        log.info("[Vacation Service] 개인 휴무 삭제 완료: vacationId={}, 사용자={}({})", vacationId, userName, userId);
    }

    /** 휴무 입력 마감일 설정 조회 (없으면 기본값: 비활성, 20일) */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getDeadlineSetting(Long companyId) {
        return vacationDeadlineSettingRepository.findByCompanyId(companyId)
                .map(s2 -> java.util.Map.<String, Object>of(
                        "deadlineDay", s2.getDeadlineDay(),
                        "enabled", s2.getEnabled(),
                        "nextMonthOnly", Boolean.TRUE.equals(s2.getNextMonthOnly())))
                .orElse(java.util.Map.of("deadlineDay", 20, "enabled", false, "nextMonthOnly", false));
    }

    /** 휴무 입력 마감일 설정 저장 (회사당 한 벌, upsert) */
    @Transactional
    public java.util.Map<String, Object> saveDeadlineSetting(Long companyId, Integer deadlineDay, boolean enabled,
                                                             Boolean nextMonthOnly) {
        if (enabled && (deadlineDay == null || deadlineDay < 1 || deadlineDay > 31)) {
            throw new IllegalArgumentException("마감일은 1~31 사이여야 합니다");
        }
        VacationDeadlineSetting setting = vacationDeadlineSettingRepository.findByCompanyId(companyId)
                .orElseGet(() -> VacationDeadlineSetting.builder()
                        .companyId(companyId)
                        .deadlineDay(20)
                        .enabled(false)
                        .nextMonthOnly(false)
                        .build());
        if (deadlineDay != null) {
            setting.setDeadlineDay(deadlineDay);
        }
        setting.setEnabled(enabled);
        // 이 스위치를 안 보내는 호출자(구버전 앱 등)는 기존 값을 유지한다
        if (nextMonthOnly != null) {
            setting.setNextMonthOnly(nextMonthOnly);
        } else if (setting.getNextMonthOnly() == null) {
            setting.setNextMonthOnly(false);
        }
        VacationDeadlineSetting saved = vacationDeadlineSettingRepository.save(setting);
        log.info("[Vacation] 휴무 마감일 설정 저장: companyId={}, day={}, enabled={}, nextMonthOnly={}",
                companyId, saved.getDeadlineDay(), saved.getEnabled(), saved.getNextMonthOnly());
        return java.util.Map.of("deadlineDay", saved.getDeadlineDay(), "enabled", saved.getEnabled(),
                "nextMonthOnly", Boolean.TRUE.equals(saved.getNextMonthOnly()));
    }

    /**
     * "바로 다음 달만" 제한이 켜져 있으면 신청 날짜가 다음 달에 속하는지 확인한다.
     *
     * 직원이 직접 넣는 경로에만 적용한다. 관리자가 대신 등록하는 경로(전화로 받은 휴무 등)는
     * 예외 상황을 처리하는 자리라 막지 않는다.
     */
    private void validateNextMonthOnly(Long companyId, LocalDate date) {
        if (date == null) {
            return;
        }
        boolean restricted = vacationDeadlineSettingRepository.findByCompanyId(companyId)
                .map(s -> Boolean.TRUE.equals(s.getNextMonthOnly()))
                .orElse(false);
        if (!restricted) {
            return;
        }

        YearMonth nextMonth = YearMonth.from(LocalDate.now()).plusMonths(1);
        if (!YearMonth.from(date).equals(nextMonth)) {
            throw new IllegalArgumentException(
                    String.format("지금은 %d년 %d월 휴무만 신청할 수 있습니다.", nextMonth.getYear(), nextMonth.getMonthValue()));
        }
    }
}
