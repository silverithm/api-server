package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ApprovalTemplateDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalTemplateRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalTemplateService {

    private final ApprovalTemplateRepository templateRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final CompanyRepository companyRepository;

    // 전체 양식 조회 (관리자용)
    @Transactional(readOnly = true)
    public List<ApprovalTemplateDTO> getAllTemplates(Long companyId) {
        return templateRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(ApprovalTemplateDTO::from)
                .collect(Collectors.toList());
    }

    // 활성화된 양식만 조회 (직원용)
    @Transactional(readOnly = true)
    public List<ApprovalTemplateDTO> getActiveTemplates(Long companyId) {
        return templateRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(companyId)
                .stream()
                .map(ApprovalTemplateDTO::from)
                .collect(Collectors.toList());
    }

    // 양식 상세 조회
    @Transactional(readOnly = true)
    public ApprovalTemplateDTO getTemplate(Long id) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + id));
        return ApprovalTemplateDTO.from(template);
    }

    // 양식 생성
    public ApprovalTemplateDTO createTemplate(Long companyId, CreateApprovalTemplateRequestDTO request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        ApprovalTemplate template = ApprovalTemplate.builder()
                .company(company)
                .name(request.getName())
                .description(request.getDescription())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .isActive(true)
                .build();

        ApprovalTemplate saved = templateRepository.save(template);
        log.info("[ApprovalTemplate] 양식 생성: id={}, name={}", saved.getId(), saved.getName());

        return ApprovalTemplateDTO.from(saved);
    }

    // 양식 수정
    public ApprovalTemplateDTO updateTemplate(Long id, CreateApprovalTemplateRequestDTO request) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + id));

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setFileUrl(request.getFileUrl());
        template.setFileName(request.getFileName());
        template.setFileSize(request.getFileSize());

        ApprovalTemplate saved = templateRepository.save(template);
        log.info("[ApprovalTemplate] 양식 수정: id={}, name={}", saved.getId(), saved.getName());

        return ApprovalTemplateDTO.from(saved);
    }

    // 양식 활성화/비활성화 토글
    public ApprovalTemplateDTO toggleActive(Long id) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + id));

        template.setIsActive(!template.getIsActive());
        ApprovalTemplate saved = templateRepository.save(template);
        log.info("[ApprovalTemplate] 양식 상태 변경: id={}, isActive={}", saved.getId(), saved.getIsActive());

        return ApprovalTemplateDTO.from(saved);
    }

    // 양식 삭제 (관련 결재 요청도 함께 삭제)
    public void deleteTemplate(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new RuntimeException("양식을 찾을 수 없습니다: " + id);
        }

        // 해당 양식을 사용하는 결재 요청이 있으면 먼저 삭제
        if (approvalRequestRepository.existsByTemplateId(id)) {
            Long count = approvalRequestRepository.countByTemplateId(id);
            approvalRequestRepository.deleteByTemplateId(id);
            log.info("[ApprovalTemplate] 관련 결재 요청 {}건 삭제: templateId={}", count, id);
        }

        templateRepository.deleteById(id);
        log.info("[ApprovalTemplate] 양식 삭제: id={}", id);
    }
}
