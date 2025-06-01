package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.VacationLimit;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.repository.VacationLimitRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final NotificationService notificationService;
    
    public VacationCalendarResponseDTO getVacationCalendar(
            LocalDate startDate, 
            LocalDate endDate, 
            String roleFilter, 
            String nameFilter) {
        
        log.info("[Vacation Service] 휴가 캘린더 요청: {} ~ {}, role={}, name={}", 
                startDate, endDate, roleFilter, nameFilter);
        
        // 휴가 신청 데이터 조회
        List<VacationRequest> vacations = vacationRequestRepository.findByDateBetween(startDate, endDate);
        List<VacationLimit> limits = vacationLimitRepository.findByDateBetween(startDate, endDate);
        
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
        
        log.info("[Vacation Service] 응답 완료: 날짜 수={}", dateMap.size());
        
        return VacationCalendarResponseDTO.builder()
                .dates(dateMap)
                .build();
    }
    
    public VacationDateResponseDTO getVacationForDate(LocalDate date, String role, String nameFilter) {
        log.info("[Vacation Service] 날짜 {} 휴가 요청: role={}, nameFilter={}", date, role, nameFilter);
        
        // 휴가 신청 조회
        List<VacationRequest> vacations = vacationRequestRepository.findByDate(date);
        
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
        
        // 휴가 제한 조회
        Integer maxPeople = null;
        if (!"all".equals(role)) {
            VacationRequest.Role roleEnum = VacationRequest.Role.valueOf(role.toUpperCase());
            maxPeople = vacationLimitRepository.findByDateAndRole(date, roleEnum)
                    .map(VacationLimit::getMaxPeople)
                    .orElse(3);
        }
        
        log.info("[Vacation Service] 날짜 {} 응답: {}명의 휴가자, 제한={}", date, totalVacationers, maxPeople);
        
        return VacationDateResponseDTO.builder()
                .date(date.toString())
                .vacations(vacationDTOs)
                .totalVacationers(totalVacationers)
                .maxPeople(maxPeople)
                .build();
    }
    
    @Transactional
    public VacationRequestDTO createVacationRequest(VacationCreateRequestDTO requestDTO) {
        log.info("[Vacation Service] 휴가 신청 생성: {}, 날짜: {}", requestDTO.getUserName(), requestDTO.getDate());
        
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
                .password(requestDTO.getPassword())
                .type(requestDTO.getType() != null ? requestDTO.getType() : "regular")
                .userId(userId)
                .status(VacationRequest.VacationStatus.PENDING)
                .build();
        
        VacationRequest saved = vacationRequestRepository.save(entity);
        
        log.info("[Vacation Service] 휴가 신청 생성 완료: ID={}", saved.getId());
        
        // 관리자에게 알림 전송 (실제 환경에서는 관리자 FCM 토큰을 조회해야 함)
        try {
            sendVacationSubmittedNotificationToAdmins(saved);
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
        
        // 관리자가 아닌 경우 비밀번호 검증
        if (deleteRequest.getIsAdmin() == null || !deleteRequest.getIsAdmin()) {
            if (deleteRequest.getPassword() == null || deleteRequest.getPassword().trim().isEmpty()) {
                throw new IllegalArgumentException("비밀번호가 필요합니다");
            }
            
            if (!vacation.getPassword().equals(deleteRequest.getPassword())) {
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
            }
        }
        
        vacationRequestRepository.delete(vacation);
        
        log.info("[Vacation Service] 휴가 삭제 완료: ID={}", id);
    }
    
    public List<VacationRequestDTO> getAllVacationRequests() {
        log.info("[Vacation Service] 모든 휴가 요청 조회");
        
        List<VacationRequest> vacations = vacationRequestRepository.findAll();
        
        log.info("[Vacation Service] 휴가 요청 조회 완료: {}건", vacations.size());
        
        return vacations.stream()
                .map(VacationRequestDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<VacationLimitDTO> getVacationLimits(LocalDate startDate, LocalDate endDate) {
        log.info("[Vacation Service] 휴가 제한 조회: {} ~ {}", startDate, endDate);
        
        List<VacationLimit> limits = vacationLimitRepository.findByDateBetween(startDate, endDate);
        
        log.info("[Vacation Service] 휴가 제한 조회 완료: {}건", limits.size());
        
        return limits.stream()
                .map(VacationLimitDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public List<VacationLimitDTO> saveVacationLimits(VacationLimitRequestDTO requestDTO) {
        log.info("[Vacation Service] 휴가 제한 저장: {}건", requestDTO.getLimits().size());
        
        List<VacationLimitDTO> savedLimits = new ArrayList<>();
        
        for (VacationLimitRequestDTO.VacationLimitCreateDTO limitDTO : requestDTO.getLimits()) {
            try {
                LocalDate date = LocalDate.parse(limitDTO.getDate());
                VacationRequest.Role role = VacationRequest.Role.valueOf(
                        (limitDTO.getRole() != null ? limitDTO.getRole() : "caregiver").toUpperCase());
                
                // 기존 제한이 있는지 확인
                VacationLimit existingLimit = vacationLimitRepository.findByDateAndRole(date, role)
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
                            .build();
                    VacationLimit saved = vacationLimitRepository.save(newLimit);
                    savedLimits.add(VacationLimitDTO.fromEntity(saved));
                }
                
                log.info("[Vacation Service] 휴가 제한 저장 완료: {}, 최대 {}명", date, limitDTO.getMaxPeople());
                
            } catch (Exception e) {
                log.error("[Vacation Service] 휴가 제한 저장 실패: {}", limitDTO, e);
                // 개별 오류는 로깅만 하고 계속 진행
            }
        }
        
        log.info("[Vacation Service] 휴가 제한 저장 전체 완료: {}건", savedLimits.size());
        
        return savedLimits;
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
    
    private void sendVacationSubmittedNotificationToAdmins(VacationRequest vacation) {
        List<String> adminFcmTokens = getAdminFcmTokens();
        
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
    
    // TODO: 실제 환경에서는 사용자 관리 시스템과 연동하여 FCM 토큰을 조회해야 함
    private String getUserFcmToken(String userId, String userName) {
        // 개발 환경에서는 테스트 토큰 반환
        log.debug("[Vacation Service] 사용자 FCM 토큰 조회: userId={}, userName={}", userId, userName);
        return "test-user-token-" + userId;
    }
    
    // TODO: 실제 환경에서는 관리자 목록과 FCM 토큰을 조회해야 함  
    private List<String> getAdminFcmTokens() {
        // 개발 환경에서는 테스트 토큰 목록 반환
        log.debug("[Vacation Service] 관리자 FCM 토큰 목록 조회");
        return List.of("test-admin-token-1", "test-admin-token-2");
    }
} 