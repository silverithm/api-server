package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.VacationBulkActionRequestDTO;
import com.silverithm.vehicleplacementsystem.service.VacationBulkDeleteService;
import com.silverithm.vehicleplacementsystem.service.VacationDeadlineDateService;
import com.silverithm.vehicleplacementsystem.service.VacationEventService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 근무조정 계획 보조 API — 월별 마감일 지정과 중요 행사.
 *
 * 휴무 신청/승인 본류(VacationController)와 라이프사이클이 달라 별도 컨트롤러로 둔다.
 */
@RestController
@RequestMapping("/api/vacation")
@RequiredArgsConstructor
@Slf4j
public class VacationPlanningController {

    private final VacationDeadlineDateService deadlineDateService;
    private final VacationEventService eventService;
    private final VacationBulkDeleteService bulkDeleteService;

    // ── 일괄 삭제 ────────────────────────────────────────

    /** 조회 기간 안의 휴무를 한 번에 삭제한다 (승인·거절과 달리 되돌릴 수 없다) */
    @PutMapping("/bulk-delete")
    public ResponseEntity<?> bulkDeleteVacations(@RequestBody VacationBulkActionRequestDTO request) {
        try {
            return ResponseEntity.ok(bulkDeleteService.bulkDeleteVacations(request.getVacationIds()));
        } catch (Exception e) {
            log.error("[Vacation API] 휴무 일괄 삭제 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "일괄 삭제 중 오류가 발생했습니다"));
        }
    }

    // ── 월별 마감일 지정 ──────────────────────────────────

    /** 기관이 지정한 월별 마감일 전체 — {"2026-08": "2026-08-16", ...} */
    @GetMapping("/deadline-dates")
    public ResponseEntity<?> getDeadlineDates(@RequestParam Long companyId) {
        try {
            return ResponseEntity.ok(Map.of("dates", deadlineDateService.getDeadlineDates(companyId)));
        } catch (Exception e) {
            log.error("[Vacation API] 월별 마감일 조회 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "마감일 조회 중 오류가 발생했습니다"));
        }
    }

    public record DeadlineDateRequest(String targetMonth, String deadlineDate) {
    }

    /** 특정 달의 마감일 지정/해제 (deadlineDate가 null이면 해제 → 매월 고정일로 되돌아간다) */
    @PostMapping("/deadline-dates")
    public ResponseEntity<?> saveDeadlineDate(@RequestParam Long companyId,
                                              @RequestBody DeadlineDateRequest request) {
        try {
            LocalDate date = request.deadlineDate() == null || request.deadlineDate().isBlank()
                    ? null : LocalDate.parse(request.deadlineDate());
            deadlineDateService.saveDeadlineDate(companyId, request.targetMonth(), date);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 월별 마감일 저장 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "마감일 저장 중 오류가 발생했습니다"));
        }
    }

    // ── 중요 행사 ────────────────────────────────────────

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(@RequestParam Long companyId,
                                       @RequestParam String startDate,
                                       @RequestParam String endDate) {
        try {
            List<Map<String, Object>> events = eventService.getEvents(
                    companyId, LocalDate.parse(startDate), LocalDate.parse(endDate));
            return ResponseEntity.ok(Map.of("events", events));
        } catch (Exception e) {
            log.error("[Vacation API] 행사 조회 오류:", e);
            // 달력이 깨지지 않도록 빈 목록으로 떨어뜨린다
            return ResponseEntity.ok(Map.of("events", List.of()));
        }
    }

    public record EventRequest(String title, String description, String startDate, String endDate,
                               Boolean warnOnRequest) {
    }

    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestParam Long companyId,
                                         @RequestBody EventRequest request,
                                         Authentication authentication) {
        try {
            Long id = eventService.createEvent(companyId,
                    request.title(),
                    request.description(),
                    LocalDate.parse(request.startDate()),
                    LocalDate.parse(request.endDate()),
                    !Boolean.FALSE.equals(request.warnOnRequest()),
                    authentication != null ? authentication.getName() : null);
            return ResponseEntity.ok(Map.of("id", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 행사 등록 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "행사 등록 중 오류가 발생했습니다"));
        }
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<?> updateEvent(@PathVariable Long eventId,
                                         @RequestParam Long companyId,
                                         @RequestBody EventRequest request) {
        try {
            eventService.updateEvent(eventId, companyId,
                    request.title(),
                    request.description(),
                    LocalDate.parse(request.startDate()),
                    LocalDate.parse(request.endDate()),
                    !Boolean.FALSE.equals(request.warnOnRequest()));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 행사 수정 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "행사 수정 중 오류가 발생했습니다"));
        }
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long eventId, @RequestParam Long companyId) {
        try {
            eventService.deleteEvent(eventId, companyId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Vacation API] 행사 삭제 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "행사 삭제 중 오류가 발생했습니다"));
        }
    }
}
