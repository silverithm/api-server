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
    private String companySealUrl;   // 최종 승인된 결재선 문서에만 세팅
    private DocumentFooterDTO documentFooter;  // 공문 하단 발신부 (기관 주소·연락처)
    private List<ApprovalViewerDTO> viewers;   // 열람 대상 (직책/개인)

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
                .docNumber(request.getDocNumber())
                .docNumberDisplay(request.getDocNumberDisplay())
                .build();
    }
}
