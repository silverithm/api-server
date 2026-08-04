package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ApprovalLineEntryDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalStepDTO;
import com.silverithm.vehicleplacementsystem.dto.ApproverCandidateDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.DocumentNumberCounter;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.DocumentNumberCounterRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.service.ApprovalAccessService.CallerIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalRequestService {

    private static final int MAX_APPROVAL_STEPS = 5;

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalTemplateRepository templateRepository;
    private final CompanyRepository companyRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ApprovalAccessService accessService;
    private final DocumentNumberCounterRepository docNumberCounterRepository;
    private final ResourceScopeGuard resourceScopeGuard;

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
        resourceScopeGuard.requireSameCompany(request.getCompany());
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
        resourceScopeGuard.requireSameCompany(template.getCompany());

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

        boolean hasLine = dto.getApprovalLine() != null && !dto.getApprovalLine().isEmpty();
        if (hasLine) {
            buildApprovalLine(request, company, dto.getApprovalLine());
        }

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 요청 생성: id={}, title={}, requester={}, 결재선={}단계",
                saved.getId(), saved.getTitle(), requesterName, saved.getSteps().size());

        if (hasLine) {
            // 결재선이 있으면 1단계 결재자에게만 알림
            notifyStepApprover(saved, saved.currentStep());
        } else {
            notifyAdminsOfSubmission(saved);
        }

        return toDTO(saved);
    }

    /** 결재선 검증 및 단계 생성. 리스트 순서=결재 순서, 마지막=최종 결재자. */
    private void buildApprovalLine(ApprovalRequest request, Company company, List<ApprovalLineEntryDTO> entries) {
        if (entries.size() > MAX_APPROVAL_STEPS) {
            throw new IllegalArgumentException("결재선은 최대 " + MAX_APPROVAL_STEPS + "단계까지 설정할 수 있습니다");
        }

        Set<String> seenApprovers = new HashSet<>();
        List<ApprovalStep> steps = new ArrayList<>();

        for (int index = 0; index < entries.size(); index++) {
            ApprovalLineEntryDTO entry = entries.get(index);

            ApprovalStep.ApproverType approverType;
            try {
                approverType = ApprovalStep.ApproverType.valueOf(entry.getApproverType().trim().toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException("결재자 유형이 올바르지 않습니다: " + entry.getApproverType());
            }

            ApprovalAccessService.ResolvedApprover approver =
                    accessService.resolveApprover(approverType, entry.getApproverId(), company.getId());

            if (!seenApprovers.add(approverType + ":" + approver.refId())) {
                throw new IllegalArgumentException("같은 결재자를 결재선에 중복 지정할 수 없습니다: " + approver.name());
            }

            boolean isLast = index == entries.size() - 1;
            steps.add(ApprovalStep.builder()
                    .approvalRequest(request)
                    .stepOrder(index + 1)
                    .approverType(approver.type())
                    .approverRefId(approver.refId())
                    .approverIdLegacy(approver.legacyId())
                    .approverName(approver.name())
                    .roleLabel(isLast ? ApprovalStep.StepRole.FINAL : ApprovalStep.StepRole.REVIEWER)
                    .status(ApprovalStep.StepStatus.PENDING)
                    .build());
        }

        request.getSteps().addAll(steps);
        request.setHasApprovalLine(true);
        request.setCurrentStepOrder(1);
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
        resourceScopeGuard.requireSameCompany(request.getCompany());

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

    // 결재 승인 — 인가는 JWT 기반, processedBy 파라미터는 호환 저장용
    public ApprovalRequestDTO approveRequest(Long id, String processedBy, String processedByName,
                                             UserDetails userDetails, String signatureBase64) {
        return approveRequest(id, processedBy, processedByName, userDetails, signatureBase64, false);
    }

    // force=true: 관리자 직권 승인(전결) — 남은 검토 단계를 건너뛰고 즉시 최종 승인
    public ApprovalRequestDTO approveRequest(Long id, String processedBy, String processedByName,
                                             UserDetails userDetails, String signatureBase64, boolean force) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(request.getCompany());

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("이미 처리된 결재 요청입니다.");
        }

        CallerIdentity caller = accessService.resolveCaller(userDetails);
        LocalDateTime now = LocalDateTime.now();

        if (!request.hasSteps()) {
            // legacy 단일 승인 — 이제 인가 필요 (기존: 인증만 되면 누구나 가능)
            accessService.requireAdminOrApprovalManage(caller, request.getCompany().getId());

            request.setStatus(ApprovalStatus.APPROVED);
            request.setProcessedBy(processedBy);
            request.setProcessedByName(processedByName);
            request.setProcessedAt(now);
            allocateDocNumber(request);

            ApprovalRequest saved = requestRepository.save(request);
            log.info("[ApprovalRequest] 결재 승인(legacy): id={}, processedBy={}", saved.getId(), processedByName);

            notifyRequesterOfResult(saved, true, null);
            return toDTO(saved);
        }

        if (force) {
            return forceApprove(request, processedBy, processedByName, caller, signatureBase64, now);
        }

        ApprovalStep step = request.currentStep();
        accessService.requireIsStepApprover(caller, step);

        step.setStatus(ApprovalStep.StepStatus.APPROVED);
        step.setSignatureUrl(resolveSignature(caller, signatureBase64));
        step.setProcessedAt(now);

        if (request.isFinalStep(step)) {
            request.setStatus(ApprovalStatus.APPROVED);
            request.setProcessedBy(processedBy);
            request.setProcessedByName(processedByName);
            request.setProcessedAt(now);
            request.setCurrentStepOrder(null);
            allocateDocNumber(request);

            ApprovalRequest saved = requestRepository.save(request);
            log.info("[ApprovalRequest] 결재 최종 승인: id={}, docNumber={}, approver={}",
                    saved.getId(), saved.getDocNumber(), step.getApproverName());

            notifyRequesterOfResult(saved, true, null);
            return toDTO(saved);
        }

        request.setCurrentStepOrder(step.getStepOrder() + 1);
        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 단계 승인: id={}, step={}/{}, approver={}",
                saved.getId(), step.getStepOrder(), saved.getSteps().size(), step.getApproverName());

        notifyStepApprover(saved, saved.currentStep());
        return toDTO(saved);
    }

    // 관리자 직권 승인(전결): 관리자 본인 단계는 서명 날인으로 승인, 나머지 대기 단계는 SKIPPED 처리
    private ApprovalRequestDTO forceApprove(ApprovalRequest request, String processedBy, String processedByName,
                                            CallerIdentity caller, String signatureBase64, LocalDateTime now) {
        if (!accessService.isCompanyAdmin(caller, request.getCompany().getId())) {
            throw new SecurityException("직권 승인은 기관 관리자만 할 수 있습니다.");
        }

        for (ApprovalStep step : request.getSteps()) {
            if (step.getStatus() != ApprovalStep.StepStatus.PENDING) {
                continue;
            }
            boolean isCallerStep = caller.type() == step.getApproverType()
                    && caller.refId().equals(step.getApproverRefId());
            if (isCallerStep) {
                step.setStatus(ApprovalStep.StepStatus.APPROVED);
                step.setSignatureUrl(resolveSignature(caller, signatureBase64));
            } else {
                step.setStatus(ApprovalStep.StepStatus.SKIPPED);
            }
            step.setProcessedAt(now);
        }

        request.setStatus(ApprovalStatus.APPROVED);
        request.setProcessedBy(processedBy);
        request.setProcessedByName(processedByName);
        request.setProcessedAt(now);
        request.setCurrentStepOrder(null);
        allocateDocNumber(request);

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 직권 승인(전결): id={}, docNumber={}, admin={}",
                saved.getId(), saved.getDocNumber(), caller.name());

        notifyRequesterOfResult(saved, true, null);
        return toDTO(saved);
    }

    // 결재 반려 — 어느 단계에서든 반려되면 요청 전체가 반려된다
    public ApprovalRequestDTO rejectRequest(Long id, String processedBy, String processedByName, String reason,
                                            UserDetails userDetails) {
        return rejectRequest(id, processedBy, processedByName, reason, userDetails, false);
    }

    // force=true: 관리자 직권 반려 — 현재 결재 차례와 무관하게 반려
    public ApprovalRequestDTO rejectRequest(Long id, String processedBy, String processedByName, String reason,
                                            UserDetails userDetails, boolean force) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(request.getCompany());

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("이미 처리된 결재 요청입니다.");
        }

        CallerIdentity caller = accessService.resolveCaller(userDetails);
        LocalDateTime now = LocalDateTime.now();

        if (request.hasSteps()) {
            ApprovalStep step = request.currentStep();
            if (force) {
                if (!accessService.isCompanyAdmin(caller, request.getCompany().getId())) {
                    throw new SecurityException("직권 반려는 기관 관리자만 할 수 있습니다.");
                }
            } else {
                accessService.requireIsStepApprover(caller, step);
            }
            step.setStatus(ApprovalStep.StepStatus.REJECTED);
            step.setRejectReason(reason);
            step.setProcessedAt(now);
            request.setCurrentStepOrder(null);
        } else {
            accessService.requireAdminOrApprovalManage(caller, request.getCompany().getId());
        }

        request.setStatus(ApprovalStatus.REJECTED);
        request.setProcessedBy(processedBy);
        request.setProcessedByName(processedByName);
        request.setProcessedAt(now);
        request.setRejectReason(reason);

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 반려: id={}, processedBy={}, reason={}", saved.getId(), processedByName, reason);

        notifyRequesterOfResult(saved, false, reason);

        return toDTO(saved);
    }

    // 일괄 승인 — 항목별 인가, 부분 성공 (실패 항목은 건너뛰고 로그)
    public List<ApprovalRequestDTO> bulkApprove(List<Long> ids, String processedBy, String processedByName,
                                                UserDetails userDetails) {
        List<ApprovalRequestDTO> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                results.add(approveRequest(id, processedBy, processedByName, userDetails, null));
            } catch (Exception e) {
                log.warn("[ApprovalRequest] 일괄 승인 항목 실패: id={}, {}", id, e.getMessage());
            }
        }
        if (results.isEmpty() && !ids.isEmpty()) {
            throw new RuntimeException("일괄 승인에 실패했습니다. 처리 권한과 결재 차례를 확인해주세요.");
        }
        return results;
    }

    // 일괄 반려 — 항목별 인가, 부분 성공
    public List<ApprovalRequestDTO> bulkReject(List<Long> ids, String processedBy, String processedByName,
                                               String reason, UserDetails userDetails) {
        List<ApprovalRequestDTO> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                results.add(rejectRequest(id, processedBy, processedByName, reason, userDetails));
            } catch (Exception e) {
                log.warn("[ApprovalRequest] 일괄 반려 항목 실패: id={}, {}", id, e.getMessage());
            }
        }
        if (results.isEmpty() && !ids.isEmpty()) {
            throw new RuntimeException("일괄 반려에 실패했습니다. 처리 권한과 결재 차례를 확인해주세요.");
        }
        return results;
    }

    // 결재 요청 삭제 (취소) — 기안자 본인 또는 회사 관리자만
    public void deleteRequest(Long id, UserDetails userDetails) {
        ApprovalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("결재 요청을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(request.getCompany());

        CallerIdentity caller = accessService.resolveCaller(userDetails);
        boolean isRequester = caller != null && caller.legacyId().equals(request.getRequesterId());
        if (!isRequester && !accessService.isCompanyAdmin(caller, request.getCompany().getId())) {
            throw new SecurityException("본인이 상신한 결재만 취소할 수 있습니다.");
        }

        requestRepository.deleteById(id);
        log.info("[ApprovalRequest] 결재 요청 삭제: id={}, status={}, by={}",
                id, request.getStatus(), caller != null ? caller.legacyId() : "unknown");
    }

    // ─── 서명/문서번호 헬퍼 ───

    /** 승인 서명 결정: 즉석 서명(base64) 우선, 없으면 등록 서명, 그것도 없으면 null (승인은 진행) */
    private String resolveSignature(CallerIdentity caller, String signatureBase64) {
        if (signatureBase64 != null && !signatureBase64.isBlank()) {
            try {
                return storeSignatureImage(signatureBase64);
            } catch (Exception e) {
                log.error("[ApprovalRequest] 즉석 서명 저장 실패, 등록 서명으로 대체: {}", e.getMessage());
            }
        }

        return accessService.findRegisteredSignature(caller);
    }

    /** base64 PNG 디코드 후 S3 signatures/ 저장. data URL 접두사 허용. */
    private String storeSignatureImage(String base64) throws IOException {
        String payload = base64.trim();
        int commaIndex = payload.indexOf(',');
        if (payload.startsWith("data:") && commaIndex > 0) {
            payload = payload.substring(commaIndex + 1);
        }

        byte[] bytes = Base64.getDecoder().decode(payload);

        // PNG magic bytes 검증
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IllegalArgumentException("서명 이미지는 PNG 형식이어야 합니다.");
        }

        return fileStorageService.storeBytes(bytes, ".png", "signatures");
    }

    /** 최종 승인 시 문서번호 발급 (회사·연도별 순번, 멱등) */
    private void allocateDocNumber(ApprovalRequest request) {
        if (request.getDocNumber() != null) {
            return;
        }

        try {
            Long companyId = request.getCompany().getId();
            int year = LocalDate.now().getYear();

            docNumberCounterRepository.ensureCounter(companyId, year);
            DocumentNumberCounter counter = docNumberCounterRepository.findByCompanyIdAndYear(companyId, year)
                    .orElseThrow(() -> new IllegalStateException("문서번호 카운터를 찾을 수 없습니다"));

            int seq = counter.getSeq() + 1;
            counter.setSeq(seq);

            request.setDocNumber(year + "-" + seq);
            request.setDocNumberDisplay("제 " + year + "-" + seq + " 호");
        } catch (Exception e) {
            // 문서번호 발급 실패가 승인 자체를 막지 않도록 한다
            log.error("[ApprovalRequest] 문서번호 발급 실패: requestId={}, {}", request.getId(), e.getMessage());
        }
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

    /** 다음 차례 결재 단계의 결재자에게 FCM 알림 전송. 실패해도 본 트랜잭션에 영향 없음. */
    private void notifyStepApprover(ApprovalRequest request, ApprovalStep step) {
        if (step == null) {
            return;
        }

        try {
            String token = null;
            String recipientUserId = null;
            String recipientUserName = step.getApproverName();

            if (step.getApproverType() == ApprovalStep.ApproverType.ADMIN) {
                AppUser approver = userRepository.findById(step.getApproverRefId()).orElse(null);
                if (approver != null) {
                    token = approver.getFcmToken();
                    recipientUserId = String.valueOf(approver.getId());
                }
            } else {
                Member approver = memberRepository.findById(step.getApproverRefId()).orElse(null);
                if (approver != null) {
                    token = approver.getFcmToken();
                    recipientUserId = String.valueOf(approver.getId());
                }
            }

            if (token == null || token.isEmpty()) {
                log.debug("[ApprovalRequest] 결재자 FCM 토큰 없음: step={}, approver={}",
                        step.getStepOrder(), step.getApproverName());
                return;
            }

            notificationService.sendAndSaveNotification(FCMNotificationRequestDTO.builder()
                    .recipientToken(token)
                    .title("결재 요청 도착")
                    .message(request.getRequesterName() + "님의 '" + request.getTitle() + "' 결재가 결재를 기다리고 있습니다.")
                    .recipientUserId(recipientUserId)
                    .recipientUserName(recipientUserName)
                    .type("approval")
                    .relatedEntityId(request.getId())
                    .relatedEntityType("approval_request")
                    .data(Map.of(
                            "type", "approval",
                            "requestId", String.valueOf(request.getId())
                    ))
                    .build());
        } catch (Exception e) {
            log.error("[ApprovalRequest] 결재 차례 알림 전송 실패: requestId={}, {}", request.getId(), e.getMessage());
        }
    }

    /** 결재선에 지정 가능한 결재자 후보: 회사 관리자(AppUser) + ADMIN 역할 또는 APPROVAL_MANAGE 권한 Member */
    @Transactional(readOnly = true)
    public List<ApproverCandidateDTO> getApproverCandidates(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        List<ApproverCandidateDTO> candidates = new ArrayList<>();

        if (company.getUsers() != null) {
            for (AppUser appUser : company.getUsers()) {
                if (appUser.getDeletedAt() != null) {
                    continue;
                }
                candidates.add(ApproverCandidateDTO.builder()
                        .approverType(ApprovalStep.ApproverType.ADMIN.name())
                        .approverId(appUser.getId())
                        .name(appUser.getUsername())
                        .position("관리자")
                        .build());
            }
        }

        // 결재선에는 검토자 단계도 있으므로 활성 상태의 전 직원을 후보로 제공한다
        // (기존에는 ADMIN 역할·결재관리 권한 보유자만 노출되어 3명뿐이라는 문의가 있었다)
        for (Member member : memberRepository.findByCompanyOrderByCreatedAtDesc(company)) {
            if (member.getStatus() != Member.MemberStatus.ACTIVE) {
                continue;
            }
            candidates.add(ApproverCandidateDTO.builder()
                    .approverType(ApprovalStep.ApproverType.MEMBER.name())
                    .approverId(member.getId())
                    .name(member.getName())
                    .position(member.getPosition())
                    .build());
        }

        return candidates;
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

        // 결재선 서명 이미지 상대경로 → S3 URL 변환
        if (dto.getApprovalLine() != null) {
            for (ApprovalStepDTO step : dto.getApprovalLine()) {
                step.setSignatureUrl(toAbsoluteFileUrl(step.getSignatureUrl()));
            }
        }

        // 최종 승인된 결재선 문서에는 기관 직인 노출 (시행 문서)
        if (request.getStatus() == ApprovalStatus.APPROVED && Boolean.TRUE.equals(dto.getHasApprovalLine())) {
            String sealUrl = request.getCompany() != null ? request.getCompany().getSealUrl() : null;
            dto.setCompanySealUrl(toAbsoluteFileUrl(sealUrl));
        }

        return dto;
    }

    private String toAbsoluteFileUrl(String path) {
        if (path == null || path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return fileStorageService.getFileUrl(path);
    }
}
