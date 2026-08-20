package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ApprovalImportPreviewDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalImportRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalImportRowDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalViewerEntryDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequestAttachment;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequestViewer;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 다른 시스템에서 결재가 끝난 문서를 옮겨 담는다.
 *
 * <p>과거 결재를 다시 진행시킬 수는 없다. 결재선은 "누가 언제 결재했다"는 기록으로만 복원되고,
 * 문서는 완료 상태로 들어와 결재함에서 승인·반려 대상이 되지 않는다.
 *
 * <p>미리보기(검증)와 등록을 나눈 이유는, 수백 건을 한 번에 올리는 작업이라 무엇이 들어갈지
 * 먼저 눈으로 확인하지 않으면 되돌리기가 어렵기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalImportService {

    /** 이관 문서를 담을 기본 양식 이름 — 없으면 만들어 쓴다 */
    private static final String DEFAULT_TEMPLATE_NAME = "이관 문서";

    private final ApprovalImportParser parser;
    private final ApprovalRequestRepository requestRepository;
    private final ApprovalTemplateRepository templateRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final ApprovalViewerResolver viewerResolver;
    private final ResourceScopeGuard resourceScopeGuard;

    /** 이름 → 계정. 같은 이름이 둘 이상이면 누구인지 특정할 수 없으므로 붙이지 않는다. */
    private record PersonIndex(Map<String, ApprovalStep.ApproverType> typeByName,
                               Map<String, Long> refIdByName,
                               Map<String, String> legacyIdByName,
                               Set<String> ambiguousNames) {
    }

    @Transactional(readOnly = true)
    public ApprovalImportPreviewDTO preview(Long companyId, MultipartFile file, Set<String> uploadedFileNames) {
        Company company = requireCompany(companyId);

        ApprovalImportParser.ParsedSheet parsed;
        try {
            parsed = parser.parse(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀을 읽지 못했습니다: " + e.getMessage());
        }

        PersonIndex people = buildPersonIndex(company);
        Set<String> existingDocNumbers = loadExistingDocNumbers(companyId, parsed.rows());
        Set<String> seenDocNumbers = new HashSet<>();
        Set<String> missingFiles = new LinkedHashSet<>();
        int errorCount = 0;

        for (ApprovalImportRowDTO row : parsed.rows()) {
            validate(row, people, existingDocNumbers, seenDocNumbers, uploadedFileNames, missingFiles);
            if (!row.getErrors().isEmpty()) {
                errorCount++;
            }
        }

        return ApprovalImportPreviewDTO.builder()
                .columnMappings(parsed.mappings())
                .unmappedColumns(parsed.unmappedColumns())
                .rows(parsed.rows())
                .totalCount(parsed.rows().size())
                .errorCount(errorCount)
                .missingFileNames(new ArrayList<>(missingFiles))
                .build();
    }

    /**
     * 확정 등록. 문제가 있는 줄은 건너뛰고 나머지를 넣는다 —
     * 한 줄 때문에 전체가 멈추면 수백 건짜리 작업이 진행되지 않는다.
     */
    public ApprovalImportPreviewDTO importRows(Long companyId, ApprovalImportRequestDTO request) {
        Company company = requireCompany(companyId);
        ApprovalTemplate template = resolveTemplate(company, request.getTemplateId());
        PersonIndex people = buildPersonIndex(company);

        Map<String, ApprovalImportRequestDTO.UploadedFile> files =
                request.getFiles() != null ? request.getFiles() : Map.of();

        Set<String> existingDocNumbers = loadExistingDocNumbers(companyId, request.getRows());
        Set<String> seenDocNumbers = new HashSet<>();
        Set<String> missingFiles = new LinkedHashSet<>();
        List<ApprovalImportRowDTO> results = new ArrayList<>();
        int errorCount = 0;
        int savedCount = 0;

        for (ApprovalImportRowDTO row : request.getRows()) {
            row.getErrors().clear();
            row.getWarnings().clear();
            validate(row, people, existingDocNumbers, seenDocNumbers, files.keySet(), missingFiles);

            if (!row.getErrors().isEmpty()) {
                errorCount++;
                results.add(row);
                continue;
            }

            save(company, template, row, people, files, request);
            savedCount++;
            results.add(row);
        }

        log.info("[ApprovalImport] 이관 완료: companyId={}, 등록={}건, 제외={}건, source={}",
                companyId, savedCount, errorCount, request.getSource());

        return ApprovalImportPreviewDTO.builder()
                .rows(results)
                .totalCount(request.getRows().size())
                .errorCount(errorCount)
                .missingFileNames(new ArrayList<>(missingFiles))
                .build();
    }

    private void save(Company company, ApprovalTemplate template, ApprovalImportRowDTO row,
                      PersonIndex people, Map<String, ApprovalImportRequestDTO.UploadedFile> files,
                      ApprovalImportRequestDTO request) {

        // 기안일 자정으로 넣는다 — 시각까지 남아 있지 않은 색인이 대부분이다
        LocalDateTime draftedAt = row.getDraftedAt().atStartOfDay();

        ApprovalRequest entity = ApprovalRequest.builder()
                .company(company)
                .template(template)
                .title(row.getTitle().trim())
                .requesterId(resolveRequesterId(row.getRequesterName(), people))
                .requesterName(nullSafe(row.getRequesterName(), "(알 수 없음)"))
                .status(ApprovalStatus.valueOf(row.getStatus()))
                .isImported(true)
                .importedSource(nullSafe(request.getSource(), "IMPORT"))
                .externalDocNumber(blankToNull(row.getExternalDocNumber()))
                .importedAt(LocalDateTime.now())
                .createdAt(draftedAt)
                .build();

        // 첫 번째 파일이 대표(원본 PDF), 나머지는 추가 첨부로 붙는다
        List<ApprovalImportRequestDTO.UploadedFile> matched = new ArrayList<>();
        List<String> matchedNames = new ArrayList<>();
        for (String fileName : row.getFileNames()) {
            ApprovalImportRequestDTO.UploadedFile uploaded = files.get(fileName);
            if (uploaded != null) {
                matched.add(uploaded);
                matchedNames.add(fileName);
            }
        }

        if (!matched.isEmpty()) {
            entity.setAttachmentUrl(matched.get(0).getFilePath());
            entity.setAttachmentFileName(matchedNames.get(0));
            entity.setAttachmentFileSize(matched.get(0).getFileSize());

            for (int i = 1; i < matched.size(); i++) {
                entity.getExtraAttachments().add(ApprovalRequestAttachment.builder()
                        .approvalRequest(entity)
                        .fileUrl(matched.get(i).getFilePath())
                        .fileName(matchedNames.get(i))
                        .fileSize(matched.get(i).getFileSize())
                        .sortOrder(i)
                        .build());
            }
        }

        buildSteps(entity, row, people);
        buildViewers(entity, company, request.getViewers());

        requestRepository.save(entity);
    }

    /**
     * 결재 이력을 결재선 모양으로 복원한다.
     *
     * <p>계정을 못 찾은 결재자는 ref_id 없이 이름만 남는다. 인가 판정은 (type, refId) 비교라
     * 이 단계가 누군가의 권한이 되는 일은 없다 — 표시와 검색용 기록이다.
     */
    private void buildSteps(ApprovalRequest entity, ApprovalImportRowDTO row, PersonIndex people) {
        List<ApprovalImportRowDTO.Approver> approvers = row.getApprovers();
        if (approvers.isEmpty()) {
            return;
        }

        boolean rejected = ApprovalStatus.REJECTED.name().equals(row.getStatus());

        for (int i = 0; i < approvers.size(); i++) {
            ApprovalImportRowDTO.Approver approver = approvers.get(i);
            boolean isLast = i == approvers.size() - 1;
            String key = normalizeName(approver.getName());
            boolean matched = people.refIdByName().containsKey(key) && !people.ambiguousNames().contains(key);

            entity.getSteps().add(ApprovalStep.builder()
                    .approvalRequest(entity)
                    .stepOrder(i + 1)
                    .approverType(matched ? people.typeByName().get(key) : ApprovalStep.ApproverType.MEMBER)
                    .approverRefId(matched ? people.refIdByName().get(key) : null)
                    .approverIdLegacy(matched ? people.legacyIdByName().get(key) : null)
                    .approverName(approver.getName())
                    .roleLabel(isLast ? ApprovalStep.StepRole.FINAL : ApprovalStep.StepRole.REVIEWER)
                    // 반려 문서는 마지막 단계에서 반려된 것으로 본다 (색인에 어느 단계인지가 없다)
                    .status(rejected && isLast ? ApprovalStep.StepStatus.REJECTED : ApprovalStep.StepStatus.APPROVED)
                    .processedAt(approver.getApprovedAt() != null
                            ? approver.getApprovedAt().atTime(LocalTime.NOON)
                            : null)
                    .build());
        }

        entity.setHasApprovalLine(true);
        entity.setCurrentStepOrder(null);
    }

    private void buildViewers(ApprovalRequest entity, Company company, List<ApprovalViewerEntryDTO> viewers) {
        if (viewers == null || viewers.isEmpty()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (ApprovalViewerEntryDTO viewer : viewers) {
            if (viewer == null || viewer.getViewerType() == null || viewer.getRefId() == null) {
                continue;
            }
            if (seen.add(viewer.getViewerType() + ":" + viewer.getRefId())) {
                entity.getViewers().add(ApprovalRequestViewer.builder()
                        .approvalRequest(entity)
                        .viewerType(viewer.getViewerType())
                        .refId(viewer.getRefId())
                        .viewerName(viewerResolver.resolveName(viewer.getViewerType(), viewer.getRefId(),
                                company.getId()))
                        .build());
            }
        }
    }

    /** 색인에 등장하는 문서번호 중 이미 이관된 것들을 한 번의 쿼리로 걷어온다 */
    private Set<String> loadExistingDocNumbers(Long companyId, List<ApprovalImportRowDTO> rows) {
        List<String> docNumbers = rows.stream()
                .map(row -> blankToNull(row.getExternalDocNumber()))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (docNumbers.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(requestRepository.findExistingExternalDocNumbers(companyId, docNumbers));
    }

    private void validate(ApprovalImportRowDTO row, PersonIndex people,
                          Set<String> existingDocNumbers, Set<String> seenDocNumbers,
                          Set<String> uploadedFileNames, Set<String> missingFiles) {

        if (row.getTitle() == null || row.getTitle().isBlank()) {
            row.getErrors().add("제목이 비어 있습니다");
        }

        if (row.getDraftedAt() == null) {
            row.getErrors().add("기안일을 읽지 못했습니다 (예: 2025-01-03)");
        }

        String status = row.getStatus();
        if (status == null || status.isBlank()) {
            row.getErrors().add("결재상태가 비어 있습니다");
        } else if (!ApprovalStatus.APPROVED.name().equals(status) && !ApprovalStatus.REJECTED.name().equals(status)) {
            row.getErrors().add("결재가 끝난 문서만 옮길 수 있습니다 (읽은 값: " + status + ")");
        }

        String docNumber = blankToNull(row.getExternalDocNumber());
        if (docNumber != null) {
            if (!seenDocNumbers.add(docNumber)) {
                row.getErrors().add("같은 파일 안에 문서번호가 중복됩니다: " + docNumber);
            } else if (existingDocNumbers.contains(docNumber)) {
                row.getErrors().add("이미 옮겨진 문서번호입니다: " + docNumber);
            }
        }

        String requester = normalizeName(row.getRequesterName());
        if (requester.isBlank()) {
            row.getWarnings().add("기안자 이름이 없습니다");
        } else if (people.ambiguousNames().contains(requester)) {
            row.getWarnings().add("같은 이름의 직원이 둘 이상이라 계정을 붙이지 않았습니다: " + row.getRequesterName());
        } else if (!people.refIdByName().containsKey(requester)) {
            row.getWarnings().add("재직 중인 직원에서 기안자를 찾지 못해 이름만 남깁니다: " + row.getRequesterName());
        }

        for (ApprovalImportRowDTO.Approver approver : row.getApprovers()) {
            String key = normalizeName(approver.getName());
            boolean matched = people.refIdByName().containsKey(key) && !people.ambiguousNames().contains(key);
            approver.setMatchedType(matched ? people.typeByName().get(key).name() : null);
            approver.setMatchedRefId(matched ? people.refIdByName().get(key) : null);
            if (!matched) {
                row.getWarnings().add("결재자 계정을 찾지 못해 이름만 남깁니다: " + approver.getName());
            }
        }

        for (String fileName : row.getFileNames()) {
            if (!uploadedFileNames.contains(fileName)) {
                missingFiles.add(fileName);
                row.getWarnings().add("파일이 아직 올라오지 않았습니다: " + fileName);
            }
        }
    }

    /** 기안자 식별자. 계정을 찾으면 그 사람 것으로, 못 찾으면 아무에게도 안 걸리는 값으로 둔다 */
    private String resolveRequesterId(String requesterName, PersonIndex people) {
        String key = normalizeName(requesterName);
        if (!key.isBlank() && !people.ambiguousNames().contains(key) && people.legacyIdByName().containsKey(key)) {
            return people.legacyIdByName().get(key);
        }
        return "imported";
    }

    private PersonIndex buildPersonIndex(Company company) {
        Map<String, ApprovalStep.ApproverType> typeByName = new HashMap<>();
        Map<String, Long> refIdByName = new HashMap<>();
        Map<String, String> legacyIdByName = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();

        if (company.getUsers() != null) {
            for (AppUser appUser : company.getUsers()) {
                if (appUser.getDeletedAt() != null) {
                    continue;
                }
                register(typeByName, refIdByName, legacyIdByName, ambiguous,
                        appUser.getUsername(), ApprovalStep.ApproverType.ADMIN, appUser.getId(),
                        "admin_" + appUser.getId());
            }
        }

        for (Member member : memberRepository.findByCompanyOrderByCreatedAtDesc(company)) {
            if (member.getStatus() != Member.MemberStatus.ACTIVE) {
                continue;
            }
            register(typeByName, refIdByName, legacyIdByName, ambiguous,
                    member.getName(), ApprovalStep.ApproverType.MEMBER, member.getId(),
                    String.valueOf(member.getId()));
        }

        return new PersonIndex(typeByName, refIdByName, legacyIdByName, ambiguous);
    }

    private void register(Map<String, ApprovalStep.ApproverType> typeByName, Map<String, Long> refIdByName,
                          Map<String, String> legacyIdByName, Set<String> ambiguous,
                          String name, ApprovalStep.ApproverType type, Long refId, String legacyId) {
        String key = normalizeName(name);
        if (key.isBlank()) {
            return;
        }
        if (refIdByName.containsKey(key)) {
            ambiguous.add(key);   // 동명이인은 누구인지 특정할 수 없다
            return;
        }
        typeByName.put(key, type);
        refIdByName.put(key, refId);
        legacyIdByName.put(key, legacyId);
    }

    /** 이름 비교용 정규화 — "홍길동 팀장", "홍 길동"이 같은 사람으로 읽히게 */
    private String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }

    private ApprovalTemplate resolveTemplate(Company company, Long templateId) {
        if (templateId != null) {
            ApprovalTemplate template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("양식을 찾을 수 없습니다: " + templateId));
            resourceScopeGuard.requireSameCompany(template.getCompany());
            return template;
        }

        return templateRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .filter(template -> DEFAULT_TEMPLATE_NAME.equals(template.getName()))
                .findFirst()
                .orElseGet(() -> templateRepository.save(ApprovalTemplate.builder()
                        .company(company)
                        .name(DEFAULT_TEMPLATE_NAME)
                        .description("다른 시스템에서 옮겨온 완료 문서를 담는 양식입니다.")
                        .category("이관")
                        .templateType("file")
                        // 이 양식으로 새 기안을 올리지는 않는다 — 목록에 뜨지 않게 비활성으로 만든다
                        .isActive(false)
                        .build()));
    }

    private Company requireCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("회사를 찾을 수 없습니다: " + companyId));
        resourceScopeGuard.requireSameCompany(company);
        return company;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
