package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.service.MemberService;
import com.silverithm.vehicleplacementsystem.service.VacationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vacation")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class VacationController {
    
    private final VacationService vacationService;
    private final MemberService memberService;
    
    @GetMapping("/calendar")
    public ResponseEntity<VacationCalendarResponseDTO> getVacationCalendar(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "all") String roleFilter,
            @RequestParam(required = false) String nameFilter,
            @RequestParam Long companyId) {
        
        try {
            log.info("[Vacation API] 휴가 캘린더 요청: companyId={}, {} ~ {}, role: {}, name: {}", 
                    companyId, startDate, endDate, roleFilter, nameFilter);
            
            // 날짜 형식 검증
            if (!isValidDateFormat(startDate) || !isValidDateFormat(endDate)) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .build();
            }
            
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            VacationCalendarResponseDTO response = vacationService.getVacationCalendar(
                    companyId, start, end, roleFilter, nameFilter);
            
            log.info("[Vacation API] 휴가 캘린더 응답 완료: 날짜 수={}", 
                    response.getDates().size());
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(response);
                    
        } catch (DateTimeParseException e) {
            log.error("[Vacation API] 날짜 파싱 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .build();
        } catch (Exception e) {
            log.error("[Vacation API] 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @GetMapping("/date/{date}")
    public ResponseEntity<VacationDateResponseDTO> getVacationForDate(
            @PathVariable String date,
            @RequestParam(defaultValue = "caregiver") String role,
            @RequestParam(required = false) String nameFilter,
            @RequestParam Long companyId) {
        
        try {
            log.info("[Vacation API] 날짜 {} 휴가 요청: companyId={}, role={}, nameFilter={}", date, companyId, role, nameFilter);
            
            // 날짜 형식 검증
            if (!isValidDateFormat(date)) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .build();
            }
            
            LocalDate localDate = LocalDate.parse(date);
            
            VacationDateResponseDTO response = vacationService.getVacationForDate(
                    companyId, localDate, role, nameFilter);
            
            log.info("[Vacation API] 날짜 {} 응답 완료: {}명의 휴가자", date, response.getTotalVacationers());
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(response);
                    
        } catch (DateTimeParseException e) {
            log.error("[Vacation API] 날짜 파싱 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .build();
        } catch (Exception e) {
            log.error("[Vacation API] 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> createVacationRequest(
            @Valid @RequestBody VacationCreateRequestDTO requestDTO,
            @RequestParam Long companyId) {
        
        try {
            log.info("[Vacation API] 휴가 신청 생성 요청: companyId={}, {}, 날짜: {}", 
                    companyId, requestDTO.getUserName(), requestDTO.getDate());
            
            VacationRequestDTO result = vacationService.createVacationRequest(companyId, requestDTO);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "data", result
                    ));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Vacation API] 유효성 검증 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 신청 생성 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴가 신청을 처리하는 중 오류가 발생했습니다."));
        }
    }
    
    @PostMapping("/admin/submit-for-member")
    public ResponseEntity<Map<String, Object>> createVacationRequestByAdmin(
            @Valid @RequestBody AdminVacationCreateRequestDTO requestDTO,
            @RequestParam Long companyId) {
        
        try {
            log.info("[Vacation API] 관리자가 직원 대신 휴가 신청: companyId={}, memberId={}, 날짜: {}", 
                    companyId, requestDTO.getMemberId(), requestDTO.getDate());
            
            VacationRequestDTO result = vacationService.createVacationRequestByAdmin(companyId, requestDTO);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "직원의 휴무가 성공적으로 등록되었습니다",
                            "data", result
                    ));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Vacation API] 관리자 휴가 신청 유효성 검증 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 관리자 휴가 신청 생성 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴가 신청을 처리하는 중 오류가 발생했습니다."));
        }
    }
    
    @PutMapping("/approve/{id}")
    public ResponseEntity<Map<String, String>> approveVacation(@PathVariable Long id) {
        try {
            log.info("[Vacation API] 휴가 승인 요청: ID={}", id);
            
            vacationService.approveVacation(id);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "휴가 신청이 승인되었습니다"));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Vacation API] 휴가 승인 오류: {}", e.getMessage());
            return ResponseEntity.notFound()
                    .headers(getCorsHeaders())
                    .build();
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 승인 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴가 승인 중 오류가 발생했습니다"));
        }
    }
    
    @PutMapping("/bulk-approve")
    public ResponseEntity<VacationBulkActionResponseDTO> bulkApproveVacations(
            @Valid @RequestBody VacationBulkActionRequestDTO requestDTO) {
        try {
            log.info("[Vacation API] 휴가 일괄 승인 요청: {}건", requestDTO.getVacationIds().size());
            
            VacationBulkActionResponseDTO response = vacationService.bulkApproveVacations(requestDTO.getVacationIds());
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(response);
                    
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 일괄 승인 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(VacationBulkActionResponseDTO.builder()
                            .message("휴가 일괄 승인 중 오류가 발생했습니다")
                            .build());
        }
    }
    
    @PutMapping("/reject/{id}")
    public ResponseEntity<Map<String, String>> rejectVacation(@PathVariable Long id) {
        try {
            log.info("[Vacation API] 휴가 거부 요청: ID={}", id);
            
            vacationService.rejectVacation(id);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "휴가 신청이 거부되었습니다"));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Vacation API] 휴가 거부 오류: {}", e.getMessage());
            return ResponseEntity.notFound()
                    .headers(getCorsHeaders())
                    .build();
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 거부 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴가 거부 중 오류가 발생했습니다"));
        }
    }
    
    @PutMapping("/bulk-reject")
    public ResponseEntity<VacationBulkActionResponseDTO> bulkRejectVacations(
            @Valid @RequestBody VacationBulkActionRequestDTO requestDTO) {
        try {
            log.info("[Vacation API] 휴가 일괄 거부 요청: {}건", requestDTO.getVacationIds().size());
            
            VacationBulkActionResponseDTO response = vacationService.bulkRejectVacations(requestDTO.getVacationIds());
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(response);
                    
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 일괄 거부 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(VacationBulkActionResponseDTO.builder()
                            .message("휴가 일괄 거부 중 오류가 발생했습니다")
                            .build());
        }
    }
    
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, String>> deleteVacation(
            @PathVariable Long id,
            @RequestBody VacationDeleteRequestDTO deleteRequest) {
        
        try {
            log.info("[Vacation API] 휴가 삭제 요청: ID={}, 관리자권한={}", 
                    id, deleteRequest.getIsAdmin());
            
            vacationService.deleteVacation(id, deleteRequest);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "휴가 신청이 삭제되었습니다"));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Vacation API] 휴가 삭제 오류: {}", e.getMessage());
            
            if (e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.notFound()
                        .headers(getCorsHeaders())
                        .build();
            } else if (e.getMessage().contains("비밀번호")) {
                return ResponseEntity.status(403)
                        .headers(getCorsHeaders())
                        .body(Map.of("error", e.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .body(Map.of("error", e.getMessage()));
            }
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 삭제 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴가 삭제 중 오류가 발생했습니다"));
        }
    }
    
    @GetMapping("/requests")
    public ResponseEntity<Map<String, List<VacationRequestDTO>>> getAllVacationRequests(@RequestParam Long companyId) {
        try {
            log.info("[Vacation API] 모든 휴가 요청 조회: companyId={}", companyId);
            
            List<VacationRequestDTO> requests = vacationService.getAllVacationRequests(companyId);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("requests", requests));
                    
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 요청 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @GetMapping("/limits")
    public ResponseEntity<Map<String, List<VacationLimitDTO>>> getVacationLimits(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam Long companyId) {
        
        try {
            // 날짜 형식 검증
            if (!isValidDateFormat(start) || !isValidDateFormat(end)) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .build();
            }
            
            log.info("[Vacation API] 휴가 제한 조회: companyId={}, {} ~ {}", companyId, start, end);
            
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            
            List<VacationLimitDTO> limits = vacationService.getVacationLimits(companyId, startDate, endDate);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("limits", limits));
                    
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 제한 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @PostMapping("/limits")
    public ResponseEntity<Map<String, Object>> saveVacationLimits(
            @RequestBody VacationLimitRequestDTO requestDTO,
            @RequestParam Long companyId) {
        
        try {
            log.info("[Vacation API] 휴가 제한 저장 요청: companyId={}, {}건", companyId, requestDTO.getLimits().size());
            
            List<VacationLimitDTO> savedLimits = vacationService.saveVacationLimitsV2(companyId, requestDTO);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", savedLimits.size() + "개의 휴가 제한이 저장되었습니다.",
                            "limits", savedLimits
                    ));
                    
        } catch (Exception e) {
            log.error("[Vacation API] 휴가 제한 저장 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "error", "휴가 제한 저장 중 오류가 발생했습니다",
                            "message", e.getMessage()
                    ));
        }
    }
    
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }
    
    /**
     * 휴무 기간 타입 목록 조회 (연차/반차)
     */
    @GetMapping("/durations")
    public ResponseEntity<Map<String, Object>> getVacationDurations() {
        try {
            log.info("[Vacation API] 휴무 기간 타입 목록 조회");
            
            List<Map<String, Object>> durations = new ArrayList<>();
            for (VacationRequest.VacationDuration duration : VacationRequest.VacationDuration.values()) {
                Map<String, Object> durationInfo = new HashMap<>();
                durationInfo.put("value", duration.name());
                durationInfo.put("displayName", duration.getDisplayName());
                durationInfo.put("description", duration.getDescription());
                durationInfo.put("days", duration.getDays());
                durations.add(durationInfo);
            }
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "data", durations
                    ));
                    
        } catch (Exception e) {
            log.error("[Vacation API] 휴무 기간 타입 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴무 기간 타입 조회 중 오류가 발생했습니다."));
        }
    }
    
    // 멤버 개인용 휴무 API
    
    /**
     * 내 휴무 신청 전체 조회
     */
    @GetMapping("/my/requests")
    public ResponseEntity<Map<String, Object>> getMyVacationRequests(
            @RequestParam Long companyId,
            @RequestParam String userName,
            @RequestParam String userId) {
        
        try {
            log.info("[Vacation API] 개인 휴무 신청 조회: companyId={}, userName={}, userId={}", 
                    companyId, userName, userId);
            
            List<VacationRequestDTO> myVacations = vacationService.getMyVacationRequests(companyId, userId, userName);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "data", myVacations,
                            "total", myVacations.size()
                    ));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Vacation API] 개인 휴무 조회 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 개인 휴무 조회 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴무 신청 조회 중 오류가 발생했습니다."));
        }
    }
    
    /**
     * 내 휴무 신청 삭제
     */
    @DeleteMapping("/my/requests/{vacationId}")
    public ResponseEntity<Map<String, String>> deleteMyVacationRequest(
            @PathVariable Long vacationId,
            @RequestParam String userName,
            @RequestParam String userId) {
        
        try {
            log.info("[Vacation API] 개인 휴무 삭제 요청: vacationId={}, userName={}, userId={}", 
                    vacationId, userName, userId);
            
            vacationService.deleteMyVacationRequest(vacationId, userId, userName);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "휴무 신청이 삭제되었습니다"));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Vacation API] 개인 휴무 삭제 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 개인 휴무 삭제 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "휴무 삭제 중 오류가 발생했습니다"));
        }
    }
    
    private HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return headers;
    }
    
    private boolean isValidDateFormat(String dateString) {
        if (dateString == null || !dateString.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return false;
        }
        
        try {
            LocalDate.parse(dateString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
} 