package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.service.ApprovalRequestService;
import com.silverithm.vehicleplacementsystem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class ApprovalRequestController {

    private final ApprovalRequestService approvalService;
    private final FileStorageService fileStorageService;

    /**
     * 결재 요청 목록 조회 (관리자)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getApprovals(
            @RequestParam Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String searchQuery) {

        try {
            log.info("[Approval API] 결재 목록 조회: companyId={}", companyId);

            List<ApprovalRequestDTO> approvals = approvalService.getApprovalRequests(
                    companyId, status, startDate, endDate, searchQuery);
            Map<String, Long> stats = approvalService.getStats(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "approvals", approvals,
                            "stats", stats
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 내 결재 요청 조회 (직원용)
     */
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyApprovals(@RequestParam String requesterId) {
        try {
            log.info("[Approval API] 내 결재 조회: requesterId={}", requesterId);

            List<ApprovalRequestDTO> approvals = approvalService.getMyApprovalRequests(requesterId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("approvals", approvals));

        } catch (Exception e) {
            log.error("[Approval API] 내 결재 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재 요청 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getApproval(@PathVariable Long id) {
        try {
            log.info("[Approval API] 결재 상세 조회: id={}", id);

            ApprovalRequestDTO approval = approvalService.getApprovalRequest(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("approval", approval));

        } catch (Exception e) {
            log.error("[Approval API] 상세 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재 요청 생성 (직원)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createApproval(
            @RequestParam Long companyId,
            @RequestParam String requesterId,
            @RequestParam String requesterName,
            @Valid @RequestBody CreateApprovalRequestDTO request) {

        try {
            log.info("[Approval API] 결재 요청 생성: companyId={}, requester={}", companyId, requesterName);

            ApprovalRequestDTO approval = approvalService.createApprovalRequest(
                    companyId, requesterId, requesterName, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approval", approval,
                            "message", "결재 요청이 제출되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 생성 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 요청 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재 첨부파일 업로드
     */
    @PostMapping("/files")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("[Approval API] 파일 업로드 요청: fileName={}, size={}bytes",
                    file.getOriginalFilename(), file.getSize());

            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .body(Map.of("error", "파일이 비어있습니다."));
            }

            // S3에 파일 저장 (approvals 서브디렉토리)
            String filePath = fileStorageService.storeFile(file, "approvals");
            String fileUrl = fileStorageService.getFileUrl(filePath);

            log.info("[Approval API] 파일 업로드 성공: filePath={}, fileUrl={}", filePath, fileUrl);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "filePath", filePath,
                            "fileUrl", fileUrl,
                            "fileName", file.getOriginalFilename(),
                            "fileSize", file.getSize()
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 파일 업로드 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재 승인 (관리자)
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveRequest(
            @PathVariable Long id,
            @RequestParam String processedBy,
            @RequestParam String processedByName) {

        try {
            log.info("[Approval API] 결재 승인: id={}, processedBy={}", id, processedByName);

            ApprovalRequestDTO approval = approvalService.approveRequest(id, processedBy, processedByName);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approval", approval,
                            "message", "결재가 승인되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 승인 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 승인 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재 반려 (관리자)
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectRequest(
            @PathVariable Long id,
            @RequestParam String processedBy,
            @RequestParam String processedByName,
            @RequestBody Map<String, String> body) {

        try {
            String reason = body.get("reason");
            log.info("[Approval API] 결재 반려: id={}, processedBy={}", id, processedByName);

            ApprovalRequestDTO approval = approvalService.rejectRequest(id, processedBy, processedByName, reason);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approval", approval,
                            "message", "결재가 반려되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 반려 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 반려 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 일괄 승인 (관리자)
     */
    @PutMapping("/bulk-approve")
    public ResponseEntity<Map<String, Object>> bulkApprove(
            @RequestParam String processedBy,
            @RequestParam String processedByName,
            @RequestBody Map<String, List<Long>> body) {

        try {
            List<Long> ids = body.get("ids");
            log.info("[Approval API] 일괄 승인: ids={}, processedBy={}", ids.size(), processedByName);

            List<ApprovalRequestDTO> approvals = approvalService.bulkApprove(ids, processedBy, processedByName);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approvals", approvals,
                            "message", ids.size() + "건의 결재가 승인되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 일괄 승인 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "일괄 승인 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 일괄 반려 (관리자)
     */
    @PutMapping("/bulk-reject")
    public ResponseEntity<Map<String, Object>> bulkReject(
            @RequestParam String processedBy,
            @RequestParam String processedByName,
            @RequestBody Map<String, Object> body) {

        try {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) body.get("ids");
            String reason = (String) body.get("reason");
            log.info("[Approval API] 일괄 반려: ids={}, processedBy={}", ids.size(), processedByName);

            List<ApprovalRequestDTO> approvals = approvalService.bulkReject(ids, processedBy, processedByName, reason);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approvals", approvals,
                            "message", ids.size() + "건의 결재가 반려되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 일괄 반려 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "일괄 반려 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재 요청 삭제/취소 (직원)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteApproval(@PathVariable Long id) {
        try {
            log.info("[Approval API] 결재 취소: id={}", id);

            approvalService.deleteRequest(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "결재 요청이 취소되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 취소 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 취소 중 오류가 발생했습니다: " + e.getMessage()));
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