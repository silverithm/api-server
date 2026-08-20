package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ScheduleCategorySettingDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleCategorySettingRequestDTO;
import com.silverithm.vehicleplacementsystem.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 기본 일정 구분(회의·행사·교육·기타)의 기관별 설정 API.
 * 기본 구분은 삭제 대신 숨김만 지원한다 — 기존 일정이 category로 물고 있어서다.
 */
@RestController
@RequestMapping("/api/v1/schedule-categories")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ScheduleCategoryController {

    private final ScheduleService scheduleService;

    /**
     * 기본 구분 4종의 기관별 최종 상태(이름·색·숨김) 조회
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCategorySettings(@RequestParam Long companyId) {
        try {
            List<ScheduleCategorySettingDTO> categories = scheduleService.getCategorySettings(companyId);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "categories", categories
                    ));
        } catch (Exception e) {
            log.error("[ScheduleCategory API] 기본 구분 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "기본 구분 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 기본 구분의 이름·색·숨김 변경 (null 필드는 유지)
     */
    @PutMapping("/{category}")
    public ResponseEntity<Map<String, Object>> updateCategorySetting(
            @PathVariable String category,
            @RequestParam Long companyId,
            @Valid @RequestBody ScheduleCategorySettingRequestDTO request) {
        try {
            log.info("[ScheduleCategory API] 기본 구분 설정: companyId={}, category={}", companyId, category);

            ScheduleCategorySettingDTO updated =
                    scheduleService.upsertCategorySetting(companyId, category, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "category", updated,
                            "message", "기본 구분이 수정되었습니다."
                    ));
        } catch (RuntimeException e) {
            log.error("[ScheduleCategory API] 기본 구분 설정 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ScheduleCategory API] 기본 구분 설정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "기본 구분 설정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 기본 구분 설정을 기본값으로 되돌린다
     */
    @DeleteMapping("/{category}")
    public ResponseEntity<Map<String, Object>> resetCategorySetting(
            @PathVariable String category,
            @RequestParam Long companyId) {
        try {
            log.info("[ScheduleCategory API] 기본 구분 초기화: companyId={}, category={}", companyId, category);
            scheduleService.resetCategorySetting(companyId, category);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "기본 구분이 기본값으로 되돌아갔습니다."
                    ));
        } catch (RuntimeException e) {
            log.error("[ScheduleCategory API] 기본 구분 초기화 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ScheduleCategory API] 기본 구분 초기화 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "기본 구분 초기화 중 오류가 발생했습니다: " + e.getMessage()));
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
