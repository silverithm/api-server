package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelRequestDTO;
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

@RestController
@RequestMapping("/api/v1/schedule-labels")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class ScheduleLabelController {

    private final ScheduleService scheduleService;

    /**
     * 라벨 목록 조회
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getLabels(@RequestParam Long companyId) {

        try {
            log.info("[ScheduleLabel API] 라벨 목록 조회: companyId={}", companyId);

            List<ScheduleLabelDTO> labels = scheduleService.getLabels(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "labels", labels
                    ));

        } catch (Exception e) {
            log.error("[ScheduleLabel API] 라벨 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "라벨 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 라벨 등록
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createLabel(
            @RequestParam Long companyId,
            @Valid @RequestBody ScheduleLabelRequestDTO request) {

        try {
            log.info("[ScheduleLabel API] 라벨 등록: companyId={}, name={}", companyId, request.getName());

            ScheduleLabelDTO label = scheduleService.createLabel(companyId, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "label", label,
                            "message", "라벨이 등록되었습니다."
                    ));

        } catch (RuntimeException e) {
            log.error("[ScheduleLabel API] 라벨 등록 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ScheduleLabel API] 라벨 등록 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "라벨 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 라벨 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateLabel(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleLabelRequestDTO request) {

        try {
            log.info("[ScheduleLabel API] 라벨 수정: id={}", id);

            ScheduleLabelDTO label = scheduleService.updateLabel(id, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "label", label,
                            "message", "라벨이 수정되었습니다."
                    ));

        } catch (RuntimeException e) {
            log.error("[ScheduleLabel API] 라벨 수정 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ScheduleLabel API] 라벨 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "라벨 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 라벨 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteLabel(@PathVariable Long id) {

        try {
            log.info("[ScheduleLabel API] 라벨 삭제: id={}", id);

            scheduleService.deleteLabel(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "라벨이 삭제되었습니다."
                    ));

        } catch (RuntimeException e) {
            log.error("[ScheduleLabel API] 라벨 삭제 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ScheduleLabel API] 라벨 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "라벨 삭제 중 오류가 발생했습니다: " + e.getMessage()));
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