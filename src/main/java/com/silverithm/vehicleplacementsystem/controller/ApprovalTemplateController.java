package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ApprovalTemplateDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalTemplateRequestDTO;
import com.silverithm.vehicleplacementsystem.service.ApprovalTemplateService;
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
@RequestMapping("/api/v1/approval-templates")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class ApprovalTemplateController {

    private final ApprovalTemplateService templateService;

    /**
     * 양식 목록 조회 (관리자)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTemplates(@RequestParam Long companyId) {
        try {
            log.info("[ApprovalTemplate API] 양식 목록 조회: companyId={}", companyId);

            List<ApprovalTemplateDTO> templates = templateService.getAllTemplates(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("templates", templates));

        } catch (Exception e) {
            log.error("[ApprovalTemplate API] 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "양식 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 활성화된 양식 목록 조회 (직원용)
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveTemplates(@RequestParam Long companyId) {
        try {
            log.info("[ApprovalTemplate API] 활성 양식 조회: companyId={}", companyId);

            List<ApprovalTemplateDTO> templates = templateService.getActiveTemplates(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("templates", templates));

        } catch (Exception e) {
            log.error("[ApprovalTemplate API] 활성 양식 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "양식 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 양식 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTemplate(@PathVariable Long id) {
        try {
            log.info("[ApprovalTemplate API] 양식 상세 조회: id={}", id);

            ApprovalTemplateDTO template = templateService.getTemplate(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("template", template));

        } catch (Exception e) {
            log.error("[ApprovalTemplate API] 상세 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "양식 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 양식 등록 (관리자)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(
            @RequestParam Long companyId,
            @Valid @RequestBody CreateApprovalTemplateRequestDTO request) {

        try {
            log.info("[ApprovalTemplate API] 양식 등록: companyId={}, name={}", companyId, request.getName());

            ApprovalTemplateDTO template = templateService.createTemplate(companyId, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "template", template,
                            "message", "양식이 등록되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[ApprovalTemplate API] 등록 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "양식 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 양식 수정 (관리자)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody CreateApprovalTemplateRequestDTO request) {

        try {
            log.info("[ApprovalTemplate API] 양식 수정: id={}", id);

            ApprovalTemplateDTO template = templateService.updateTemplate(id, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "template", template,
                            "message", "양식이 수정되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[ApprovalTemplate API] 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "양식 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 양식 활성화/비활성화 토글 (관리자)
     */
    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        try {
            log.info("[ApprovalTemplate API] 양식 상태 토글: id={}", id);

            ApprovalTemplateDTO template = templateService.toggleActive(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "template", template,
                            "message", "양식 상태가 변경되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[ApprovalTemplate API] 상태 토글 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "양식 상태 변경 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 양식 삭제 (관리자)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(@PathVariable Long id) {
        try {
            log.info("[ApprovalTemplate API] 양식 삭제: id={}", id);

            templateService.deleteTemplate(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "양식이 삭제되었습니다."
                    ));

        } catch (IllegalStateException e) {
            // 비즈니스 로직 에러 (결재 요청이 있어서 삭제 불가 등)
            log.warn("[ApprovalTemplate API] 삭제 불가: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // 양식을 찾을 수 없는 경우
            log.warn("[ApprovalTemplate API] 양식 없음: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ApprovalTemplate API] 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "양식 삭제 중 오류가 발생했습니다: " + e.getMessage()));
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
