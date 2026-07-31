package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ScheduleDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    /**
     * 일정 목록 조회
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSchedules(
            @RequestParam Long companyId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long labelId,
            @RequestParam(required = false) String searchQuery) {

        try {
            log.info("[Schedule API] 일정 목록 조회: companyId={}, {} ~ {}", companyId, startDate, endDate);

            LocalDate start = null;
            LocalDate end = null;

            if (startDate != null && !startDate.isEmpty()) {
                start = LocalDate.parse(startDate);
            }
            if (endDate != null && !endDate.isEmpty()) {
                end = LocalDate.parse(endDate);
            }

            List<ScheduleDTO> schedules = scheduleService.getSchedules(
                    companyId, start, end, category, labelId, searchQuery);
            Map<String, Long> stats = scheduleService.getStats(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "schedules", schedules,
                            "stats", stats
                    ));

        } catch (DateTimeParseException e) {
            log.error("[Schedule API] 날짜 파싱 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "날짜 형식이 올바르지 않습니다."));
        } catch (Exception e) {
            log.error("[Schedule API] 일정 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "일정 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 일정 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSchedule(@PathVariable Long id) {

        try {
            log.info("[Schedule API] 일정 상세 조회: id={}", id);

            ScheduleDTO schedule = scheduleService.getSchedule(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("schedule", schedule));

        } catch (Exception e) {
            log.error("[Schedule API] 일정 상세 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "일정 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 일정 등록
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSchedule(
            @RequestParam Long companyId,
            @Valid @RequestBody ScheduleRequestDTO request,
            Authentication authentication) {

        try {
            log.info("[Schedule API] 일정 등록: companyId={}, title={}", companyId, request.getTitle());

            // 인증 정보에서 작성자 정보 추출
            String authName = authentication != null ? authentication.getName() : "unknown";
            String authorId = resolveAuthorEmail(authName);
            String authorName = resolveAuthorName(authName);

            ScheduleDTO schedule = scheduleService.createSchedule(companyId, authorId, authorName, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "schedule", schedule,
                            "message", "일정이 등록되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Schedule API] 일정 등록 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "일정 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 일정 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequestDTO request) {

        try {
            log.info("[Schedule API] 일정 수정: id={}", id);

            ScheduleDTO schedule = scheduleService.updateSchedule(id, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "schedule", schedule,
                            "message", "일정이 수정되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Schedule API] 일정 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "일정 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 일정 수행완료 상태 변경 (진행도 체크)
     */
    @PutMapping("/{id}/completion")
    public ResponseEntity<Map<String, Object>> updateScheduleCompletion(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        try {
            boolean completed = Boolean.TRUE.equals(body.get("completed"))
                    || "true".equalsIgnoreCase(String.valueOf(body.get("completed")));

            String authName = authentication != null ? authentication.getName() : null;
            if (authName == null) {
                return ResponseEntity.status(401)
                        .headers(getCorsHeaders())
                        .body(Map.of("error", "인증 정보가 필요합니다."));
            }

            String userId = resolveAuthorEmail(authName);
            String userName = resolveAuthorName(authName);
            boolean isAdmin = resolveIsScheduleManager(authName);

            log.info("[Schedule API] 일정 수행완료 변경: id={}, completed={}, user={}", id, completed, userId);

            ScheduleDTO schedule = scheduleService.updateCompletion(
                    id, completed, userId, userName, resolveMemberId(authName), isAdmin);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "schedule", schedule,
                            "message", completed ? "수행완료로 변경되었습니다." : "수행완료가 해제되었습니다."
                    ));

        } catch (IllegalStateException e) {
            log.warn("[Schedule API] 일정 수행완료 권한 없음: id={}, {}", id, e.getMessage());
            return ResponseEntity.status(403)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Schedule API] 일정 수행완료 변경 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "수행완료 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 할 일(담당자 업무) ====================

    /**
     * 할 일 목록 조회
     */
    @GetMapping("/{id}/tasks")
    public ResponseEntity<Map<String, Object>> getTasks(@PathVariable Long id) {
        try {
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("tasks", scheduleService.getTasks(id)));
        } catch (Exception e) {
            log.error("[Schedule API] 할 일 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "할 일 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 할 일 추가 (기관 구성원 누구나)
     */
    @PostMapping("/{id}/tasks")
    public ResponseEntity<Map<String, Object>> createTask(
            @PathVariable Long id,
            @RequestBody ScheduleTaskRequestDTO request,
            Authentication authentication) {
        try {
            String authName = requireAuthName(authentication);
            ScheduleTaskDTO task = scheduleService.createTask(
                    id, request, resolveAuthorEmail(authName), resolveAuthorName(authName));

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "task", task, "message", "할 일이 추가되었습니다."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Schedule API] 할 일 추가 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "할 일 추가 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 할 일 내용·담당자 수정
     */
    @PutMapping("/{id}/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> updateTask(
            @PathVariable Long id,
            @PathVariable Long taskId,
            @RequestBody ScheduleTaskRequestDTO request,
            Authentication authentication) {
        try {
            String authName = requireAuthName(authentication);
            ScheduleTaskDTO task = scheduleService.updateTask(
                    taskId, request, resolveAuthorEmail(authName),
                    resolveMemberId(authName), resolveIsScheduleManager(authName));

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "task", task, "message", "할 일이 수정되었습니다."));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).headers(getCorsHeaders()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Schedule API] 할 일 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "할 일 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 할 일 수행완료 토글 (담당자 본인 또는 관리자)
     */
    @PutMapping("/{id}/tasks/{taskId}/completion")
    public ResponseEntity<Map<String, Object>> updateTaskCompletion(
            @PathVariable Long id,
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            String authName = requireAuthName(authentication);
            boolean completed = Boolean.TRUE.equals(body.get("completed"))
                    || "true".equalsIgnoreCase(String.valueOf(body.get("completed")));

            ScheduleTaskDTO task = scheduleService.updateTaskCompletion(
                    taskId, completed, resolveAuthorEmail(authName), resolveAuthorName(authName),
                    resolveMemberId(authName), resolveIsScheduleManager(authName));

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "task", task,
                            "message", completed ? "수행완료로 변경되었습니다." : "수행완료가 해제되었습니다."));

        } catch (IllegalStateException e) {
            log.warn("[Schedule API] 할 일 완료 권한 없음: taskId={}, {}", taskId, e.getMessage());
            return ResponseEntity.status(403).headers(getCorsHeaders()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Schedule API] 할 일 완료 변경 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "수행완료 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 할 일 삭제
     */
    @DeleteMapping("/{id}/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> deleteTask(
            @PathVariable Long id,
            @PathVariable Long taskId,
            Authentication authentication) {
        try {
            String authName = requireAuthName(authentication);
            scheduleService.deleteTask(taskId, resolveAuthorEmail(authName),
                    resolveMemberId(authName), resolveIsScheduleManager(authName));

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "message", "할 일이 삭제되었습니다."));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).headers(getCorsHeaders()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Schedule API] 할 일 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "할 일 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 내 할 일 목록 (대시보드 위젯 / 내 업무 필터)
     */
    @GetMapping("/my-tasks")
    public ResponseEntity<Map<String, Object>> getMyTasks(
            @RequestParam Long companyId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {
        try {
            String authName = requireAuthName(authentication);
            Long memberId = resolveMemberId(authName);

            LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : null;
            LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : null;

            List<ScheduleTaskDTO> tasks = scheduleService.getMyTasks(companyId, memberId, start, end);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("tasks", tasks, "memberId", memberId != null ? memberId : -1));

        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "날짜 형식이 올바르지 않습니다."));
        } catch (Exception e) {
            log.error("[Schedule API] 내 할 일 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "내 할 일 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 일정 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSchedule(@PathVariable Long id) {

        try {
            log.info("[Schedule API] 일정 삭제: id={}", id);

            scheduleService.deleteSchedule(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "일정이 삭제되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Schedule API] 일정 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "일정 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 카테고리 목록 조회
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        try {
            log.info("[Schedule API] 카테고리 목록 조회");

            List<Map<String, String>> categories = List.of(
                    Map.of("value", "MEETING", "displayName", "회의"),
                    Map.of("value", "EVENT", "displayName", "행사"),
                    Map.of("value", "TRAINING", "displayName", "교육"),
                    Map.of("value", "OTHER", "displayName", "기타")
            );

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "categories", categories
                    ));

        } catch (Exception e) {
            log.error("[Schedule API] 카테고리 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "카테고리 목록 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 알림 타입 목록 조회
     */
    @GetMapping("/reminders")
    public ResponseEntity<Map<String, Object>> getReminders() {
        try {
            log.info("[Schedule API] 알림 타입 목록 조회");

            List<Map<String, String>> reminders = List.of(
                    Map.of("value", "NONE", "displayName", "알림 없음"),
                    Map.of("value", "TEN_MIN", "displayName", "10분 전"),
                    Map.of("value", "THIRTY_MIN", "displayName", "30분 전"),
                    Map.of("value", "ONE_HOUR", "displayName", "1시간 전"),
                    Map.of("value", "ONE_DAY", "displayName", "1일 전")
            );

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "reminders", reminders
                    ));

        } catch (Exception e) {
            log.error("[Schedule API] 알림 타입 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "알림 타입 목록 조회 중 오류가 발생했습니다."));
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }

    /**
     * authentication.getName()은 관리자=email, 직원=username을 반환.
     * authorId를 항상 email로 통일하여 프론트엔드와 비교 가능하게 한다.
     */
    private String resolveAuthorEmail(String authName) {
        // 직원: username으로 검색 → email 반환
        Optional<Member> member = memberRepository.findByUsername(authName);
        if (member.isEmpty()) {
            member = memberRepository.findByEmail(authName);
        }
        if (member.isPresent()) {
            return member.get().getEmail();
        }
        // 관리자: authName이 이미 email
        return authName;
    }

    private String resolveAuthorName(String authName) {
        // 직원: username 또는 email로 검색
        Optional<Member> member = memberRepository.findByUsername(authName);
        if (member.isEmpty()) {
            member = memberRepository.findByEmail(authName);
        }
        if (member.isPresent()) {
            return member.get().getName();
        }
        // 관리자: email로 검색
        Optional<AppUser> appUser = userRepository.findByEmail(authName);
        if (appUser.isPresent() && appUser.get().getUsername() != null) {
            return appUser.get().getUsername();
        }
        return authName;
    }

    /**
     * 일정 관리 권한 보유 여부.
     * Member로 조회되지 않으면 관리자 계정(AppUser)이므로 권한이 있다고 본다.
     */
    private boolean resolveIsScheduleManager(String authName) {
        Optional<Member> member = memberRepository.findByUsername(authName);
        if (member.isEmpty()) {
            member = memberRepository.findByEmail(authName);
        }
        if (member.isEmpty()) {
            return true; // 관리자 계정
        }
        Member found = member.get();
        if (found.getRole() == Member.Role.ADMIN) {
            return true;
        }
        return found.getPermissions() != null && found.getPermissions().contains("SCHEDULE_MANAGE");
    }

    private String requireAuthName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("인증 정보가 필요합니다.");
        }
        return authentication.getName();
    }

    /** 로그인 주체의 member id. 관리자 계정(AppUser)이면 null */
    private Long resolveMemberId(String authName) {
        Optional<Member> member = memberRepository.findByUsername(authName);
        if (member.isEmpty()) {
            member = memberRepository.findByEmail(authName);
        }
        return member.map(Member::getId).orElse(null);
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
}
