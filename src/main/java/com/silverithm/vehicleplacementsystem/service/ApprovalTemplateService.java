package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ApprovalTemplateDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalTemplateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalViewerEntryDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateViewer;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalTemplateService {

    private final ApprovalTemplateRepository templateRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final CompanyRepository companyRepository;
    private final ResourceScopeGuard resourceScopeGuard;
    private final ApprovalViewerResolver viewerResolver;

    // 전체 양식 조회 (관리자용) — 관리자가 정한 순서(sortOrder) 우선, 그다음 최신 등록순
    @Transactional(readOnly = true)
    public List<ApprovalTemplateDTO> getAllTemplates(Long companyId) {
        return templateRepository.findByCompanyIdOrderBySortOrderAscCreatedAtDesc(companyId)
                .stream()
                .map(ApprovalTemplateDTO::from)
                .collect(Collectors.toList());
    }

    // 활성화된 양식만 조회 (직원용) — 기안 작성 화면의 양식 선택도 이 순서를 그대로 따른다
    @Transactional(readOnly = true)
    public List<ApprovalTemplateDTO> getActiveTemplates(Long companyId) {
        return templateRepository.findByCompanyIdAndIsActiveTrueOrderBySortOrderAscCreatedAtDesc(companyId)
                .stream()
                .map(ApprovalTemplateDTO::from)
                .collect(Collectors.toList());
    }

    // 양식 상세 조회
    @Transactional(readOnly = true)
    public ApprovalTemplateDTO getTemplate(Long id) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(template.getCompany());
        return ApprovalTemplateDTO.from(template);
    }

    // 양식 생성
    public ApprovalTemplateDTO createTemplate(Long companyId, CreateApprovalTemplateRequestDTO request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        // 새 양식은 목록 맨 끝에 붙인다 (기존 순서를 밀어내지 않음)
        int nextSortOrder = templateRepository.findFirstByCompanyIdOrderBySortOrderDesc(companyId)
                .map(t -> t.getSortOrder() + 1)
                .orElse(0);

        ApprovalTemplate template = ApprovalTemplate.builder()
                .company(company)
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .templateType(request.getTemplateType() != null ? request.getTemplateType() : "file")
                .formSchema(request.getFormSchema())
                .defaultApprovalLine(request.getDefaultApprovalLine())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .isActive(true)
                .sortOrder(nextSortOrder)
                .build();

        applyDefaultViewers(template, request.getDefaultViewers());

        ApprovalTemplate saved = templateRepository.save(template);
        log.info("[ApprovalTemplate] 양식 생성: id={}, name={}, 열람대상={}건",
                saved.getId(), saved.getName(), saved.getDefaultViewers().size());

        return ApprovalTemplateDTO.from(saved);
    }

    // 양식 수정
    public ApprovalTemplateDTO updateTemplate(Long id, CreateApprovalTemplateRequestDTO request) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(template.getCompany());

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setCategory(request.getCategory());
        if (request.getTemplateType() != null) {
            template.setTemplateType(request.getTemplateType());
        }
        template.setFormSchema(request.getFormSchema());
        template.setDefaultApprovalLine(request.getDefaultApprovalLine());
        template.setFileUrl(request.getFileUrl());
        template.setFileName(request.getFileName());
        template.setFileSize(request.getFileSize());
        applyDefaultViewers(template, request.getDefaultViewers());

        ApprovalTemplate saved = templateRepository.save(template);
        log.info("[ApprovalTemplate] 양식 수정: id={}, name={}, 열람대상={}건",
                saved.getId(), saved.getName(), saved.getDefaultViewers().size());

        return ApprovalTemplateDTO.from(saved);
    }

    /**
     * 기본 열람 대상 교체. null이면 손대지 않고(이 필드를 모르는 클라이언트 보호),
     * 빈 배열이면 지정을 모두 지운다.
     */
    private void applyDefaultViewers(ApprovalTemplate template, List<ApprovalViewerEntryDTO> entries) {
        if (entries == null) {
            return;
        }

        Long companyId = template.getCompany() != null ? template.getCompany().getId() : null;

        List<ApprovalTemplateViewer> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ApprovalViewerEntryDTO entry : entries) {
            if (entry == null || entry.getViewerType() == null || entry.getRefId() == null) {
                continue;
            }
            if (seen.add(entry.getViewerType() + ":" + entry.getRefId())) {
                resolved.add(ApprovalTemplateViewer.builder()
                        .template(template)
                        .viewerType(entry.getViewerType())
                        .refId(entry.getRefId())
                        .viewerName(viewerResolver.resolveName(entry.getViewerType(), entry.getRefId(), companyId))
                        .build());
            }
        }

        template.getDefaultViewers().clear();
        template.getDefaultViewers().addAll(resolved);
    }

    /**
     * 양식 관리 화면에서 드래그(또는 위/아래 이동)로 정한 새 순서를 저장한다.
     * 넘어온 순서대로 0부터 sortOrder를 다시 매긴다 — 이 회사 소유가 아닌 id가 섞여 있으면 거부한다.
     */
    public List<ApprovalTemplateDTO> reorderTemplates(Long companyId, List<Long> orderedTemplateIds) {
        List<ApprovalTemplate> templates = templateRepository.findAllById(orderedTemplateIds);

        if (templates.size() != orderedTemplateIds.size()) {
            throw new RuntimeException("존재하지 않는 양식이 순서 목록에 포함되어 있습니다");
        }
        boolean allSameCompany = templates.stream()
                .allMatch(t -> t.getCompany() != null && t.getCompany().getId().equals(companyId));
        if (!allSameCompany) {
            throw new IllegalStateException("다른 기관의 양식은 순서를 바꿀 수 없습니다");
        }

        Map<Long, ApprovalTemplate> byId = templates.stream()
                .collect(Collectors.toMap(ApprovalTemplate::getId, t -> t));

        int order = 0;
        for (Long id : orderedTemplateIds) {
            byId.get(id).setSortOrder(order++);
        }
        templateRepository.saveAll(templates);
        log.info("[ApprovalTemplate] 양식 순서 변경: companyId={}, count={}", companyId, templates.size());

        return getAllTemplates(companyId);
    }

    // 양식 활성화/비활성화 토글
    public ApprovalTemplateDTO toggleActive(Long id) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(template.getCompany());

        template.setIsActive(!template.getIsActive());
        ApprovalTemplate saved = templateRepository.save(template);
        log.info("[ApprovalTemplate] 양식 상태 변경: id={}, isActive={}", saved.getId(), saved.getIsActive());

        return ApprovalTemplateDTO.from(saved);
    }

    // 양식 삭제 — 작성된 결재 문서가 있으면 삭제하지 않는다 (결재 기록 보존).
    // 과거에 문서를 연쇄 삭제하던 구현이 실제 결재 기록 226건을 지운 사고가 있었다. 절대 되돌리지 말 것.
    public void deleteTemplate(Long id) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(template.getCompany());

        Long count = approvalRequestRepository.countByTemplateId(id);
        if (count > 0) {
            throw new IllegalStateException(
                    "이 양식으로 작성된 결재 문서가 " + count + "건 있어 삭제할 수 없습니다. "
                            + "기록을 보존한 채 사용을 중단하려면 양식을 비활성화해 주세요.");
        }

        templateRepository.deleteById(id);
        log.info("[ApprovalTemplate] 양식 삭제: id={}", id);
    }
}
