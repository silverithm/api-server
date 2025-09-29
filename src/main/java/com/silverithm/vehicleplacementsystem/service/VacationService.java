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
import com.silverithm.vehicleplacementsystem.repository.VacationRequestRepository;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacationService {

    private final VacationRequestRepository vacationRequestRepository;
    private final VacationLimitRepository vacationLimitRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

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

        // 역할별 필터링
        if (!"all".equals(roleFilter)) {
            VacationRequest.Role role = VacationRequest.Role.valueOf(roleFilter.toUpperCase());
            vacations = vacations.stream()
                    .filter(v -> v.getRole() == role || v.getRole() == VacationRequest.Role.ALL)
                    .collect(Collectors.toList());
            limits = limits.stream()
                    .filter(l -> l.getRole() == role)
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
                    .map(VacationRequestDTO::fromEntity)
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
                dateInfo.setMaxPeople(limit.getMaxPeople());
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

        // 역할별 필터링
        if (!"all".equals(role)) {
            VacationRequest.Role roleEnum = VacationRequest.Role.valueOf(role.toUpperCase());
            vacations = vacations.stream()
                    .filter(v -> v.getRole() == roleEnum)
                    .collect(Collectors.toList());
        }

        // 이름별 필터링
        if (nameFilter != null && !nameFilter.trim().isEmpty()) {
            vacations = vacations.stream()
                    .filter(v -> nameFilter.equals(v.getUserName()))
                    .collect(Collectors.toList());
        }

        List<VacationRequestDTO> vacationDTOs = vacations.stream()
                .map(VacationRequestDTO::fromEntity)
                .collect(Collectors.toList());

        // 거부되지 않은 휴가만 카운트
        int totalVacationers = (int) vacations.stream()
                .filter(v -> v.getStatus() != VacationRequest.VacationStatus.REJECTED)
                .count();

        // 회사별 휴가 제한 조회
        Integer maxPeople = null;
        if (!"all".equals(role)) {
            VacationRequest.Role roleEnum = VacationRequest.Role.valueOf(role.toUpperCase());
            maxPeople = vacationLimitRepository.findByCompanyAndDateAndRole(company, date, roleEnum)
                    .map(VacationLimit::getMaxPeople)
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

        // Role enum 변환
        VacationRequest.Role role;
        try {
            role = VacationRequest.Role.valueOf(requestDTO.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 직원 역할입니다: " + requestDTO.getRole());
        }

        // userId 생성 (없으면 자동 생성)
        String userId = requestDTO.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            userId = "user_" + System.currentTimeMillis();
        }

        VacationRequest entity = VacationRequest.builder()
                .userName(requestDTO.getUserName())
                .date(requestDTO.getDate())
                .reason(requestDTO.getReason())
                .role(role)
                .type(requestDTO.getType() != null ? requestDTO.getType() : "regular")
                .duration(requestDTO.getDuration() != null ? requestDTO.getDuration().name() : "FULL_DAY")
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

    @Transactional
    public void approveVacation(Long id) {
        log.info("[Vacation Service] 휴가 승인: ID={}", id);

        VacationRequest vacation = vacationRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 휴가 신청을 찾을 수 없습니다: " + id));

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

        vacationRequestRepository.delete(vacation);

        log.info("[Vacation Service] 휴가 삭제 완료: ID={}", id);
    }

    public List<VacationRequestDTO> getAllVacationRequests(Long companyId) {
        log.info("[Vacation Service] 모든 휴가 요청 조회: companyId={}", companyId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        List<VacationRequest> vacations = vacationRequestRepository.findByCompanyOrderByCreatedAtDesc(company);

        log.info("[Vacation Service] 휴가 요청 조회 완료: 회사 {}, {}건", company.getName(), vacations.size());

        return vacations.stream()
                .map(VacationRequestDTO::fromEntity)
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
                VacationRequest.Role role = VacationRequest.Role.valueOf(
                        (limitDTO.getRole() != null ? limitDTO.getRole() : "caregiver").toUpperCase());

                // 기존 제한이 있는지 확인 (회사별로)
                VacationLimit existingLimit = vacationLimitRepository.findByCompanyAndDateAndRole(company, date, role)
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
        List<VacationRequest.Role> roles = new ArrayList<>();
        Map<String, VacationLimitRequestDTO.VacationLimitCreateDTO> requestMap = new HashMap<>();

        for (VacationLimitRequestDTO.VacationLimitCreateDTO limitDTO : requestDTO.getLimits()) {
            try {
                LocalDate date = LocalDate.parse(limitDTO.getDate());
                VacationRequest.Role role = VacationRequest.Role.valueOf(
                        (limitDTO.getRole() != null ? limitDTO.getRole() : "caregiver").toUpperCase());

                dates.add(date);
                roles.add(role);
                requestMap.put(date + "_" + role, limitDTO);
            } catch (Exception e) {
                log.error("[Vacation Service] 휴가 제한 파싱 실패: {}", limitDTO, e);
            }
        }

        // 2. 한 번에 모든 기존 데이터 조회
        Map<String, VacationLimit> existingMap = vacationLimitRepository
                .findByCompanyAndDateInAndRoleIn(company, dates, roles)
                .stream()
                .collect(Collectors.toMap(
                        limit -> limit.getDate() + "_" + limit.getRole(),
                        Function.identity()
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
                String[] parts = key.split("_");
                LocalDate date = LocalDate.parse(parts[0]);
                VacationRequest.Role role = VacationRequest.Role.valueOf(parts[1]);

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

        for (String adminToken : adminFcmTokens) {
            try {
                notificationService.sendVacationSubmittedNotification(
                        adminToken,
                        "admin", // 관리자 사용자 ID
                        "관리자", // 관리자 이름
                        vacation.getUserName(),
                        vacation.getDate().toString(),
                        vacation.getId()
                );
            } catch (Exception e) {
                log.error("[Vacation Service] 관리자 알림 전송 실패: {}", e.getMessage());
            }
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
            log.error("[Vacation Service] FCM 토큰 조회 중 오류 발생: userId={}, userName={}", userId, userName, e);
            throw new CustomException("FCM 토큰 조회 중 오류 발생: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> getAdminFcmTokens(Company company) {
        log.debug("[Vacation Service] 관리자 FCM 토큰 목록 조회");

        try {
            // ADMIN, MANAGER 권한을 가진 Member들의 FCM 토큰 조회
            List<Member> adminMembers = memberRepository.findByRoleInAndFcmTokenIsNotNull(
                    List.of(Member.Role.ADMIN)
            );

            List<AppUser> adminUsers = company.getUsers();

            List<String> adminTokens = adminUsers.stream()
                    .map(AppUser::getFcmToken)
                    .filter(token -> token != null && !token.isEmpty())
                    .collect(Collectors.toList());

            log.debug("[Vacation Service] 관리자 FCM 토큰 조회 완료: {} 개", adminTokens.size());
            return adminTokens;
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
        
        // 직원의 역할을 VacationRequest.Role로 변환
        VacationRequest.Role vacationRole;
        switch (member.getRole()) {
            case CAREGIVER:
                vacationRole = VacationRequest.Role.CAREGIVER;
                break;
            case OFFICE:
                vacationRole = VacationRequest.Role.OFFICE;
                break;
            default:
                vacationRole = VacationRequest.Role.ALL;
                break;
        }
        
        VacationRequest entity = VacationRequest.builder()
                .userName(member.getName())
                .date(requestDTO.getDate())
                .reason(requestDTO.getReason() != null ? requestDTO.getReason() : "관리자 대신 신청")
                .role(vacationRole)
                .type(requestDTO.getType() != null ? requestDTO.getType() : "admin_created")
                .duration(requestDTO.getDuration() != null ? requestDTO.getDuration().name() : "FULL_DAY")
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
            log.warn("[Vacation Service] 직원 FCM 토큰을 찾을 수 없음: {}", member.getName());
        }
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

        log.info("[Vacation Service] 개인 휴무 신청 조회 완료: 회사 {}, 사용자 {}({}), {}건",
                company.getName(), userName, userId, myVacations.size());

        return myVacations.stream()
                .map(VacationRequestDTO::fromEntity)
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
} 