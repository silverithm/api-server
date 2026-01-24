package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
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
                .attachmentUrl(dto.getAttachmentUrl())
                .attachmentFileName(dto.getAttachmentFileName())
                .attachmentFileSize(dto.getAttachmentFileSize())
                .build();

        ApprovalRequest saved = requestRepository.save(request);
        log.info("[ApprovalRequest] 결재 요청 생성: id={}, title={}, requester={}", saved.getId(), saved.getTitle(), requesterName);

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

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("대기중인 결재만 취소할 수 있습니다.");
        }

        requestRepository.deleteById(id);
        log.info("[ApprovalRequest] 결재 요청 취소: id={}", id);
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
