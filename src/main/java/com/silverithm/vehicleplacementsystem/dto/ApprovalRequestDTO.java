package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequestDTO {

    private Long id;
    private Long templateId;
    private String templateName;
    private String title;
    private String requesterId;
    private String requesterName;
    private ApprovalStatus status;
    private String formData;
    private String attachmentUrl;
    private String attachmentFileName;
    private Long attachmentFileSize;
    private String processedBy;
    private String processedByName;
    private LocalDateTime processedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 결재선/공문 확장 (전부 additive — 앱은 무시)
    private Boolean hasApprovalLine;
    private List<ApprovalStepDTO> approvalLine;
    private String docNumber;
    private String docNumberDisplay;

    /** 차수. 처음 올린 기안이 1 */
    private Integer revision;
    /** 이 기안이 고쳐 올린 원본(반려 건) id — 화면에서 '이전 차수 보기'로 잇는다 */
    private Long revisedFromId;
    /** 원본이 반려된 사유. 다시 조회하지 않고 바로 보여주기 위해 함께 싣는다 */
    private String revisedFromRejectReason;
    private String companySealUrl;   // 최종 승인된 결재선 문서에만 세팅
    private DocumentFooterDTO documentFooter;  // 공문 하단 발신부 (기관 주소·연락처)
    private List<ApprovalViewerDTO> viewers;   // 열람 대상 (직책/개인)

    /** 다른 시스템에서 옮겨온 완료 문서인지 — 참이면 결재를 다시 진행하지 않는다 */
    private Boolean isImported;
    private String importedSource;
    private String externalDocNumber;
    /** 대표 첨부(attachmentUrl) 외의 딸린 파일들 */
    private List<ApprovalAttachmentDTO> extraAttachments;

    public static ApprovalRequestDTO from(ApprovalRequest request) {
        return ApprovalRequestDTO.builder()
                .id(request.getId())
                .templateId(request.getTemplate().getId())
                .templateName(request.getTemplate().getName())
                .title(request.getTitle())
                .requesterId(request.getRequesterId())
                .requesterName(request.getRequesterName())
                .status(request.getStatus())
                .formData(request.getFormData())
                .attachmentUrl(request.getAttachmentUrl())
                .attachmentFileName(request.getAttachmentFileName())
                .attachmentFileSize(request.getAttachmentFileSize())
                .processedBy(request.getProcessedBy())
                .processedByName(request.getProcessedByName())
                .processedAt(request.getProcessedAt())
                .rejectReason(request.getRejectReason())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .hasApprovalLine(Boolean.TRUE.equals(request.getHasApprovalLine()))
                .approvalLine(request.getSteps() == null ? List.of()
                        : request.getSteps().stream()
                                .map(ApprovalStepDTO::from)
                                .collect(Collectors.toList()))
                .viewers(request.getViewers() == null ? List.of()
                        : request.getViewers().stream()
                                .map(viewer -> ApprovalViewerDTO.builder()
                                        .viewerType(viewer.getViewerType())
                                        .refId(viewer.getRefId())
                                        .viewerName(viewer.getViewerName())
                                        .build())
                                .collect(Collectors.toList()))
                .isImported(Boolean.TRUE.equals(request.getIsImported()))
                .importedSource(request.getImportedSource())
                .externalDocNumber(request.getExternalDocNumber())
                .extraAttachments(request.getExtraAttachments() == null ? List.of()
                        : request.getExtraAttachments().stream()
                                .map(attachment -> ApprovalAttachmentDTO.builder()
                                        .fileUrl(attachment.getFileUrl())
                                        .fileName(attachment.getFileName())
                                        .fileSize(attachment.getFileSize())
                                        .build())
                                .collect(Collectors.toList()))
                .docNumber(request.getDocNumber())
                .docNumberDisplay(request.getDocNumberDisplay())
                .revision(request.getRevision())
                .revisedFromId(request.getRevisedFrom() != null ? request.getRevisedFrom().getId() : null)
                .revisedFromRejectReason(request.getRevisedFrom() != null ? request.getRevisedFrom().getRejectReason() : null)
                .build();
    }
}
