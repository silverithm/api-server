package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.ApprovalTemplateVersionDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateVersion;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateVersion.Kind;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateVersionRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결재 양식 버전 기록(2026-11-16) — 저장마다 그때 모습을 남긴다.
 *
 * <ul>
 *   <li>처음 고치기 직전 기록이 하나도 없으면 고치기 전 모습을 BASELINE으로 먼저 남긴다
 *       (배포 때 마이그레이션이 지금 양식을 모두 BASELINE으로 남기지만, 혹시 빠진 양식을 위해).</li>
 *   <li>내용(이름·설명·분류·종류·항목 구성·기본 결재선·파일)이 마지막 버전과 같으면 새 버전을 만들지 않는다 —
 *       열람 대상만 바꾼 저장이 버전 목록을 채우지 않게.</li>
 *   <li>버전은 고치거나 지우지 않는다. 되돌리기도 새 버전(RESTORED)으로 쌓인다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalTemplateVersionService {

    private final ApprovalTemplateVersionRepository versionRepository;
    /** 저장한 사람 — 업무진행도와 같은 방식(토큰)으로 정한다 */
    private final WorkTaskAccess callerAccess;
    private final ObjectMapper objectMapper;

    /** 고치기 직전 — 기록이 없으면 지금(고치기 전) 모습을 첫 버전으로 */
    public void ensureBaseline(ApprovalTemplate template) {
        if (versionRepository.findFirstByTemplateIdOrderByVersionNoDesc(template.getId()).isPresent()) {
            return;
        }
        versionRepository.save(ApprovalTemplateVersion.builder()
                .template(template)
                .versionNo(1)
                .kind(Kind.BASELINE)
                .createdAt(template.getUpdatedAt())
                .build());
    }

    /**
     * 저장 뒤 — 지금 모습을 다음 번호로 남긴다. 마지막 버전과 내용이 같으면 남기지 않는다.
     *
     * @return 새로 남긴 버전(같아서 안 남겼으면 비어 있음)
     */
    public Optional<ApprovalTemplateVersion> recordSaved(ApprovalTemplate template, Kind kind, Integer restoredFromNo) {
        Optional<ApprovalTemplateVersion> latest =
                versionRepository.findFirstByTemplateIdOrderByVersionNoDesc(template.getId());
        if (latest.isPresent() && sameContent(latest.get(), template)) {
            return Optional.empty();
        }
        int next = latest.map(v -> v.getVersionNo() + 1).orElse(1);
        WorkTaskAccess.Caller caller = currentCaller();
        ApprovalTemplateVersion saved = versionRepository.save(ApprovalTemplateVersion.builder()
                .template(template)
                .versionNo(next)
                .kind(kind)
                .restoredFromNo(restoredFromNo)
                .savedById(caller != null ? caller.personId() : null)
                .savedByName(caller != null ? truncate(caller.name(), 50) : null)
                .build());
        log.info("[ApprovalTemplate] 버전 기록: templateId={}, v{} {}{}", template.getId(), next, kind,
                restoredFromNo != null ? " (v" + restoredFromNo + "에서)" : "");
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalTemplateVersionDTO> list(Long templateId) {
        List<ApprovalTemplateVersion> versions = versionRepository.findTop100ByTemplateIdOrderByVersionNoDesc(templateId);
        return versions.stream()
                .map(v -> ApprovalTemplateVersionDTO.from(v, v == versions.get(0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalTemplateVersion require(Long templateId, Long versionId) {
        return versionRepository.findByIdAndTemplateId(versionId, templateId)
                .orElseThrow(() -> new CustomException("양식 버전을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
    }

    /** 문서 모양이 같은지 — 항목 구성·결재선은 JSON 값으로 비교한다(띄어쓰기·키 순서가 달라도 같다) */
    boolean sameContent(ApprovalTemplateVersion v, ApprovalTemplate t) {
        return Objects.equals(v.getName(), t.getName())
                && Objects.equals(blankToNull(v.getDescription()), blankToNull(t.getDescription()))
                && Objects.equals(blankToNull(v.getCategory()), blankToNull(t.getCategory()))
                && Objects.equals(v.getTemplateType(), t.getTemplateType() != null ? t.getTemplateType() : "file")
                && sameJson(v.getFormSchema(), t.getFormSchema())
                && sameJson(v.getDefaultApprovalLine(), t.getDefaultApprovalLine())
                && Objects.equals(v.getFileUrl(), t.getFileUrl())
                && Objects.equals(v.getFileName(), t.getFileName());
    }

    private boolean sameJson(String a, String b) {
        if (Objects.equals(a, b)) return true;
        if (a == null || b == null) return false;
        try {
            return Objects.equals(parseJson(a), parseJson(b));
        } catch (Exception e) {
            return false;
        }
    }

    /** JSON 글자를 값으로 — 문자열로 한 번 더 싸여 있으면(JSON 칸에 글자로 들어간 경우) 한 겹 벗긴다 */
    private JsonNode parseJson(String raw) throws Exception {
        JsonNode node = objectMapper.readTree(raw);
        if (node != null && node.isTextual()) {
            String inner = node.asText().trim();
            if (inner.startsWith("{") || inner.startsWith("[")) {
                return objectMapper.readTree(inner);
            }
        }
        return node;
    }

    private WorkTaskAccess.Caller currentCaller() {
        try {
            return callerAccess.requireCaller();
        } catch (RuntimeException e) {
            // 누가 저장했는지 모르는 것은 버전을 못 남길 이유가 아니다
            log.debug("[ApprovalTemplate] 버전 기록 — 저장한 사람을 정하지 못함: {}", e.getMessage());
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
