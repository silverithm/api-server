package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequestAttachment;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequestViewer;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.DocumentNumberCounter;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutes;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesAttendee;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.DocumentNumberCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 완료된 회의록을 결재함에 완결 문서로 넣는다.
 *
 * <p>이관 문서(V1.79, ApprovalImportService)와 같은 방식이다: 결재를 다시 진행시키는 게 아니라
 * "누가 서명했다"는 기록을 결재선 모양으로 복원해 status=APPROVED로 저장한다.
 * 이렇게 하면 문서함 목록·상세·공문 렌더(결재란 서명 표시)·열람 대상·검색·출력이
 * 프론트 수정 없이 그대로 적용된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MeetingMinutesApprovalBridgeService {

    /** 회의록 문서를 담는 공유 양식 이름 — 없으면 만들어 쓴다 */
    private static final String TEMPLATE_NAME = "회의록";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalTemplateRepository templateRepository;
    private final DocumentNumberCounterRepository docNumberCounterRepository;
    private final ObjectMapper objectMapper;

    public void registerToApprovalBox(MeetingMinutes minutes) {
        Company company = minutes.getCompany();
        ApprovalTemplate template = resolveTemplate(company);

        ApprovalRequest entity = ApprovalRequest.builder()
                .company(company)
                .template(template)
                .title(minutes.getTitle())
                .requesterId(authorLegacyId(minutes))
                .requesterName(minutes.getAuthorName())
                .status(ApprovalRequest.ApprovalStatus.APPROVED)
                .formData(buildFormData(minutes))
                .processedAt(LocalDateTime.now())
                // 결재함 기간 필터에 회의 날짜로 잡히게 한다 (이관 문서의 기안일 보존과 같은 이유)
                .createdAt(minutes.getMeetingStartAt())
                .build();

        buildSteps(entity, minutes);
        buildViewers(entity, minutes);
        buildAttachments(entity, minutes);
        allocateDocNumber(entity);

        ApprovalRequest saved = requestRepository.save(entity);
        minutes.setApprovalRequest(saved);

        log.info("[MeetingMinutes] 결재함 등록: minutesId={}, approvalRequestId={}, docNumber={}",
                minutes.getId(), saved.getId(), saved.getDocNumber());
    }

    /**
     * 참석자 서명을 결재선 모양으로 복원한다. 서명 안 한 참석자(불참 등)도 칸은 남는다 —
     * 종이 회의록에서 빈 서명란이 남는 것과 같다. 인가 판정은 (type, refId) 비교라
     * 이 스텝이 누군가의 권한이 되는 일은 없다 (이관 문서와 동일).
     */
    private void buildSteps(ApprovalRequest entity, MeetingMinutes minutes) {
        List<MeetingMinutesAttendee> attendees = minutes.getAttendees();
        for (int i = 0; i < attendees.size(); i++) {
            MeetingMinutesAttendee attendee = attendees.get(i);
            boolean isLast = i == attendees.size() - 1;

            ApprovalStep.ApproverType type = attendee.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.ADMIN
                    ? ApprovalStep.ApproverType.ADMIN
                    : ApprovalStep.ApproverType.MEMBER;   // EXTERNAL은 refId 없는 MEMBER (이관 문서 관례)
            Long refId = attendee.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.EXTERNAL
                    ? null
                    : attendee.getRefId();

            entity.getSteps().add(ApprovalStep.builder()
                    .approvalRequest(entity)
                    .stepOrder(i + 1)
                    .approverType(type)
                    .approverRefId(refId)
                    .approverIdLegacy(refId == null ? null
                            : (type == ApprovalStep.ApproverType.ADMIN ? "admin_" + refId : String.valueOf(refId)))
                    .approverName(attendee.getAttendeeName())
                    .roleLabel(isLast ? ApprovalStep.StepRole.FINAL : ApprovalStep.StepRole.REVIEWER)
                    .status(ApprovalStep.StepStatus.APPROVED)
                    .signatureUrl(attendee.getSignatureUrl())
                    .processedAt(attendee.getSignedAt())
                    .build());
        }

        entity.setHasApprovalLine(!attendees.isEmpty());
        entity.setCurrentStepOrder(null);
    }

    /** 내부 참석자 + 작성자를 열람 대상으로 — 결재함에서도 참석자들이 볼 수 있게 */
    private void buildViewers(ApprovalRequest entity, MeetingMinutes minutes) {
        Set<String> seen = new HashSet<>();

        for (MeetingMinutesAttendee attendee : minutes.getAttendees()) {
            if (attendee.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.EXTERNAL) {
                continue;
            }
            ApprovalViewerType viewerType = attendee.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.ADMIN
                    ? ApprovalViewerType.ADMIN
                    : ApprovalViewerType.MEMBER;
            addViewer(entity, seen, viewerType, attendee.getRefId(), attendee.getAttendeeName());
        }

        ApprovalViewerType authorViewerType = minutes.getAuthorType() == ApprovalStep.ApproverType.ADMIN
                ? ApprovalViewerType.ADMIN
                : ApprovalViewerType.MEMBER;
        addViewer(entity, seen, authorViewerType, minutes.getAuthorRefId(), minutes.getAuthorName());
    }

    private void addViewer(ApprovalRequest entity, Set<String> seen,
                           ApprovalViewerType type, Long refId, String name) {
        if (refId == null || !seen.add(type + ":" + refId)) {
            return;
        }
        entity.getViewers().add(ApprovalRequestViewer.builder()
                .approvalRequest(entity)
                .viewerType(type)
                .refId(refId)
                .viewerName(name)
                .build());
    }

    private void buildAttachments(ApprovalRequest entity, MeetingMinutes minutes) {
        int order = 0;
        for (var attachment : minutes.getAttachments()) {
            entity.getExtraAttachments().add(ApprovalRequestAttachment.builder()
                    .approvalRequest(entity)
                    .fileUrl(attachment.getFileUrl())
                    .fileName(attachment.getFileName())
                    .fileSize(attachment.getFileSize())
                    .sortOrder(order++)
                    .build());
        }
    }

    /**
     * 문서번호 채번. ApprovalRequestService.allocateDocNumber와 같은 루틴 —
     * 원본이 private이고 승인 경로를 건드리지 않기 위해 복제한다.
     */
    private void allocateDocNumber(ApprovalRequest request) {
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
            // 문서번호 발급 실패가 완료 자체를 막지 않도록 한다
            log.error("[MeetingMinutes] 문서번호 발급 실패: {}", e.getMessage());
        }
    }

    /**
     * 회사별 공유 "회의록" 양식. 고정 필드 4개(일시·장소·참석자·내용)로,
     * 섹션 구성이 바뀌어도 옛 문서 렌더가 깨지지 않게 내용은 하나의 textarea에 담는다.
     * 이 양식으로 새 기안을 올리지는 않는다 — 비활성으로 만들어 기안 목록에 띄우지 않는다.
     */
    private ApprovalTemplate resolveTemplate(Company company) {
        return templateRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .filter(template -> TEMPLATE_NAME.equals(template.getName()))
                .findFirst()
                .orElseGet(() -> templateRepository.save(ApprovalTemplate.builder()
                        .company(company)
                        .name(TEMPLATE_NAME)
                        .description("회의록 기능에서 완료된 회의록이 자동 등록되는 양식입니다.")
                        .category("회의")
                        .templateType("form")
                        .formSchema(buildFormSchema())
                        .isActive(false)
                        .build()));
    }

    private String buildFormSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("version", 1);
        ArrayNode fields = schema.putArray("fields");
        fields.add(field("meetingDate", "text", "회의 일시", "half"));
        fields.add(field("location", "text", "회의 장소", "half"));
        fields.add(field("attendees", "textarea", "참석자", "full"));
        fields.add(field("content", "textarea", "회의 내용", "full"));
        return schema.toString();
    }

    private ObjectNode field(String id, String type, String label, String width) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("label", label);
        node.put("required", false);
        node.put("width", width);
        return node;
    }

    private String buildFormData(MeetingMinutes minutes) {
        StringBuilder when = new StringBuilder(minutes.getMeetingStartAt().format(DATE_FORMAT))
                .append(" ").append(minutes.getMeetingStartAt().format(TIME_FORMAT));
        if (minutes.getMeetingEndAt() != null) {
            when.append(" ~ ").append(minutes.getMeetingEndAt().format(TIME_FORMAT));
        }

        StringBuilder attendees = new StringBuilder();
        for (MeetingMinutesAttendee attendee : minutes.getAttendees()) {
            if (attendees.length() > 0) {
                attendees.append(", ");
            }
            attendees.append(attendee.getAttendeeName());
            if (attendee.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.EXTERNAL) {
                attendees.append("(외부)");
            }
            if (!attendee.isSigned()) {
                attendees.append("(미서명)");
            }
        }

        ObjectNode formData = objectMapper.createObjectNode();
        formData.put("meetingDate", when.toString());
        formData.put("location", minutes.getLocation() != null ? minutes.getLocation() : "");
        formData.put("attendees", attendees.toString());
        formData.put("content", formatSections(minutes.getSectionsJson()));
        return formData.toString();
    }

    /** [{"key","label","content"}] → "[전체]\n…\n\n[팀별 전달사항]\n…" */
    private String formatSections(String sectionsJson) {
        if (sectionsJson == null || sectionsJson.isBlank()) {
            return "";
        }
        try {
            JsonNode sections = objectMapper.readTree(sectionsJson);
            StringBuilder text = new StringBuilder();
            for (JsonNode section : sections) {
                String content = section.path("content").asText("");
                if (content.isBlank()) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append("[").append(section.path("label").asText("")).append("]\n").append(content.trim());
            }
            return text.toString();
        } catch (Exception e) {
            log.warn("[MeetingMinutes] 섹션 JSON 해석 실패, 원문 그대로 담습니다: {}", e.getMessage());
            return sectionsJson;
        }
    }

    private String authorLegacyId(MeetingMinutes minutes) {
        return minutes.getAuthorType() == ApprovalStep.ApproverType.ADMIN
                ? "admin_" + minutes.getAuthorRefId()
                : String.valueOf(minutes.getAuthorRefId());
    }
}
