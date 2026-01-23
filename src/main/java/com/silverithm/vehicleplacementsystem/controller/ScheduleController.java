package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ScheduleDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleRequestDTO;
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

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class ScheduleController {

    private final ScheduleService scheduleService;

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
            String authorId = authentication != null ? authentication.getName() : "unknown";
            String authorName = "관리자"; // 필요시 인증 정보에서 이름 추출

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
