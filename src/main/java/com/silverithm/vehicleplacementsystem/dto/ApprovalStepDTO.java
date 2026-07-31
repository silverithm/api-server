package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStepDTO {

    private Long id;
    private Integer stepOrder;
    private String approverType;   // ADMIN | MEMBER
    private String approverId;     // 프론트 호환 문자열 (admin_<id> 또는 memberId)
    private String approverName;
    private String roleLabel;      // REVIEWER | FINAL
    private String status;         // PENDING | APPROVED | REJECTED | SKIPPED
    private String signatureUrl;   // 전체 S3 URL (미서명 시 null)
    private LocalDateTime processedAt;
    private String rejectReason;

    public static ApprovalStepDTO from(ApprovalStep step) {
        return ApprovalStepDTO.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .approverType(step.getApproverType().name())
                .approverId(step.getApproverIdLegacy())
                .approverName(step.getApproverName())
                .roleLabel(step.getRoleLabel().name())
                .status(step.getStatus().name())
                .signatureUrl(step.getSignatureUrl())
                .processedAt(step.getProcessedAt())
                .rejectReason(step.getRejectReason())
                .build();
    }
}
