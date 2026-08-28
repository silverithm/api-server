package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
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
public class ApprovalTemplateDTO {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String templateType;
    private String formSchema;
    private String defaultApprovalLine;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private Boolean isActive;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ApprovalViewerDTO> defaultViewers;   // 기본 열람 대상 (직책/개인)

    public static ApprovalTemplateDTO from(ApprovalTemplate template) {
        return ApprovalTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .category(template.getCategory())
                .templateType(template.getTemplateType())
                .formSchema(template.getFormSchema())
                .defaultApprovalLine(template.getDefaultApprovalLine())
                .fileUrl(template.getFileUrl())
                .fileName(template.getFileName())
                .fileSize(template.getFileSize())
                .isActive(template.getIsActive())
                .sortOrder(template.getSortOrder())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .defaultViewers(template.getDefaultViewers() == null ? List.of()
                        : template.getDefaultViewers().stream()
                                .map(viewer -> ApprovalViewerDTO.builder()
                                        .viewerType(viewer.getViewerType())
                                        .refId(viewer.getRefId())
                                        .viewerName(viewer.getViewerName())
                                        .build())
                                .collect(Collectors.toList()))
                .build();
    }
}
