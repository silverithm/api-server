package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalRequestService {

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalTemplateRepository templateRepository;
    private final CompanyRepository companyRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final MemberRepository memberRepository;

    // 결재 요청 목록 조회 (관리자용, 필터 적용)
    @Transactional(readOnly = true)
    public List<ApprovalRequestDTO> getApprovalRequests(
            Long companyId,
            String status,
            String startDate,
            String endDate,
            String searchQuery
    ) {
        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        ApprovalStatus statusEnum = null;
        if (status != null && !status.equals("ALL")) {
            try {
                statusEnum = ApprovalStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status: {}", status);
            }
        }

        List<ApprovalRequest> requests;
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            requests = requestRepository.findByCompanyIdAndFiltersWithSearch(companyId, statusEnum, start, end, searchQuery);
        } else {
            requests = requestRepository.findByCompanyIdAndFilters(companyId, statusEnum, start, end);
        }

        return requests.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // 내 결재 요청 조회 (직원용)
    @Transactional(readOnly = true)
    public List<ApprovalRequestDTO> getMyApprovalRequests(String requesterId) {
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // 결재 요청 상세 조회
    @Transactional(readOnly = true)
    public ApprovalRequestDTO getApprovalRequest(Long id) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));
        return toDTO(request);
    }

    // 결재 요청 생성 (직원)
    public ApprovalRequestDTO createApprovalRequest(
            Long companyId,
            String requesterId,
            String requesterName,
            CreateApprovalRequestDTO dto
    ) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        ApprovalTemplate template = templateRepository.findById(dto.getTemplateId())
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + dto.getTemplateId()));

        ApprovalRequest request = ApprovalRequest.builder()
                .company(company)
                .template(template)
                .title(dto.getTitle())
                .requesterId(requesterId)
                .requesterName(requesterName)
                .status(ApprovalStatus.PENDING)
                .formData(dto.getFormData())
                .attachmentUrl(dto.getAttachmentUrl())
                .attachmentFileName(dto.getAttachmentFileName())
                .attachmentFileSize(dto.getAttachmentFileSize())
                .build();

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 요청 생성: id={}, title={}, requester={}", saved.getId(), saved.getTitle(), requesterName);

        notifyAdminsOfSubmission(saved);

        return toDTO(saved);
    }

    // 진행중 결재의 첨부파일 교체 (기안자 본인만)
    public ApprovalRequestDTO updateAttachment(
            Long id,
            String requesterId,
            String attachmentUrl,
            String attachmentFileName,
            Long attachmentFileSize
    ) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("진행중인 결재만 첨부파일을 수정할 수 있습니다.");
        }
        if (requesterId == null || !requesterId.equals(request.getRequesterId())) {
            throw new RuntimeException("본인이 상신한 결재만 수정할 수 있습니다.");
        }

        request.setAttachmentUrl(attachmentUrl);
        request.setAttachmentFileName(attachmentFileName);
        request.setAttachmentFileSize(attachmentFileSize);

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 첨부파일 수정: id={}, requester={}, file={}", saved.getId(), requesterId, attachmentFileName);

        return toDTO(saved);
    }

    // 결재 승인 (관리자)
    public ApprovalRequestDTO approveRequest(Long id, String processedBy, String processedByName) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("이미 처리된 결재 요청입니다.");
        }

        request.setStatus(ApprovalStatus.APPROVED);
        request.setProcessedBy(processedBy);
        request.setProcessedByName(processedByName);
        request.setProcessedAt(LocalDateTime.now());

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 승인: id={}, processedBy={}", saved.getId(), processedByName);

        notifyRequesterOfResult(saved, true, null);

        return toDTO(saved);
    }

    // 결재 반려 (관리자)
    public ApprovalRequestDTO rejectRequest(Long id, String processedBy, String processedByName, String reason) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("이미 처리된 결재 요청입니다.");
        }

        request.setStatus(ApprovalStatus.REJECTED);
        request.setProcessedBy(processedBy);
        request.setProcessedByName(processedByName);
        request.setProcessedAt(LocalDateTime.now());
        request.setRejectReason(reason);

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 반려: id={}, processedBy={}, reason={}", saved.getId(), processedByName, reason);

        notifyRequesterOfResult(saved, false, reason);

        return toDTO(saved);
    }

    // 일괄 승인
    public List<ApprovalRequestDTO> bulkApprove(List<Long> ids, String processedBy, String processedByName) {
        return ids.stream()
                .map(id -> approveRequest(id, processedBy, processedByName))
                .collect(Collectors.toList());
    }

    // 일괄 반려
    public List<ApprovalRequestDTO> bulkReject(List<Long> ids, String processedBy, String processedByName, String reason) {
        return ids.stream()
                .map(id -> rejectRequest(id, processedBy, processedByName, reason))
                .collect(Collectors.toList());
    }

    // 결재 요청 삭제 (취소)
    public void deleteRequest(Long id) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));

        requestRepository.deleteById(id);
        log.info("[ApprovalRequest] 결재 요청 삭제: id={}, status={}", id, request.getStatus());
    }

    // ─── 알림 헬퍼 ───

    /** 결재 상신 시 회사 관리자(AppUser)들에게 FCM 알림 전송. 실패해도 본 트랜잭션에 영향 없음. */
    private void notifyAdminsOfSubmission(ApprovalRequest request) {
        try {
            Company company = request.getCompany();
            if (company == null || company.getUsers() == null) {
                return;
            }
            for (AppUser admin : company.getUsers()) {
                String token = admin.getFcmToken();
                if (token == null || token.isEmpty()) {
                    continue;
                }
                try {
                    notificationService.sendAndSaveNotification(FCMNotificationRequestDTO.builder()
                            .recipientToken(token)
                            .title("새 전자결재 요청")
                            .message(request.getRequesterName() + "님이 '" + request.getTitle() + "' 결재를 상신했습니다.")
                            .recipientUserId(String.valueOf(admin.getId()))
                            .recipientUserName(admin.getUsername())
                            .type("approval")
                            .relatedEntityId(request.getId())
                            .relatedEntityType("approval_request")
                            .data(Map.of(
                                    "type", "approval",
                                    "requestId", String.valueOf(request.getId())
                            ))
                            .build());
                } catch (Exception e) {
                    log.error("[ApprovalRequest] 관리자 결재 알림 전송 실패: adminId={}, {}", admin.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[ApprovalRequest] 결재 상신 알림 처리 실패: requestId={}, {}", request.getId(), e.getMessage());
        }
    }

    /** 결재 승인/반려 시 기안자(Member)에게 FCM 알림 전송. 실패해도 본 트랜잭션에 영향 없음. */
    private void notifyRequesterOfResult(ApprovalRequest request, boolean approved, String reason) {
        try {
            Member requester = findRequester(request.getRequesterId());
            if (requester == null || requester.getFcmToken() == null || requester.getFcmToken().isEmpty()) {
                log.debug("[ApprovalRequest] 기안자 FCM 토큰 없음: requesterId={}", request.getRequesterId());
                return;
            }

            String title = approved ? "전자결재 승인" : "전자결재 반려";
            StringBuilder message = new StringBuilder()
                    .append("'").append(request.getTitle()).append("' 결재가 ")
                    .append(approved ? "승인" : "반려").append("되었습니다.");
            if (!approved && reason != null && !reason.isBlank()) {
                message.append(" 사유: ").append(reason);
            }

            notificationService.sendAndSaveNotification(FCMNotificationRequestDTO.builder()
                    .recipientToken(requester.getFcmToken())
                    .title(title)
                    .message(message.toString())
                    .recipientUserId(String.valueOf(requester.getId()))
                    .recipientUserName(requester.getName())
                    .type("approval")
                    .relatedEntityId(request.getId())
                    .relatedEntityType("approval_request")
                    .data(Map.of(
                            "type", "approval",
                            "requestId", String.valueOf(request.getId()),
                            "result", approved ? "approved" : "rejected"
                    ))
                    .build());
        } catch (Exception e) {
            log.error("[ApprovalRequest] 결재 결과 알림 전송 실패: requestId={}, {}", request.getId(), e.getMessage());
        }
    }

    /** requesterId(Member id 또는 username)로 기안자 조회 */
    private Member findRequester(String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            return null;
        }
        try {
            return memberRepository.findById(Long.valueOf(requesterId)).orElse(null);
        } catch (NumberFormatException e) {
            return memberRepository.findByUsername(requesterId).orElse(null);
        }
    }

    // 통계 조회
    @Transactional(readOnly = true)
    public Map<String, Long> getStats(Long companyId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("pending", requestRepository.countByCompanyIdAndStatus(companyId, ApprovalStatus.PENDING));
        stats.put("approved", requestRepository.countByCompanyIdAndStatus(companyId, ApprovalStatus.APPROVED));
        stats.put("rejected", requestRepository.countByCompanyIdAndStatus(companyId, ApprovalStatus.REJECTED));
        return stats;
    }

    // DTO 변환 시 S3 URL로 변환
    private ApprovalRequestDTO toDTO(ApprovalRequest request) {
        ApprovalRequestDTO dto = ApprovalRequestDTO.from(request);

        // attachmentUrl이 상대경로인 경우 S3 URL로 변환
        String attachmentUrl = dto.getAttachmentUrl();
        if (attachmentUrl != null && !attachmentUrl.isEmpty()
                && !attachmentUrl.startsWith("http://")
                && !attachmentUrl.startsWith("https://")) {
            String s3Url = fileStorageService.getFileUrl(attachmentUrl);
            dto.setAttachmentUrl(s3Url);
            log.debug("[ApprovalRequest] attachmentUrl 변환: {} -> {}", attachmentUrl, s3Url);
        }

        return dto;
    }
}
