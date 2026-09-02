package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApproveRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalImportPreviewDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalImportRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalViewerCandidatesDTO;
import com.silverithm.vehicleplacementsystem.dto.ApproverCandidateDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.UpdateApprovalAttachmentRequestDTO;
import com.silverithm.vehicleplacementsystem.service.ApprovalAccessService;
import com.silverithm.vehicleplacementsystem.service.ApprovalImportService;
import com.silverithm.vehicleplacementsystem.service.ApprovalImportTemplateWriter;
import com.silverithm.vehicleplacementsystem.service.ApprovalRequestService;
import com.silverithm.vehicleplacementsystem.service.FileAccessGuard;
import com.silverithm.vehicleplacementsystem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ApprovalRequestController {

    private final ApprovalRequestService approvalService;
    private final FileStorageService fileStorageService;
    private final FileAccessGuard fileAccessGuard;
    private final ApprovalImportService importService;
    private final ApprovalImportTemplateWriter importTemplateWriter;
    private final ApprovalAccessService accessService;

    /**
     * 결재 요청 목록 조회 (관리자)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getApprovals(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String category) {

        try {
            log.info("[Approval API] 결재 목록 조회: companyId={}", companyId);

            List<ApprovalRequestDTO> approvals = approvalService.getApprovalRequests(
                    companyId, status, startDate, endDate, searchQuery, templateId, category, userDetails);
            Map<String, Long> stats = approvalService.getStats(companyId, userDetails);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "approvals", approvals,
                            "stats", stats
                    ));

        } catch (SecurityException e) {
            log.warn("[Approval API] 목록 조회 권한 거부: companyId={}, {}", companyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
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
    public ResponseEntity<Map<String, Object>> getApproval(@AuthenticationPrincipal UserDetails userDetails,
                                                          @PathVariable Long id) {
        try {
            log.info("[Approval API] 결재 상세 조회: id={}", id);

            ApprovalRequestDTO approval = approvalService.getApprovalRequest(id, userDetails);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("approval", approval));

        } catch (SecurityException e) {
            log.warn("[Approval API] 상세 조회 권한 거부: id={}, {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
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
                            "message", request.isDraft() ? "임시저장했습니다." : "결재 요청이 제출되었습니다."
                    ));

        } catch (IllegalArgumentException e) {
            // 잘못 보낸 요청이지 서버 잘못이 아니다 — 500으로 내보내면 클라이언트가
            // 사용자에게 보여줄지 재시도할지 판단할 수 없다
            log.warn("[Approval API] 생성 거부: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("[Approval API] 생성 권한 거부: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Approval API] 생성 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 요청 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 임시저장 문서 이어쓰기 (기안자 본인)
     */
    @PutMapping("/{id}/draft")
    public ResponseEntity<Map<String, Object>> updateDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateApprovalRequestDTO request) {
        try {
            ApprovalRequestDTO approval = approvalService.updateDraft(id, userDetails, request);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "approval", approval, "message", "임시저장했습니다."));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders()).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Approval API] 임시저장 갱신 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "임시저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 임시저장 문서 상신 (기안자 본인) — 이 시점에 결재선이 검증되고 알림이 나간다
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) CreateApprovalRequestDTO request) {
        try {
            ApprovalRequestDTO approval = approvalService.submitDraft(id, userDetails, request);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "approval", approval, "message", "결재 요청이 제출되었습니다."));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders()).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Approval API] 임시저장 상신 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "상신 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재 첨부파일 업로드
     */
    @PostMapping("/files")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        try {
            log.info("[Approval API] 파일 업로드 요청: fileName={}, size={}bytes",
                    file.getOriginalFilename(), file.getSize());

            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .body(Map.of("error", "파일이 비어있습니다."));
            }

            // S3에 파일 저장 (approvals 서브디렉토리)
            // HEIC/HEIF 사진이면 JPEG 사본이 함께 만들어지고 첨부는 그 사본을 가리킨다.
            FileStorageService.StoredUpload stored = fileStorageService.storeUpload(file, "approvals");
            String filePath = stored.path();
            String fileUrl = fileStorageService.getFileUrl(filePath);

            // 결재 요청에 연결되기 전까지 업로더 본인만 접근 가능하도록 유예 부여
            fileAccessGuard.grantUploadGrace(userDetails, filePath);
            if (stored.isConverted()) {
                fileAccessGuard.grantUploadGrace(userDetails, stored.originalPath());
            }

            log.info("[Approval API] 파일 업로드 성공: filePath={}, fileUrl={}, converted={}",
                    filePath, fileUrl, stored.isConverted());

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "filePath", filePath,
                            "fileUrl", fileUrl,
                            "fileName", stored.fileName(),
                            "fileSize", stored.size()
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
    @PutMapping("/{id}/attachment")
    public ResponseEntity<Map<String, Object>> updateAttachment(
            @PathVariable Long id,
            @RequestParam String requesterId,
            @Valid @RequestBody UpdateApprovalAttachmentRequestDTO request) {

        try {
            log.info("[Approval API] 첨부파일 수정: id={}, requesterId={}", id, requesterId);

            ApprovalRequestDTO approval = approvalService.updateAttachment(
                    id, requesterId,
                    request.getAttachmentUrl(),
                    request.getAttachmentFileName(),
                    request.getAttachmentFileSize());

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approval", approval,
                            "message", "첨부파일이 수정되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 첨부파일 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "첨부파일 수정 중 오류가 발생했습니다."));
        }
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveRequest(
            @PathVariable Long id,
            @RequestParam String processedBy,
            @RequestParam String processedByName,
            @RequestParam(required = false, defaultValue = "false") boolean force,
            @RequestBody(required = false) ApproveRequestDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            log.info("[Approval API] 결재 승인: id={}, processedBy={}, force={}", id, processedByName, force);

            String signatureBase64 = body != null ? body.getSignatureBase64() : null;
            ApprovalRequestDTO approval = approvalService.approveRequest(
                    id, processedBy, processedByName, userDetails, signatureBase64, force);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approval", approval,
                            "message", "결재가 승인되었습니다."
                    ));

        } catch (SecurityException e) {
            log.warn("[Approval API] 승인 권한 거부: id={}, {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
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
            @RequestParam(required = false, defaultValue = "false") boolean force,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String reason = body.get("reason");
            log.info("[Approval API] 결재 반려: id={}, processedBy={}, force={}", id, processedByName, force);

            ApprovalRequestDTO approval = approvalService.rejectRequest(id, processedBy, processedByName, reason,
                    userDetails, force);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approval", approval,
                            "message", "결재가 반려되었습니다."
                    ));

        } catch (SecurityException e) {
            log.warn("[Approval API] 반려 권한 거부: id={}, {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
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
            @RequestBody Map<String, List<Long>> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            List<Long> ids = body.get("ids");
            log.info("[Approval API] 일괄 승인: ids={}, processedBy={}", ids.size(), processedByName);

            List<ApprovalRequestDTO> approvals = approvalService.bulkApprove(ids, processedBy, processedByName,
                    userDetails);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approvals", approvals,
                            "message", approvals.size() + "건의 결재가 승인되었습니다."
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
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) body.get("ids");
            String reason = (String) body.get("reason");
            log.info("[Approval API] 일괄 반려: ids={}, processedBy={}", ids.size(), processedByName);

            List<ApprovalRequestDTO> approvals = approvalService.bulkReject(ids, processedBy, processedByName, reason,
                    userDetails);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "approvals", approvals,
                            "message", approvals.size() + "건의 결재가 반려되었습니다."
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
    public ResponseEntity<Map<String, Object>> deleteApproval(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.info("[Approval API] 결재 취소: id={}", id);

            approvalService.deleteRequest(id, userDetails);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "결재 요청이 취소되었습니다."
                    ));

        } catch (SecurityException e) {
            log.warn("[Approval API] 취소 권한 거부: id={}, {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Approval API] 취소 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재 취소 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 결재선 지정 가능 결재자 후보 목록 (회사 관리자 + 결재 권한 보유 직원)
     */
    @GetMapping("/approver-candidates")
    public ResponseEntity<Map<String, Object>> getApproverCandidates(@RequestParam Long companyId) {
        try {
            log.info("[Approval API] 결재자 후보 조회: companyId={}", companyId);

            List<ApproverCandidateDTO> candidates = approvalService.getApproverCandidates(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("candidates", candidates));

        } catch (Exception e) {
            log.error("[Approval API] 결재자 후보 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "결재자 후보 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 열람 대상 지정 후보 목록 (직책 + 사람)
     */
    @GetMapping("/viewer-candidates")
    public ResponseEntity<Map<String, Object>> getViewerCandidates(@RequestParam Long companyId) {
        try {
            log.info("[Approval API] 열람 대상 후보 조회: companyId={}", companyId);

            ApprovalViewerCandidatesDTO candidates = approvalService.getViewerCandidates(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "positions", candidates.getPositions(),
                            "people", candidates.getPeople()
                    ));

        } catch (Exception e) {
            log.error("[Approval API] 열람 대상 후보 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "열람 대상 후보 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 이관 색인 양식(엑셀) 내려받기 — 기관이 이 양식을 채워 오면 된다.
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadImportTemplate(@AuthenticationPrincipal UserDetails userDetails,
                                                         @RequestParam Long companyId) {
        try {
            requireImportPermission(userDetails, companyId);

            byte[] body = importTemplateWriter.write();
            String encodedName = URLEncoder.encode(ApprovalImportTemplateWriter.FILE_NAME, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .header(HttpHeaders.CONTENT_TYPE,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedName)
                    .body(body);

        } catch (SecurityException e) {
            log.warn("[Approval API] 이관 양식 권한 거부: companyId={}, {}", companyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).headers(getCorsHeaders()).build();
        } catch (Exception e) {
            log.error("[Approval API] 이관 양식 생성 오류:", e);
            return ResponseEntity.internalServerError().headers(getCorsHeaders()).build();
        }
    }

    /**
     * 이관 색인(엑셀) 읽어보기 — 저장하지 않고 무엇이 들어갈지 돌려준다.
     */
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> previewImport(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long companyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedFileNames", required = false) List<String> uploadedFileNames) {

        try {
            requireImportPermission(userDetails, companyId);

            ApprovalImportPreviewDTO preview = importService.preview(companyId, file,
                    uploadedFileNames != null ? new HashSet<>(uploadedFileNames) : Set.of());

            return ResponseEntity.ok().headers(getCorsHeaders()).body(Map.of("preview", preview));

        } catch (SecurityException e) {
            log.warn("[Approval API] 이관 미리보기 권한 거부: companyId={}, {}", companyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Approval API] 이관 미리보기 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "색인 파일을 읽는 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 이관 확정 등록 — 문제가 있는 줄은 건너뛰고 결과를 줄 단위로 돌려준다.
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importApprovals(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long companyId,
            @RequestBody ApprovalImportRequestDTO request) {

        try {
            requireImportPermission(userDetails, companyId);

            ApprovalImportPreviewDTO result = importService.importRows(companyId, request);

            return ResponseEntity.ok().headers(getCorsHeaders()).body(Map.of(
                    "success", true,
                    "result", result,
                    "message", (result.getTotalCount() - result.getErrorCount()) + "건이 등록되었습니다."
            ));

        } catch (SecurityException e) {
            log.warn("[Approval API] 이관 등록 권한 거부: companyId={}, {}", companyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Approval API] 이관 등록 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "문서 이관 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /** 과거 문서 이관은 기관 관리자만 — 남의 이름으로 완료 문서를 만들어내는 일이라 권한을 좁게 둔다 */
    private void requireImportPermission(UserDetails userDetails, Long companyId) {
        var caller = accessService.resolveCaller(userDetails);
        if (!accessService.isCompanyAdmin(caller, companyId)) {
            throw new SecurityException("과거 문서 이관은 기관 관리자만 할 수 있습니다");
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